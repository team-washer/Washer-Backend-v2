package team.washer.server.v2.domain.smartthings.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmartThingsTokenProvider 클래스는")
class SmartThingsTokenProviderTest {

    @InjectMocks
    private SmartThingsTokenProvider smartThingsTokenProvider;

    @Mock
    private SmartThingsTokenRepository tokenRepository;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private SmartThingsToken token(final String accessToken) {
        return SmartThingsToken.builder().accessToken(accessToken).refreshToken("refresh-token")
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
    }

    private SmartThingsToken token(final String accessToken, final LocalDateTime expiresAt) {
        return SmartThingsToken.builder().accessToken(accessToken).refreshToken("refresh-token").expiresAt(expiresAt)
                .build();
    }

    private SmartThingsToken createOldToken() {
        return SmartThingsToken.builder().accessToken("old-access").refreshToken("old-refresh")
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
    }

    private SmartThingsToken createNewToken() {
        return SmartThingsToken.builder().accessToken("new-access").refreshToken("new-refresh")
                .expiresAt(LocalDateTime.now().plusHours(24)).build();
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    @Nested
    @DisplayName("refresh 메서드는")
    class Describe_refresh {

        @Nested
        @DisplayName("트랜잭션 밖에서 호출되면")
        class Context_without_transaction {

            @Test
            @DisplayName("즉시 캐시에 반영해야 한다")
            void it_publishes_immediately() {
                // When
                smartThingsTokenProvider.refresh(createNewToken());

                // Then
                assertThat(smartThingsTokenProvider.getValidAccessToken()).isEqualTo("new-access");
                then(tokenRepository).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("트랜잭션 안에서 호출되면")
        class Context_within_transaction {

            @Test
            @DisplayName("커밋 전에는 기존 캐시를 유지하고 커밋 후에 새 토큰을 반영해야 한다")
            void it_publishes_after_commit() {
                // Given
                given(tokenRepository.findSingletonToken()).willReturn(Optional.of(createOldToken()));
                assertThat(smartThingsTokenProvider.getValidAccessToken()).isEqualTo("old-access");
                beginTransaction();

                // When
                smartThingsTokenProvider.refresh(createNewToken());

                // Then
                assertThat(smartThingsTokenProvider.getValidAccessToken()).isEqualTo("old-access");

                final var synchronizations = TransactionSynchronizationManager.getSynchronizations();
                assertThat(synchronizations).hasSize(1);
                synchronizations.getFirst().afterCommit();

                assertThat(smartThingsTokenProvider.getValidAccessToken()).isEqualTo("new-access");
            }

            @Test
            @DisplayName("롤백되면 새 토큰을 반영하지 않고 DB의 기존 토큰을 사용해야 한다")
            void it_keeps_previous_token_on_rollback() {
                // Given
                given(tokenRepository.findSingletonToken()).willReturn(Optional.of(createOldToken()));
                beginTransaction();

                // When
                smartThingsTokenProvider.refresh(createNewToken());
                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

                // Then
                assertThat(smartThingsTokenProvider.getValidAccessToken()).isEqualTo("old-access");
            }
        }
    }

    @Nested
    @DisplayName("getValidAccessToken 메서드는")
    class Describe_getValidAccessToken {

        @Nested
        @DisplayName("DB 조회 중 더 늦게 만료되는 토큰이 먼저 캐시에 반영되면")
        class Context_with_newer_token_published_during_reload {

            @Test
            @DisplayName("조회한 이전 토큰으로 덮어쓰지 않고 새 토큰을 반환해야 한다")
            void it_keeps_newer_token() {
                // Given
                final var oldToken = createOldToken();
                final var newToken = createNewToken();
                given(tokenRepository.findSingletonToken()).willAnswer(invocation -> {
                    smartThingsTokenProvider.refresh(newToken);
                    return Optional.of(oldToken);
                });

                // When
                final var accessToken = smartThingsTokenProvider.getValidAccessToken();

                // Then
                assertThat(accessToken).isEqualTo("new-access");
                assertThat(smartThingsTokenProvider.getValidAccessToken()).isEqualTo("new-access");
                then(tokenRepository).should(times(1)).findSingletonToken();
            }
        }
    }

    @Nested
    @DisplayName("invalidate 메서드는")
    class Describe_invalidate {

        @Test
        @DisplayName("외부에서 거절된 토큰은 DB에서도 만료되어 다시 사용되지 않는다")
        void invalidates_rejected_token_in_database() {
            final var token = token("rejected-access");
            final var provider = new SmartThingsTokenProvider(tokenRepository);
            given(tokenRepository.findSingletonToken()).willReturn(Optional.of(token));
            given(tokenRepository.findSingletonTokenWithLock()).willReturn(Optional.of(token));

            assertThat(provider.getValidAccessToken()).isEqualTo("rejected-access");
            provider.invalidate("rejected-access");

            assertThatThrownBy(provider::getValidAccessToken).isInstanceOf(ErrorCodeException.class)
                    .satisfies(e -> assertThat(((ErrorCodeException) e).getErrorCode())
                            .isEqualTo(ErrorCode.SMARTTHINGS_TOKEN_INVALID));
        }

        @Test
        @DisplayName("커밋 전에 다시 게시된 거절 토큰도 커밋 뒤 캐시에서 제거한다")
        void clears_rejected_token_republished_before_commit() throws Exception {
            // Given
            final var rejectedToken = token("rejected-access");
            final var databaseToken = token("rejected-access");
            smartThingsTokenProvider.refresh(rejectedToken);
            given(tokenRepository.findSingletonTokenWithLock()).willReturn(Optional.of(databaseToken));
            given(tokenRepository.findSingletonToken()).willReturn(Optional.of(databaseToken));
            beginTransaction();

            // When
            smartThingsTokenProvider.invalidate("rejected-access");
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> smartThingsTokenProvider.refresh(rejectedToken)).get(5, TimeUnit.SECONDS);
            }
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            // Then
            assertThatThrownBy(smartThingsTokenProvider::getValidAccessToken).isInstanceOf(ErrorCodeException.class)
                    .satisfies(e -> assertThat(((ErrorCodeException) e).getErrorCode())
                            .isEqualTo(ErrorCode.SMARTTHINGS_TOKEN_INVALID));
        }

        @Test
        @DisplayName("다른 인스턴스가 갱신한 최신 토큰은 과거 401 응답으로 무효화하지 않는다")
        void preserves_newer_token_after_stale_rejection() {
            final var currentToken = token("new-access");
            final var provider = new SmartThingsTokenProvider(tokenRepository);
            provider.refresh(currentToken);
            given(tokenRepository.findSingletonTokenWithLock()).willReturn(Optional.of(currentToken));

            provider.invalidate("old-access");

            assertThat(provider.getValidAccessToken()).isEqualTo("new-access");
            assertThat(currentToken.isValid()).isTrue();
            then(tokenRepository).should(never()).findSingletonToken();
        }

        @Test
        @DisplayName("거절된 캐시 토큰보다 DB 토큰의 만료 시각이 이르더라도 DB 토큰으로 교체한다")
        void replaces_rejected_token_even_when_replacement_expires_earlier() {
            // Given
            final var rejectedToken = token("rejected-access", LocalDateTime.now().plusHours(24));
            final var replacementToken = token("replacement-access", LocalDateTime.now().plusHours(1));
            smartThingsTokenProvider.refresh(rejectedToken);
            given(tokenRepository.findSingletonTokenWithLock()).willReturn(Optional.of(replacementToken));

            // When
            smartThingsTokenProvider.invalidate("rejected-access");

            // Then
            assertThat(smartThingsTokenProvider.getValidAccessToken()).isEqualTo("replacement-access");
        }

        @Test
        @DisplayName("동시에 거절된 이전 토큰을 무효화해도 최종 캐시에 거절된 토큰을 남기지 않는다")
        void removes_rejected_token_when_multiple_requests_receive_unauthorized() throws Exception {
            // Given
            final var rejectedToken = token("rejected-access", LocalDateTime.now().plusHours(24));
            final var replacementToken = token("replacement-access", LocalDateTime.now().plusHours(1));
            final var barrier = new CyclicBarrier(2);
            smartThingsTokenProvider.refresh(rejectedToken);
            given(tokenRepository.findSingletonTokenWithLock()).willAnswer(invocation -> {
                barrier.await(5, TimeUnit.SECONDS);
                return Optional.of(replacementToken);
            });

            // When
            try (var executor = Executors.newFixedThreadPool(2)) {
                final var first = executor.submit(() -> smartThingsTokenProvider.invalidate("rejected-access"));
                final var second = executor.submit(() -> smartThingsTokenProvider.invalidate("rejected-access"));

                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
            }

            // Then
            assertThat(smartThingsTokenProvider.getValidAccessToken()).isEqualTo("replacement-access");
        }

        @Test
        @DisplayName("DB 조회 뒤 다른 요청이 게시한 새 토큰을 거절 토큰 처리로 덮어쓰지 않는다")
        void preserves_new_token_published_after_database_read() throws Exception {
            // Given
            final var rejectedToken = token("rejected-access", LocalDateTime.now().plusHours(24));
            final var replacementToken = token("replacement-access", LocalDateTime.now().plusHours(1));
            final var newerToken = token("newer-access", LocalDateTime.now().plusHours(48));
            final var databaseRead = new CountDownLatch(1);
            final var allowReplacement = new CountDownLatch(1);
            smartThingsTokenProvider.refresh(rejectedToken);
            given(tokenRepository.findSingletonTokenWithLock()).willAnswer(invocation -> {
                databaseRead.countDown();
                if (!allowReplacement.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("새 토큰 게시 대기 시간이 초과되었습니다");
                }
                return Optional.of(replacementToken);
            });

            // When
            try (var executor = Executors.newSingleThreadExecutor()) {
                final var invalidation = executor.submit(() -> smartThingsTokenProvider.invalidate("rejected-access"));
                assertThat(databaseRead.await(5, TimeUnit.SECONDS)).isTrue();
                smartThingsTokenProvider.refresh(newerToken);
                allowReplacement.countDown();
                invalidation.get();
            }

            // Then
            assertThat(smartThingsTokenProvider.getValidAccessToken()).isEqualTo("newer-access");
        }

        @Test
        @DisplayName("트랜잭션 안에서도 DB의 최신 토큰을 커밋을 기다리지 않고 즉시 캐시에 반영해야 한다")
        void publishes_current_token_without_waiting_for_commit() {
            // Given
            given(tokenRepository.findSingletonToken()).willReturn(Optional.of(createOldToken()));
            assertThat(smartThingsTokenProvider.getValidAccessToken()).isEqualTo("old-access");
            given(tokenRepository.findSingletonTokenWithLock()).willReturn(Optional.of(createNewToken()));
            beginTransaction();

            // When
            smartThingsTokenProvider.invalidate("old-access");

            // Then
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
            assertThat(smartThingsTokenProvider.getValidAccessToken()).isEqualTo("new-access");
        }

        @Test
        @DisplayName("다른 인스턴스가 DB에서 무효화한 같은 토큰은 캐시의 늦은 만료 시각을 유지하지 않아야 한다")
        void applies_shortened_expiry_of_rejected_token() {
            // Given
            smartThingsTokenProvider.refresh(createNewToken());
            final var invalidatedToken = createNewToken();
            invalidatedToken.invalidateAccessToken();
            given(tokenRepository.findSingletonTokenWithLock()).willReturn(Optional.of(invalidatedToken));
            given(tokenRepository.findSingletonToken()).willReturn(Optional.of(invalidatedToken));

            // When
            smartThingsTokenProvider.invalidate("old-access");

            // Then
            assertThatThrownBy(smartThingsTokenProvider::getValidAccessToken).isInstanceOf(ErrorCodeException.class)
                    .satisfies(e -> assertThat(((ErrorCodeException) e).getErrorCode())
                            .isEqualTo(ErrorCode.SMARTTHINGS_TOKEN_INVALID));
        }
    }
}
