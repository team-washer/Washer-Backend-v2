package team.washer.server.v2.domain.reservation.service.impl;

import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.reservation.dto.request.AdminCreateReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.AdminReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.service.AdminCreateReservationService;
import team.washer.server.v2.domain.reservation.support.ReservationCreationSupport;
import team.washer.server.v2.domain.reservation.support.ReservationDeviceStateVerifier;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

/**
 * 관리자가 특정 사용자를 대신하여 예약을 생성하는 서비스.
 *
 * <p>
 * 예약의 주체는 요청 바디의 {@code userId}이고, 토큰의 관리자는 {@code createdBy}로만 기록된다. 관리자의 현장
 * 판단을 신뢰하므로 시간대 제한·48시간 호실 차단·5분 쿨다운 같은 정책 검증은 우회하지만, 층 제한·호실 세탁 금지·기기 가용성·중복
 * 예약 같은 불변식과 SmartThings 실제 작동 상태 확인은 그대로 적용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCreateReservationServiceImpl implements AdminCreateReservationService {

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ReservationCreationSupport reservationCreationSupport;
    private final ReservationDeviceStateVerifier reservationDeviceStateVerifier;
    private final TransactionTemplate transactionTemplate;

    @Override
    public AdminReservationResDto execute(final AdminCreateReservationReqDto reqDto) {
        final var adminId = currentUserProvider.getCurrentUserId();

        // 실패할 요청이 외부 API를 호출하지 않도록 락 없이 먼저 검증한다
        final Machine machine = transactionTemplate
                .execute(status -> validate(reqDto, adminId, reservationCreationSupport::findMachine).machine());

        // 관리자 대리 예약도 사용자 본인 예약과 같은 기기 작동 상태 정책을 적용한다
        reservationDeviceStateVerifier.verifyNotOperating(machine);

        return transactionTemplate.execute(status -> {
            // 동일 기기 동시 예약 직렬화를 위해 비관적 쓰기 락으로 조회한 뒤 저장 직전 불변식을 다시 확인한다
            final ValidatedTarget target = validate(reqDto, adminId, reservationCreationSupport::lockMachine);

            final Reservation saved = reservationCreationSupport
                    .create(target.targetUser(), target.machine(), target.adminUser());
            log.info("admin created proxy reservation reservationId={} targetUserId={} adminId={} machineId={}",
                    saved.getId(),
                    reqDto.userId(),
                    adminId,
                    target.machine().getId());

            return mapToAdminReservationResDto(saved, target.adminUser());
        });
    }

    private ValidatedTarget validate(final AdminCreateReservationReqDto reqDto,
            final Long adminId,
            final Function<Long, Machine> machineLoader) {
        final User targetUser = userRepository.findById(reqDto.userId())
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        reservationCreationSupport.validateRoomConstraints(targetUser);

        final Machine machine = machineLoader.apply(reqDto.machineId());

        reservationCreationSupport.validateMachineAndReservations(targetUser, machine);

        return new ValidatedTarget(targetUser, adminUser, machine);
    }

    private AdminReservationResDto mapToAdminReservationResDto(final Reservation reservation, final User adminUser) {
        return new AdminReservationResDto(reservation.getId(),
                reservation.getUser().getId(),
                reservation.getUser().getName(),
                reservation.getUser().getRoomNumber(),
                reservation.getUser().getStudentId(),
                reservation.getMachine().getId(),
                reservation.getMachine().getName(),
                reservation.getMachine().getAvailability(),
                reservation.getReservedAt(),
                reservation.getStartTime(),
                reservation.getExpectedCompletionTime(),
                reservation.getActualCompletionTime(),
                reservation.getStatus(),
                reservation.getCancelledAt(),
                adminUser.getName());
    }

    private record ValidatedTarget(User targetUser, User adminUser, Machine machine) {
    }
}
