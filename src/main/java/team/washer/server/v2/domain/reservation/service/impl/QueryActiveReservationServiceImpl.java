package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.QueryActiveReservationService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@Service
@RequiredArgsConstructor
public class QueryActiveReservationServiceImpl implements QueryActiveReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public ReservationResDto execute() {
        final var userId = currentUserProvider.getCurrentUserId();
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        // createdAt 내림차순으로 조회되므로 첫 건이 가장 최근 활성 예약이다
        final Reservation latest = reservationRepository.findCurrentlyActiveByUser(user).stream().findFirst()
                .orElse(null);

        if (latest == null) {
            return null;
        }

        return new ReservationResDto(latest.getId(),
                latest.getUser().getId(),
                latest.getUser().getName(),
                latest.getUser().getRoomNumber(),
                latest.getUser().getStudentId(),
                latest.getMachine().getId(),
                latest.getMachine().getName(),
                latest.getReservedAt(),
                latest.getStartTime(),
                latest.getExpectedCompletionTime(),
                latest.getActualCompletionTime(),
                latest.getStatus(),
                latest.getCancelledAt(),
                latest.getDayOfWeek(),
                latest.getCreatedAt(),
                latest.getUpdatedAt());
    }
}
