package team.washer.server.v2.domain.reservation.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.QueryActiveReservationService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class QueryActiveReservationServiceImpl implements QueryActiveReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public ReservationResDto execute() {
        final var userId = (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        final User user = userRepository.findById(Objects.requireNonNull(userId))
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final List<Reservation> activeReservations = reservationRepository.findByUserAndStatusIn(user,
                List.of(ReservationStatus.RESERVED, ReservationStatus.CONFIRMED, ReservationStatus.RUNNING));

        final Reservation latest = activeReservations.stream().filter(r -> !r.isExpired())
                .max(Comparator.comparing(Reservation::getCreatedAt)).orElse(null);

        if (latest == null) {
            return null;
        }

        return new ReservationResDto(latest.getId(),
                latest.getUser().getId(),
                latest.getUser().getName(),
                latest.getUser().getRoomNumber(),
                latest.getMachine().getId(),
                latest.getMachine().getName(),
                latest.getReservedAt(),
                latest.getStartTime(),
                latest.getExpectedCompletionTime(),
                latest.getActualCompletionTime(),
                latest.getStatus(),
                latest.getConfirmedAt(),
                latest.getCancelledAt(),
                latest.getDayOfWeek(),
                latest.getCreatedAt(),
                latest.getUpdatedAt());
    }
}
