package team.washer.server.v2.domain.user.service.impl;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.auth.repository.redis.RefreshTokenRedisRepository;
import team.washer.server.v2.domain.auth.util.WithdrawnStudentRedisUtil;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.WithdrawUserService;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@Service
@RequiredArgsConstructor
public class WithdrawUserServiceImpl implements WithdrawUserService {

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final MachineRepository machineRepository;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final WithdrawnStudentRedisUtil withdrawnStudentRedisUtil;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public void execute() {
        final var userId = currentUserProvider.getCurrentUserId();
        final var user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final var activeStatuses = List.of(ReservationStatus.RESERVED, ReservationStatus.RUNNING);
        final var activeReservations = reservationRepository.findByUserAndStatusIn(user, activeStatuses);
        for (final var reservation : activeReservations) {
            final var machine = reservation.getMachine();
            reservation.cancel();
            machine.markAsAvailable();
            machineRepository.save(machine);
        }

        refreshTokenRedisRepository.deleteById(userId);

        withdrawnStudentRedisUtil.markWithdrawn(user.getStudentId());

        userRepository.delete(user);
    }
}
