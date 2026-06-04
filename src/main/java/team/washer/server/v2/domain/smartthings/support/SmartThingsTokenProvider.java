package team.washer.server.v2.domain.smartthings.support;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;

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

    private String reload() {
        final var token = tokenRepository.findSingletonToken()
                .orElseThrow(() -> new ExpectedException("SmartThings 토큰이 존재하지 않습니다", HttpStatus.NOT_FOUND));
        if (!token.isValid()) {
            throw new ExpectedException("SmartThings 토큰이 만료되었거나 유효하지 않습니다", HttpStatus.NOT_FOUND);
        }
        cache.set(new CachedToken(token.getAccessToken(), token.getExpiresAt()));
        log.debug("smartthings token cache loaded from db expiresAt={}", token.getExpiresAt());
        return token.getAccessToken();
    }

    private record CachedToken(String accessToken, LocalDateTime expiresAt) {

        private static final int EXPIRY_BUFFER_MINUTES = 5;

        private boolean isValid() {
            return accessToken != null && !accessToken.isBlank()
                    && expiresAt.isAfter(LocalDateTime.now().plusMinutes(EXPIRY_BUFFER_MINUTES));
        }
    }
}
