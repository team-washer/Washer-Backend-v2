package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.reservation.dto.request.AdminCreateReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.AdminReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.service.AdminCreateReservationService;
import team.washer.server.v2.domain.reservation.support.ReservationCreationSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

/**
 * 관리자가 특정 사용자를 대신하여 예약을 생성하는 서비스.
 *
 * <p>
 * 예약의 주체는 요청 바디의 {@code userId}이고, 토큰의 관리자는 {@code createdBy}로만 기록된다. 관리자의 현장
 * 판단을 신뢰하므로 시간대 제한·48시간 호실 차단·5분 쿨다운 같은 정책 검증은 우회하지만, 층 제한·호실 세탁 금지·기기 가용성·중복
 * 예약 같은 불변식은 그대로 적용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCreateReservationServiceImpl implements AdminCreateReservationService {

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ReservationCreationSupport reservationCreationSupport;

    @Override
    @Transactional
    public AdminReservationResDto execute(final AdminCreateReservationReqDto reqDto) {
        final User targetUser = userRepository.findById(reqDto.userId())
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final var adminId = currentUserProvider.getCurrentUserId();
        final User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        reservationCreationSupport.validateRoomConstraints(targetUser);

        // 동일 기기 동시 예약 직렬화를 위해 비관적 쓰기 락으로 조회
        final Machine machine = reservationCreationSupport.lockMachine(reqDto.machineId());

        reservationCreationSupport.validateMachineAndReservations(targetUser, machine);

        final Reservation saved = reservationCreationSupport.create(targetUser, machine, adminUser);
        log.info("admin created proxy reservation reservationId={} targetUserId={} adminId={} machineId={}",
                saved.getId(),
                reqDto.userId(),
                adminId,
                machine.getId());

        return mapToAdminReservationResDto(saved, adminUser);
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
}
