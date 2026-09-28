package team.washer.server.v2.domain.auth.repository.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.auth.dto.request.RefreshTokenReqDto;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.auth.entity.redis.RefreshTokenEntity;
import team.washer.server.v2.domain.auth.service.impl.RefreshTokenServiceImpl;
import team.washer.server.v2.domain.auth.support.TokenGenerationSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.jwt.config.JwtEnvironment;
import team.washer.server.v2.global.security.jwt.dto.JwtPayload;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RefreshTokenRedisStructureTest.RedisTestConfiguration.class)
class RefreshTokenRedisStructureTest {
    private static final int REDIS_PORT = 6379;
    private static final Long USER_ID = 42L;
    private static final Long TTL_SECONDS = 3600L;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    @Autowired
    private RefreshTokenRedisRepository refreshTokenRedisRepository;

    @Autowired
    private RefreshTokenRedisStore refreshTokenRedisStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @DynamicPropertySource
    static void registerRedisProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableRedisRepositories(basePackageClasses = RefreshTokenRedisRepository.class)
    @ImportAutoConfiguration(DataRedisAutoConfiguration.class)
    @Import(RefreshTokenRedisStore.class)
    static class RedisTestConfiguration {
    }

    @BeforeEach
    void setUp() {
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void atomicStoreKeepsSpringDataRedisIndexesCompatible() {
        refreshTokenRedisStore.replace(USER_ID, "refresh-token-value", TTL_SECONDS);

        final var entity = refreshTokenRedisRepository.findByToken("refresh-token-value").orElseThrow();
        assertThat(entity.getUserId()).isEqualTo(USER_ID);
        assertThat(stringRedisTemplate.type(RefreshTokenRedisStore.userKey(USER_ID))).isEqualTo(DataType.HASH);
        assertThat(stringRedisTemplate.opsForSet().members("auth:refresh-token:user"))
                .containsExactly(String.valueOf(USER_ID));
        assertThat(stringRedisTemplate.opsForSet().members(RefreshTokenRedisStore.userKey(USER_ID) + ":idx"))
                .containsExactly(RefreshTokenRedisStore.tokenIndexKey("refresh-token-value"));
        assertThat(stringRedisTemplate.getExpire(RefreshTokenRedisStore.userKey(USER_ID))).isBetween(TTL_SECONDS - 2,
                TTL_SECONDS);
    }

    @Test
    void replaceRemovesThePreviousTokenIndexAtomically() {
        refreshTokenRedisRepository
                .save(RefreshTokenEntity.builder().userId(USER_ID).token("old-refresh-token").ttl(TTL_SECONDS).build());
        refreshTokenRedisStore.replace(USER_ID, "new-refresh-token", TTL_SECONDS);

        assertThat(refreshTokenRedisRepository.findByToken("old-refresh-token")).isEmpty();
        assertThat(refreshTokenRedisRepository.findByToken("new-refresh-token")).isPresent();
        assertThat(stringRedisTemplate.hasKey(RefreshTokenRedisStore.tokenIndexKey("old-refresh-token"))).isFalse();
        assertThat(stringRedisTemplate.opsForSet().members(RefreshTokenRedisStore.userKey(USER_ID) + ":idx"))
                .containsExactly(RefreshTokenRedisStore.tokenIndexKey("new-refresh-token"));
    }

    @Test
    void onlyOneConcurrentRotationConsumesThePreviousToken() throws Exception {
        refreshTokenRedisStore.replace(USER_ID, "old-refresh-token", TTL_SECONDS);
        final var barrier = new CyclicBarrier(2);
        final var executor = Executors.newFixedThreadPool(2);
        try {
            final var first = executor.submit(() -> rotateAfterBarrier(barrier, "new-refresh-token-1"));
            final var second = executor.submit(() -> rotateAfterBarrier(barrier, "new-refresh-token-2"));
            final var results = new ArrayList<Boolean>();
            results.add(first.get());
            results.add(second.get());

            assertThat(results).containsExactlyInAnyOrder(true, false);
            final var storedToken = refreshTokenRedisRepository.findById(USER_ID).orElseThrow().getToken();
            assertThat(storedToken).isIn("new-refresh-token-1", "new-refresh-token-2");
            assertThat(refreshTokenRedisRepository.findByToken("old-refresh-token")).isEmpty();
            assertThat(refreshTokenRedisRepository.findByToken(storedToken)).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rotationRejectsARefreshTokenStoredForAnotherUser() {
        refreshTokenRedisStore.replace(USER_ID, "user-42-refresh-token", TTL_SECONDS);

        assertThat(refreshTokenRedisStore.rotate(43L, "user-42-refresh-token", "new-refresh-token", TTL_SECONDS))
                .isFalse();
        assertThat(refreshTokenRedisRepository.findByToken("user-42-refresh-token")).isPresent();
        assertThat(refreshTokenRedisRepository.findByToken("new-refresh-token")).isEmpty();
    }

    @Test
    void repositoryDeleteRemovesIndexesCreatedByAtomicStore() {
        refreshTokenRedisStore.replace(USER_ID, "refresh-token-value", TTL_SECONDS);

        refreshTokenRedisRepository.deleteById(USER_ID);

        assertThat(refreshTokenRedisRepository.findById(USER_ID)).isEmpty();
        assertThat(refreshTokenRedisRepository.findByToken("refresh-token-value")).isEmpty();
        assertThat(stringRedisTemplate.hasKey(RefreshTokenRedisStore.tokenIndexKey("refresh-token-value"))).isFalse();
        assertThat(stringRedisTemplate.hasKey(RefreshTokenRedisStore.userKey(USER_ID) + ":idx")).isFalse();
    }

    @Test
    void concurrentServiceRefreshRequestsReturnOneSuccessAndKeepItsToken() throws Exception {
        final var oldToken = "old-service-refresh-token";
        refreshTokenRedisStore.replace(USER_ID, oldToken, TTL_SECONDS);

        final var jwtTokenProvider = mock(JwtTokenProvider.class);
        final var userRepository = mock(UserRepository.class);
        final var jwtEnvironment = mock(JwtEnvironment.class);
        final var generatedTokenSequence = new AtomicInteger();
        final var user = User.builder().name("테스트 사용자").studentId("20260001").roomNumber("301").grade(3).floor(3)
                .penaltyCount(0).role(UserRole.USER).build();

        given(jwtTokenProvider.parseRefreshToken(oldToken)).willReturn(new JwtPayload(USER_ID, null));
        given(jwtTokenProvider.generateAccessToken(USER_ID, UserRole.USER)).willReturn("new-access-token");
        given(jwtTokenProvider.generateRefreshToken(USER_ID))
                .willAnswer(invocation -> "new-service-token-" + generatedTokenSequence.incrementAndGet());
        given(jwtEnvironment.accessTokenExpiration()).willReturn(3600L);
        given(jwtEnvironment.refreshTokenExpiration()).willReturn(TTL_SECONDS);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        final var tokenGenerationSupport = new TokenGenerationSupport(jwtTokenProvider,
                refreshTokenRedisStore,
                jwtEnvironment);
        final var refreshTokenService = new RefreshTokenServiceImpl(jwtTokenProvider,
                userRepository,
                tokenGenerationSupport);
        final var barrier = new CyclicBarrier(2);
        final var executor = Executors.newFixedThreadPool(2);
        try {
            final var first = executor.submit(() -> executeRefreshAfterBarrier(refreshTokenService, barrier, oldToken));
            final var second = executor
                    .submit(() -> executeRefreshAfterBarrier(refreshTokenService, barrier, oldToken));
            final var results = new ArrayList<Optional<TokenResDto>>();
            results.add(first.get());
            results.add(second.get());

            assertThat(results).filteredOn(Optional::isPresent).hasSize(1);
            assertThat(results).filteredOn(Optional::isEmpty).hasSize(1);
            final var successfulToken = results.stream().flatMap(Optional::stream).findFirst().orElseThrow()
                    .refreshToken();
            assertThat(refreshTokenRedisRepository.findByToken(successfulToken)).isPresent();
            assertThat(refreshTokenRedisRepository.findByToken(oldToken)).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentLoginAndRotationLeaveOneConsistentToken() throws Exception {
        refreshTokenRedisStore.replace(USER_ID, "old-refresh-token", TTL_SECONDS);
        final var barrier = new CyclicBarrier(2);
        final var executor = Executors.newFixedThreadPool(2);
        try {
            final var rotation = executor.submit(() -> {
                barrier.await();
                return refreshTokenRedisStore.rotate(USER_ID, "old-refresh-token", "new-refresh-token", TTL_SECONDS);
            });
            final var login = executor.submit(() -> {
                barrier.await();
                refreshTokenRedisStore.replace(USER_ID, "new-login-refresh-token", TTL_SECONDS);
                return true;
            });

            rotation.get();
            assertThat(login.get()).isTrue();
            final var storedToken = refreshTokenRedisRepository.findById(USER_ID).orElseThrow().getToken();
            assertThat(storedToken).isIn("new-refresh-token", "new-login-refresh-token");
            assertThat(refreshTokenRedisRepository.findByToken(storedToken)).isPresent();
            assertThat(refreshTokenRedisRepository.findByToken("old-refresh-token")).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean rotateAfterBarrier(final CyclicBarrier barrier, final String newToken) throws Exception {
        barrier.await();
        return refreshTokenRedisStore.rotate(USER_ID, "old-refresh-token", newToken, TTL_SECONDS);
    }

    private Optional<TokenResDto> executeRefreshAfterBarrier(final RefreshTokenServiceImpl refreshTokenService,
            final CyclicBarrier barrier,
            final String oldToken) throws Exception {
        barrier.await();
        try {
            return Optional.of(refreshTokenService.execute(new RefreshTokenReqDto(oldToken)));
        } catch (final ExpectedException exception) {
            assertThat(exception.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return Optional.empty();
        }
    }
}
