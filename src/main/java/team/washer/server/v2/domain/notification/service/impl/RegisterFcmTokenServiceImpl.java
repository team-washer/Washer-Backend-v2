package team.washer.server.v2.domain.notification.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.notification.service.RegisterFcmTokenService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterFcmTokenServiceImpl implements RegisterFcmTokenService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void execute(final Long userId, final String token) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        user.updateFcmToken(token);
        log.info("FCM token registered/updated: userId={}", userId);
    }
}
