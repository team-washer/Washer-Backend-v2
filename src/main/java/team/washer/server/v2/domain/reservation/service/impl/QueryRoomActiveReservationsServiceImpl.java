package team.washer.server.v2.domain.reservation.service.impl;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.QueryRoomActiveReservationsService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@Service
@RequiredArgsConstructor
public class QueryRoomActiveReservationsServiceImpl implements QueryRoomActiveReservationsService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResDto> execute() {
        final var userId = currentUserProvider.getCurrentUserId();
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        return reservationRepository.findActiveReservationsByRoomNumber(user.getRoomNumber()).stream()
                .filter(r -> !r.isExpired())
                .map(r -> new ReservationResDto(r.getId(),
                        r.getUser().getId(),
                        r.getUser().getName(),
                        r.getUser().getRoomNumber(),
                        r.getMachine().getId(),
                        r.getMachine().getName(),
                        r.getReservedAt(),
                        r.getStartTime(),
                        r.getExpectedCompletionTime(),
                        r.getActualCompletionTime(),
                        r.getStatus(),
                        r.getConfirmedAt(),
                        r.getCancelledAt(),
                        r.getDayOfWeek(),
                        r.getCreatedAt(),
                        r.getUpdatedAt()))
                .toList();
    }
}
