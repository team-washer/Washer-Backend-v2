package team.washer.server.v2.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisKeyValueAdapter.EnableKeyspaceEvents;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import team.washer.server.v2.domain.admin.entity.WashingBan;
import team.washer.server.v2.domain.admin.service.impl.QueryAdminDashboardServiceImpl;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.entity.redis.CancellationBlockEntity;
import team.washer.server.v2.domain.reservation.entity.redis.CooldownEntity;
import team.washer.server.v2.domain.reservation.enums.RestrictionStatus;
import team.washer.server.v2.domain.reservation.repository.redis.CooldownRedisRepository;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.config.JpaAuditingConfig;
import team.washer.server.v2.global.config.QueryDslConfig;

@DataJpaTest
@Testcontainers
@Import({JpaAuditingConfig.class, QueryDslConfig.class, PenaltyRedisUtil.class, QueryAdminDashboardServiceImpl.class,
        QueryAdminDashboardSuspendedStudentsIntegrationTest.RedisTestConfiguration.class})
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:suspended_students_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.docker.compose.enabled=false"})
@DisplayName("세탁 정지 학생 통계 DB·Redis 연계 테스트")
class QueryAdminDashboardSuspendedStudentsIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void registerRedisProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableRedisRepositories(basePackageClasses = CooldownRedisRepository.class, enableKeyspaceEvents = EnableKeyspaceEvents.ON_STARTUP)
    @ImportAutoConfiguration(DataRedisAutoConfiguration.class)
    static class RedisTestConfiguration {
    }

    @Autowired
    private QueryAdminDashboardServiceImpl queryAdminDashboardService;

    @Autowired
    private PenaltyRedisUtil penaltyRedisUtil;

    @Autowired
    private CooldownRedisRepository cooldownRedisRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        stringRedisTemplate.execute(connection -> {
            connection.serverCommands().flushAll();
            return null;
        }, true);
    }

    private User persistUser(final String studentId, final String roomNumber) {
        final User user = entityManager.persist(User.builder().name("학생" + studentId).studentId(studentId)
                .roomNumber(roomNumber).grade(2).floor(3).build());
        entityManager.flush();
        return user;
    }

    private WashingBan persistWashingBan(final String roomNumber) {
        final WashingBan washingBan = entityManager.persist(WashingBan.builder().roomNumber(roomNumber).build());
        entityManager.flush();
        return washingBan;
    }

    private long suspendedStudents() {
        return queryAdminDashboardService.execute().suspendedStudents();
    }

    @Nested
    @DisplayName("쿨다운은")
    class Describe_cooldown {

        @Test
        @DisplayName("세탁기·건조기 중 한 유형이라도 활성이면 사용자를 한 번만 센다")
        void it_counts_user_once_per_any_machine_type() {
            // Given
            final User washerOnly = persistUser("20240001", "301");
            final User both = persistUser("20240002", "302");
            persistUser("20240003", "303");
            penaltyRedisUtil.applyCooldown(washerOnly.getId(), MachineType.WASHER);
            penaltyRedisUtil.applyCooldown(both.getId(), MachineType.WASHER);
            penaltyRedisUtil.applyCooldown(both.getId(), MachineType.DRYER);

            // When & Then
            assertThat(suspendedStudents()).isEqualTo(2L);
        }

        @Test
        @DisplayName("만료되면 예약 생성 판정과 함께 집계에서도 빠진다")
        void it_excludes_expired_cooldown() {
            // Given
            final User user = persistUser("20240001", "301");
            cooldownRedisRepository
                    .save(CooldownEntity.builder().id(user.getId() + ":" + MachineType.DRYER.name()).ttl(1L).build());
            assertThat(suspendedStudents()).isEqualTo(1L);

            // When & Then
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(penaltyRedisUtil.checkCooldown(user.getId(), MachineType.DRYER))
                        .isEqualTo(RestrictionStatus.NONE);
                assertThat(suspendedStudents()).isZero();
            });
        }
    }

    @Nested
    @DisplayName("호실 단위 제한은")
    class Describe_room_restriction {

        @Test
        @DisplayName("Redis 호실 차단(관리자 연장 포함)이 있으면 해당 호실 사용자를 모두 센다")
        void it_counts_all_users_in_blocked_room() {
            // Given
            persistUser("20240001", "301");
            persistUser("20240002", "301");
            persistUser("20240003", "302");
            penaltyRedisUtil.applyBlockOrThrow("301");
            penaltyRedisUtil.extendBlock("301", 1);

            // When & Then
            assertThat(suspendedStudents()).isEqualTo(2L);
        }

        @Test
        @DisplayName("WashingBan이 부과되면 해당 호실 사용자를 모두 세고, 해제되면 제외한다")
        void it_follows_washing_ban_lifecycle() {
            // Given
            persistUser("20240001", "401");
            persistUser("20240002", "401");
            final WashingBan washingBan = persistWashingBan("401");
            assertThat(suspendedStudents()).isEqualTo(2L);

            // When
            entityManager.remove(washingBan);
            entityManager.flush();

            // Then
            assertThat(suspendedStudents()).isZero();
        }
    }

    @Nested
    @DisplayName("여러 제한이 겹치면")
    class Describe_overlapping_restrictions {

        @Test
        @DisplayName("사용자 기준 합집합으로 중복 없이 센다")
        void it_counts_distinct_users() {
            // Given
            final User cooldownInBlockedRoom = persistUser("20240001", "301");
            persistUser("20240002", "301");
            final User cooldownOnly = persistUser("20240003", "302");
            persistUser("20240004", "303");
            penaltyRedisUtil.applyBlockOrThrow("301");
            persistWashingBan("301");
            penaltyRedisUtil.applyCooldown(cooldownInBlockedRoom.getId(), MachineType.WASHER);
            penaltyRedisUtil.applyCooldown(cooldownOnly.getId(), MachineType.DRYER);

            // When & Then
            assertThat(suspendedStudents()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("keyspace 만료 이벤트가 유실되어 인덱스에만 ID가 남으면")
    class Describe_stale_keyspace_index {

        @Test
        @DisplayName("예약 생성 판정처럼 제한이 없는 것으로 보고 집계하지 않는다")
        void it_ignores_stale_index_members() {
            // Given
            final User user = persistUser("20240001", "301");
            persistUser("20240002", "302");
            final String cooldownId = user.getId() + ":" + MachineType.WASHER.name();
            stringRedisTemplate.opsForSet().add(CooldownEntity.KEYSPACE, cooldownId);
            stringRedisTemplate.opsForSet().add(CancellationBlockEntity.KEYSPACE, "302");

            // When & Then
            assertThat(penaltyRedisUtil.checkCooldown(user.getId(), MachineType.WASHER))
                    .isEqualTo(RestrictionStatus.NONE);
            assertThat(penaltyRedisUtil.checkBlock("302")).isEqualTo(RestrictionStatus.NONE);
            assertThat(suspendedStudents()).isZero();
        }
    }

    @Nested
    @DisplayName("활성 제한이 없으면")
    class Describe_no_active_restriction {

        @Test
        @DisplayName("최근 취소 시각만 있는 사용자는 세지 않는다")
        void it_ignores_recent_cancellation_time_only() {
            // Given
            final User user = persistUser("20240001", "301");
            user.updateLastCancellationTime();
            entityManager.flush();

            // When & Then
            assertThat(suspendedStudents()).isZero();
        }
    }
}
