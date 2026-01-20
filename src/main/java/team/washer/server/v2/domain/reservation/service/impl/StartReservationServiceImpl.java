package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.dto.request.StartReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.StartReservationService;

@Slf4j
@Service
@RequiredArgsConstructor
public class StartReservationServiceImpl implements StartReservationService {

    private final ReservationRepository reservationRepository;

    @Override
    @Transactional
    public ReservationResDto startReservation(final Long userId,
            final Long reservationId,
            final StartReservationReqDto reqDto) {
        final Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다: " + reservationId));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("예약을 시작할 권한이 없습니다");
        }

        if (!reservation.isConfirmed()) {
            throw new IllegalStateException("확인된 예약만 시작할 수 있습니다");
        }

        if (reservation.isExpired()) {
            throw new IllegalStateException("예약이 만료되었습니다");
        }

        reservation.start(reqDto.expectedCompletionTime());
        final Reservation saved = reservationRepository.save(reservation);
        log.info("Started reservation {} by user {}", reservationId, userId);

        return mapToReservationResDto(saved);
    }

    private ReservationResDto mapToReservationResDto(final Reservation reservation) {
        return new ReservationResDto(reservation.getId(),
                reservation.getUser().getId(),
                reservation.getUser().getName(),
                reservation.getUser().getRoomNumber(),
                reservation.getMachine().getId(),
                reservation.getMachine().getName(),
                reservation.getReservedAt(),
                reservation.getStartTime(),
                reservation.getExpectedCompletionTime(),
                reservation.getActualCompletionTime(),
                reservation.getStatus(),
                reservation.getConfirmedAt(),
                reservation.getCancelledAt(),
                reservation.getDayOfWeek(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}
