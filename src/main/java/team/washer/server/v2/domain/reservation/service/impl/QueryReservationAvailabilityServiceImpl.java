package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.admin.repository.WashingBanRepository;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.dto.response.ReservationAvailabilityResDto;
import team.washer.server.v2.domain.reservation.service.QueryReservationAvailabilityService;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@Service
@RequiredArgsConstructor
public class QueryReservationAvailabilityServiceImpl implements QueryReservationAvailabilityService {

    private final WashingBanRepository washingBanRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public ReservationAvailabilityResDto execute() {
        final var userId = currentUserProvider.getCurrentUserId();
        final var user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        final String roomNumber = user.getRoomNumber();

        // 호실 세탁 강제 금지 여부
        final boolean isBanned = washingBanRepository.existsByRoomNumber(roomNumber);

        // 48시간 블록(호실 단위)이면 모든 예약 불가
        final boolean blocked = penaltyRedisUtil.isBlocked(roomNumber);

        // 쿨다운은 유형별이므로 모든 기기 유형이 쿨다운 중일 때만 전체 예약 불가로 간주
        boolean allTypesInCooldown = true;
        for (final MachineType machineType : MachineType.values()) {
            if (!penaltyRedisUtil.isInCooldown(userId, machineType)) {
                allTypesInCooldown = false;
                break;
            }
        }

        if (isBanned || blocked || allTypesInCooldown) {
            return new ReservationAvailabilityResDto(false, penaltyRedisUtil.getPenaltyExpiryTime(userId), isBanned);
        }

        return new ReservationAvailabilityResDto(true, null, false);
    }
}
