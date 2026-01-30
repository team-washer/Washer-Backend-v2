package team.washer.server.v2.domain.user.service.impl;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.DeleteUserService;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

/**
 * 사용자 삭제 서비스 구현체
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DeleteUserServiceImpl implements DeleteUserService {

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;

    @Override
    public void execute(Long userId) {
        final var user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        // 활성 예약이 있는지 확인
        final var activeStatuses = List
                .of(ReservationStatus.RESERVED, ReservationStatus.CONFIRMED, ReservationStatus.RUNNING);
        final boolean hasActiveReservations = reservationRepository.existsByUserAndStatusIn(user, activeStatuses);

        if (hasActiveReservations) {
            throw new ExpectedException("활성 예약이 있는 사용자는 삭제할 수 없습니다", HttpStatus.BAD_REQUEST);
        }

        // Hard delete
        userRepository.delete(user);
    }
}
