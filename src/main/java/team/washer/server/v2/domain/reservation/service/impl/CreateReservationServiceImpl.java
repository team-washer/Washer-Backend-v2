package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.reservation.config.ReservationEnvironment;
import team.washer.server.v2.domain.reservation.dto.request.CreateReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.service.CreateReservationService;
import team.washer.server.v2.domain.reservation.support.ReservationCreationSupport;
import team.washer.server.v2.domain.reservation.support.ReservationDeviceStateVerifier;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * 사용자 본인의 예약을 생성하는 서비스.
 *
 * <p>
 * 락 없는 사전 검증 → 트랜잭션 밖 SmartThings 작동 상태 확인 → 락 획득 후 재검증 및 저장 순으로 처리한다. 외부 호출
 * 중에는 DB 락과 커넥션을 점유하지 않으며, 외부 확인 이후 DB에서 바뀐 상태는 락 아래 재검증으로 걸러낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateReservationServiceImpl implements CreateReservationService {

    private final UserRepository userRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final ReservationEnvironment reservationEnvironment;
    private final CurrentUserProvider currentUserProvider;
    private final ReservationCreationSupport reservationCreationSupport;
    private final ReservationDeviceStateVerifier reservationDeviceStateVerifier;
    private final PlatformTransactionManager transactionManager;

    @Override
    public ReservationResDto execute(final CreateReservationReqDto reqDto) {
        final var userId = currentUserProvider.getCurrentUserId();
        final TransactionTemplate transactionTemplate = readCommittedTransactionTemplate();

        // 실패할 요청이 외부 API를 호출하지 않도록 락 없이 먼저 검증한다
        final Machine machine = transactionTemplate
                .execute(status -> validate(userId, reqDto.machineId(), false).machine());

        reservationDeviceStateVerifier.verifyNotOperating(machine);

        return transactionTemplate.execute(status -> {
            // 락을 획득한 뒤 저장 직전 불변식을 다시 확인한다
            final ValidatedTarget target = validate(userId, reqDto.machineId(), true);

            final Reservation saved = reservationCreationSupport.create(target.user(), target.machine(), null);
            log.info("Created reservation {} for user {} on machine {}",
                    saved.getId(),
                    userId,
                    target.machine().getId());

            return mapToReservationResDto(saved);
        });
    }

    /**
     * 예약 정책과 불변식을 검증합니다.
     *
     * @param userId
     *            예약 주체 사용자 ID
     * @param machineId
     *            예약 대상 기기 ID
     * @param forUpdate
     *            {@code true}이면 정책 범위·사용자·기기를 비관적 쓰기 락으로 조회한다
     * @return 검증을 통과한 사용자와 기기
     */
    private ValidatedTarget validate(final Long userId, final Long machineId, final boolean forUpdate) {
        if (forUpdate) {
            reservationCreationSupport.lockReservationPolicyScope(userId);
        }

        // 사용자 삭제와 직렬화하기 위해 사용자 행을 먼저 잠근다 (락 순서: 사용자 → 기기 → 예약)
        final User user = (forUpdate ? userRepository.findByIdForUpdate(userId) : userRepository.findById(userId))
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final String roomNumber = reservationCreationSupport.validateRoomConstraints(user);

        // 48시간 차단 검증 (호실 단위). 조회에 실패하면 제한을 우회하지 않도록 예약을 거부한다
        switch (penaltyRedisUtil.checkBlock(roomNumber)) {
            case RESTRICTED -> throw new ExpectedException("48시간 내 취소 횟수를 초과하여 예약이 제한됩니다", HttpStatus.BAD_REQUEST);
            case UNAVAILABLE -> throw new ErrorCodeException(ErrorCode.RESERVATION_RESTRICTION_UNAVAILABLE);
            case NONE -> {
            }
        }

        // 시간 제한 검증 (학년별 예약 시작 시각, 개발환경에서는 비활성화 가능)
        if (!reservationEnvironment.disableTimeRestriction()) {
            user.validateTimeRestriction(DateTimeUtil.nowInKorea());
        }

        // 동일 기기 동시 예약 직렬화를 위해 비관적 쓰기 락으로 조회
        final Machine machine = forUpdate
                ? reservationCreationSupport.lockMachine(machineId)
                : reservationCreationSupport.findMachine(machineId);

        // 쿨다운 검증 (취소 후 5분, 동일 기기 유형 한정). 조회에 실패하면 예약을 거부한다
        switch (penaltyRedisUtil.checkCooldown(userId, machine.getType())) {
            case RESTRICTED -> throw new ExpectedException(
                    String.format("예약 취소 후 5분간 %s 예약이 제한됩니다", machine.getType().getDescription()),
                    HttpStatus.BAD_REQUEST);
            case UNAVAILABLE -> throw new ErrorCodeException(ErrorCode.RESERVATION_RESTRICTION_UNAVAILABLE);
            case NONE -> {
            }
        }

        reservationCreationSupport.validateMachineAndReservations(user, machine);

        return new ValidatedTarget(user, machine);
    }

    private TransactionTemplate readCommittedTransactionTemplate() {
        final var transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transactionTemplate;
    }

    private ReservationResDto mapToReservationResDto(final Reservation saved) {
        return new ReservationResDto(saved.getId(),
                saved.getUser().getId(),
                saved.getUser().getName(),
                saved.getUser().getRoomNumber(),
                saved.getUser().getStudentId(),
                saved.getMachine().getId(),
                saved.getMachine().getName(),
                saved.getReservedAt(),
                saved.getStartTime(),
                saved.getExpectedCompletionTime(),
                saved.getActualCompletionTime(),
                saved.getStatus(),
                saved.getCancelledAt(),
                saved.getDayOfWeek(),
                saved.getCreatedAt(),
                saved.getUpdatedAt());
    }

    private record ValidatedTarget(User user, Machine machine) {
    }
}
