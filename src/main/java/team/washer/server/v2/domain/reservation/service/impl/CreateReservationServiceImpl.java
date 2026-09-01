package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;
import team.washer.server.v2.global.util.DateTimeUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateReservationServiceImpl implements CreateReservationService {

    private final UserRepository userRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final ReservationEnvironment reservationEnvironment;
    private final CurrentUserProvider currentUserProvider;
    private final ReservationCreationSupport reservationCreationSupport;

    @Override
    @Transactional
    public ReservationResDto execute(final CreateReservationReqDto reqDto) {
        final var userId = currentUserProvider.getCurrentUserId();
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final String roomNumber = reservationCreationSupport.validateRoomConstraints(user);

        // 48시간 차단 검증 (호실 단위)
        if (penaltyRedisUtil.isBlocked(roomNumber)) {
            throw new ExpectedException("48시간 내 취소 횟수를 초과하여 예약이 제한됩니다", HttpStatus.BAD_REQUEST);
        }

        // 시간 제한 검증 (학년별 예약 시작 시각, 개발환경에서는 비활성화 가능)
        if (!reservationEnvironment.disableTimeRestriction()) {
            user.validateTimeRestriction(DateTimeUtil.nowInKorea());
        }

        // 동일 기기 동시 예약 직렬화를 위해 비관적 쓰기 락으로 조회
        final Machine machine = reservationCreationSupport.lockMachine(reqDto.machineId());

        // 쿨다운 검증 (취소 후 5분, 동일 기기 유형 한정)
        if (penaltyRedisUtil.isInCooldown(userId, machine.getType())) {
            throw new ExpectedException(String.format("예약 취소 후 5분간 %s 예약이 제한됩니다", machine.getType().getDescription()),
                    HttpStatus.BAD_REQUEST);
        }

        reservationCreationSupport.validateMachineAndReservations(user, machine);

        final Reservation saved = reservationCreationSupport.create(user, machine, null);
        log.info("Created reservation {} for user {} on machine {}", saved.getId(), userId, machine.getId());

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
}
