package team.washer.server.v2.domain.smartthings.support;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmartThingsTokenProvider {

    private final SmartThingsTokenRepository tokenRepository;
    private final AtomicReference<CachedToken> cache = new AtomicReference<>();

    public String getValidAccessToken() {
        final var cached = cache.get();
        if (cached != null && cached.isValid()) {
            return cached.accessToken();
        }
        return reload();
    }

    public void refresh(final SmartThingsToken token) {
        cache.set(new CachedToken(token.getAccessToken(), token.getExpiresAt()));
        log.debug("smartthings token cache refreshed expiresAt={}", token.getExpiresAt());
    }

    @Transactional
    public void invalidate(final String rejectedAccessToken) {
        if (rejectedAccessToken == null || rejectedAccessToken.isBlank()) {
            return;
        }

        final var token = tokenRepository.findSingletonTokenWithLock().orElse(null);
        if (token == null) {
            clearCachedToken(rejectedAccessToken);
            return;
        }
        if (!token.getAccessToken().equals(rejectedAccessToken)) {
            refresh(token);
            return;
        }

        token.invalidateAccessToken();
        clearCachedToken(rejectedAccessToken);
        log.warn("smartthings access token invalidated after upstream rejection");
    }

    private String reload() {
        final var token = tokenRepository.findSingletonToken()
                .orElseThrow(() -> new ErrorCodeException(ErrorCode.SMARTTHINGS_TOKEN_UNAVAILABLE));
        if (!token.isValid()) {
            throw new ErrorCodeException(ErrorCode.SMARTTHINGS_TOKEN_INVALID);
        }
        cache.set(new CachedToken(token.getAccessToken(), token.getExpiresAt()));
        log.debug("smartthings token cache loaded from db expiresAt={}", token.getExpiresAt());
        return token.getAccessToken();
    }

    private void clearCachedToken(final String rejectedAccessToken) {
        cache.updateAndGet(
                cached -> cached != null && cached.accessToken().equals(rejectedAccessToken) ? null : cached);
    }

    private record CachedToken(String accessToken, LocalDateTime expiresAt) {

        private static final int EXPIRY_BUFFER_MINUTES = 5;

        private boolean isValid() {
            return accessToken != null && !accessToken.isBlank() && expiresAt != null
                    && expiresAt.isAfter(LocalDateTime.now().plusMinutes(EXPIRY_BUFFER_MINUTES));
        }
    }
}
