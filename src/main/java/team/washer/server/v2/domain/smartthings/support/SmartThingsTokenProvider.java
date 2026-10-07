package team.washer.server.v2.domain.smartthings.support;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    /**
     * 저장한 토큰을 캐시에 반영한다.
     *
     * <p>
     * 트랜잭션 안에서 호출되면 커밋이 성공한 뒤에만 반영하고, 롤백되면 기존 캐시를 유지한다. 트랜잭션 밖에서는 즉시 반영한다.
     * </p>
     *
     * @param token
     *            저장한 토큰
     */
    public void refresh(final SmartThingsToken token) {
        final var refreshed = new CachedToken(token.getAccessToken(), token.getExpiresAt());
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    publish(refreshed);
                    log.debug("smartthings token cache refreshed after commit expiresAt={}", refreshed.expiresAt());
                }
            });
            return;
        }

        publish(refreshed);
        log.debug("smartthings token cache refreshed expiresAt={}", refreshed.expiresAt());
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
            replaceRejectedCachedToken(rejectedAccessToken,
                    new CachedToken(token.getAccessToken(), token.getExpiresAt()));
            return;
        }

        token.invalidateAccessToken();
        clearCachedToken(rejectedAccessToken);
        clearRejectedTokenAfterCommit(rejectedAccessToken);
        log.warn("smartthings access token invalidated after upstream rejection");
    }

    private String reload() {
        final var token = tokenRepository.findSingletonToken()
                .orElseThrow(() -> new ErrorCodeException(ErrorCode.SMARTTHINGS_TOKEN_UNAVAILABLE));
        if (!token.isValid()) {
            throw new ErrorCodeException(ErrorCode.SMARTTHINGS_TOKEN_INVALID);
        }
        final var published = publish(new CachedToken(token.getAccessToken(), token.getExpiresAt()));
        log.debug("smartthings token cache loaded from db expiresAt={}", published.expiresAt());
        return published.accessToken();
    }

    /**
     * 서로 다른 토큰이면 만료 시각이 더 늦은 토큰만 캐시에 남긴다. 먼저 조회한 이전 토큰이 나중에 확정된 토큰을 덮어쓰지 않도록 한다. 같은
     * 토큰이면 무효화로 앞당겨진 만료 시각도 반영한다.
     */
    private CachedToken publish(final CachedToken candidate) {
        return cache.updateAndGet(current -> current != null && !current.accessToken().equals(candidate.accessToken())
                && current.expiresLaterThan(candidate) ? current : candidate);
    }

    private void clearCachedToken(final String rejectedAccessToken) {
        cache.updateAndGet(
                cached -> cached != null && cached.accessToken().equals(rejectedAccessToken) ? null : cached);
    }

    private void clearRejectedTokenAfterCommit(final String rejectedAccessToken) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                clearCachedToken(rejectedAccessToken);
            }
        });
    }

    /**
     * 거절된 토큰이 캐시에 남아 있을 때만 DB의 확정 토큰으로 교체한다.
     *
     * <p>
     * 401 처리에서는 만료 시각이 아니라 실제로 거절된 토큰을 기준으로 비교해야 한다. 다른 요청이 이미 새 토큰을 반영한 경우에는 그 값을
     * 보존한다.
     * </p>
     */
    private void replaceRejectedCachedToken(final String rejectedAccessToken, final CachedToken replacement) {
        cache.updateAndGet(cached -> cached == null || cached.accessToken().equals(rejectedAccessToken)
                || cached.accessToken().equals(replacement.accessToken()) ? replacement : cached);
    }

    private record CachedToken(String accessToken, LocalDateTime expiresAt) {

        private static final int EXPIRY_BUFFER_MINUTES = 5;

        private boolean isValid() {
            return accessToken != null && !accessToken.isBlank() && expiresAt != null
                    && expiresAt.isAfter(LocalDateTime.now().plusMinutes(EXPIRY_BUFFER_MINUTES));
        }

        private boolean expiresLaterThan(final CachedToken other) {
            return expiresAt != null && other.expiresAt() != null && expiresAt.isAfter(other.expiresAt());
        }
    }
}
