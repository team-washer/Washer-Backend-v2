package team.washer.server.v2.domain.notification.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.notification.service.DeleteFcmTokenIfMatchesService;
import team.washer.server.v2.domain.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteFcmTokenIfMatchesServiceImpl implements DeleteFcmTokenIfMatchesService {

    private final UserRepository userRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(final Long userId, final String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        final int deletedCount = userRepository.clearFcmTokenIfMatches(userId, token);
        if (deletedCount == 0) {
            log.info("FCM token deletion skipped because token changed userId={}", userId);
            return;
        }

        log.info("Stale FCM token deleted userId={}", userId);
    }
}
