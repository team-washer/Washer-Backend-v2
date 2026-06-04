package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.dto.response.ReservationAvailabilityResDto;
import team.washer.server.v2.domain.reservation.service.QueryReservationAvailabilityService;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@Service
@RequiredArgsConstructor
public class QueryReservationAvailabilityServiceImpl implements QueryReservationAvailabilityService {

    private final PenaltyRedisUtil penaltyRedisUtil;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public ReservationAvailabilityResDto execute() {
        final var userId = currentUserProvider.getCurrentUserId();
        final var roomNumber = userRepository.findById(userId).map(u -> u.getRoomNumber()).orElse(null);

        // 48시간 블록(호실 단위)이면 모든 예약 불가
        final boolean blocked = roomNumber != null && penaltyRedisUtil.isBlocked(roomNumber);

        // 쿨다운은 유형별이므로 모든 기기 유형이 쿨다운 중일 때만 전체 예약 불가로 간주
        boolean allTypesInCooldown = true;
        for (final MachineType machineType : MachineType.values()) {
            if (!penaltyRedisUtil.isInCooldown(userId, machineType)) {
                allTypesInCooldown = false;
                break;
            }
        }

        if (blocked || allTypesInCooldown) {
            return new ReservationAvailabilityResDto(false, penaltyRedisUtil.getPenaltyExpiryTime(userId));
        }

        return new ReservationAvailabilityResDto(true, null);
    }
}
