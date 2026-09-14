package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.google.firebase.messaging.FirebaseMessaging;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.dto.request.AdminCreateReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.request.CreateReservationReqDto;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;

@Testcontainers
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
        "spring.docker.compose.enabled=false", "reservation.disable-time-restriction=true",
        "jwt.secret=test-secret-for-reservation-concurrency-test"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("예약 생성 동시성 통합 테스트")
class ReservationCreationConcurrencyTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0").withDatabaseName("washer_test")
            .withUsername("washer").withPassword("password");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configureDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private CreateReservationService createReservationService;

    @Autowired
    private AdminCreateReservationService adminCreateReservationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @MockitoBean
    private PenaltyRedisUtil penaltyRedisUtil;

    @MockitoBean
    private FirebaseMessaging firebaseMessaging;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp(@Autowired final PlatformTransactionManager transactionManager) {
        transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            reservationRepository.deleteAllInBatch();
            machineRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
        });

        given(penaltyRedisUtil.isBlocked(anyString())).willReturn(false);
        given(penaltyRedisUtil.isInCooldown(anyLong(), any(MachineType.class))).willReturn(false);
    }

    @Nested
    @DisplayName("서로 다른 기기에 동시에 예약할 때")
    class DifferentMachineConcurrency {

        @Test
        @DisplayName("같은 사용자의 두 예약은 하나만 성공한다")
        void 같은_사용자의_두_예약은_하나만_성공한다() {
            final var data = transactionTemplate.execute(status -> {
                final var user = saveUser("2101", "301", UserRole.USER);
                final var washer1 = saveMachine("washer-1", MachineType.WASHER, Position.LEFT, 1);
                final var washer2 = saveMachine("washer-2", MachineType.WASHER, Position.RIGHT, 2);
                return new SameUserData(user.getId(), washer1.getId(), washer2.getId());
            });

            final var results = runConcurrently(() -> reserveAsUser(data.userId(), data.firstMachineId()),
                    () -> reserveAsUser(data.userId(), data.secondMachineId()));

            assertOneSuccessAndOneExpectedFailure(results, "1인 1예약");
            assertReservationCount(1);
        }

        @Test
        @DisplayName("같은 호실의 동일 유형 두 예약은 하나만 성공한다")
        void 같은_호실의_동일_유형_두_예약은_하나만_성공한다() {
            final var data = transactionTemplate.execute(status -> {
                final var firstUser = saveUser("2102", "302", UserRole.USER);
                final var secondUser = saveUser("2103", "302", UserRole.USER);
                final var washer1 = saveMachine("washer-3", MachineType.WASHER, Position.LEFT, 3);
                final var washer2 = saveMachine("washer-4", MachineType.WASHER, Position.RIGHT, 4);
                return new TwoUserData(firstUser.getId(), secondUser.getId(), washer1.getId(), washer2.getId());
            });

            final var results = runConcurrently(() -> reserveAsUser(data.firstUserId(), data.firstMachineId()),
                    () -> reserveAsUser(data.secondUserId(), data.secondMachineId()));

            assertOneSuccessAndOneExpectedFailure(results, "동일 유형");
            assertReservationCount(1);
        }

        @Test
        @DisplayName("같은 호실의 세탁기와 건조기 예약은 모두 성공한다")
        void 같은_호실의_세탁기와_건조기_예약은_모두_성공한다() {
            final var data = transactionTemplate.execute(status -> {
                final var firstUser = saveUser("2104", "303", UserRole.USER);
                final var secondUser = saveUser("2105", "303", UserRole.USER);
                final var washer = saveMachine("washer-5", MachineType.WASHER, Position.LEFT, 5);
                final var dryer = saveMachine("dryer-1", MachineType.DRYER, Position.RIGHT, 1);
                return new TwoUserData(firstUser.getId(), secondUser.getId(), washer.getId(), dryer.getId());
            });

            final var results = runConcurrently(() -> reserveAsUser(data.firstUserId(), data.firstMachineId()),
                    () -> reserveAsUser(data.secondUserId(), data.secondMachineId()));

            assertThat(results).allMatch(ReservationAttemptResult::isSuccess);
            assertReservationCount(2);
        }

        @Test
        @DisplayName("다른 호실의 동일 유형 예약은 모두 성공한다")
        void 다른_호실의_동일_유형_예약은_모두_성공한다() {
            final var data = transactionTemplate.execute(status -> {
                final var firstUser = saveUser("2106", "304", UserRole.USER);
                final var secondUser = saveUser("2107", "305", UserRole.USER);
                final var washer1 = saveMachine("washer-6", MachineType.WASHER, Position.LEFT, 6);
                final var washer2 = saveMachine("washer-7", MachineType.WASHER, Position.RIGHT, 7);
                return new TwoUserData(firstUser.getId(), secondUser.getId(), washer1.getId(), washer2.getId());
            });

            final var results = runConcurrently(() -> reserveAsUser(data.firstUserId(), data.firstMachineId()),
                    () -> reserveAsUser(data.secondUserId(), data.secondMachineId()));

            assertThat(results).allMatch(ReservationAttemptResult::isSuccess);
            assertReservationCount(2);
        }

        @Test
        @DisplayName("일반 예약과 관리자 대리 예약이 같은 사용자를 대상으로 겹치면 하나만 성공한다")
        void 일반_예약과_관리자_대리_예약이_같은_사용자를_대상으로_겹치면_하나만_성공한다() {
            final var data = transactionTemplate.execute(status -> {
                final var targetUser = saveUser("2108", "306", UserRole.USER);
                final var adminUser = saveUser("2109", "401", UserRole.ADMIN);
                final var washer1 = saveMachine("washer-8", MachineType.WASHER, Position.LEFT, 8);
                final var washer2 = saveMachine("washer-9", MachineType.WASHER, Position.RIGHT, 9);
                return new AdminOverlapData(targetUser.getId(), adminUser.getId(), washer1.getId(), washer2.getId());
            });

            final var results = runConcurrently(() -> reserveAsUser(data.targetUserId(), data.firstMachineId()),
                    () -> reserveAsAdmin(data.adminUserId(), data.targetUserId(), data.secondMachineId()));

            assertOneSuccessAndOneExpectedFailure(results, "1인 1예약");
            assertReservationCount(1);
        }
    }

    private ReservationAttemptResult reserveAsUser(final Long userId, final Long machineId) {
        authenticate(userId);
        try {
            createReservationService.execute(new CreateReservationReqDto(machineId));
            return ReservationAttemptResult.succeeded();
        } catch (Throwable throwable) {
            return ReservationAttemptResult.failure(throwable);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private ReservationAttemptResult reserveAsAdmin(final Long adminUserId,
            final Long targetUserId,
            final Long machineId) {
        authenticate(adminUserId);
        try {
            adminCreateReservationService.execute(new AdminCreateReservationReqDto(targetUserId, machineId));
            return ReservationAttemptResult.succeeded();
        } catch (Throwable throwable) {
            return ReservationAttemptResult.failure(throwable);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private List<ReservationAttemptResult> runConcurrently(final Callable<ReservationAttemptResult> firstTask,
            final Callable<ReservationAttemptResult> secondTask) {
        final var executor = Executors.newFixedThreadPool(2);
        final var ready = new CountDownLatch(2);
        final var start = new CountDownLatch(1);

        try {
            final Future<ReservationAttemptResult> first = executor.submit(readyAndRun(firstTask, ready, start));
            final Future<ReservationAttemptResult> second = executor.submit(readyAndRun(secondTask, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } catch (Exception e) {
            throw new AssertionError("동시 예약 실행 중 예외가 발생했습니다.", e);
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<ReservationAttemptResult> readyAndRun(final Callable<ReservationAttemptResult> task,
            final CountDownLatch ready,
            final CountDownLatch start) {
        return () -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return task.call();
        };
    }

    private void authenticate(final Long userId) {
        final var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private User saveUser(final String studentId, final String roomNumber, final UserRole role) {
        return userRepository.save(User.builder().name("테스트사용자" + studentId).studentId(studentId).roomNumber(roomNumber)
                .grade(1).floor(3).role(role).build());
    }

    private Machine saveMachine(final String deviceId,
            final MachineType type,
            final Position position,
            final int number) {
        return machineRepository.save(Machine.builder().name(type.getCode() + "-3F-" + position.getCode() + number)
                .type(type).deviceId(deviceId).floor(3).position(position).number(number).build());
    }

    private void assertOneSuccessAndOneExpectedFailure(final List<ReservationAttemptResult> results,
            final String message) {
        assertThat(results).filteredOn(ReservationAttemptResult::isSuccess).hasSize(1);
        assertThat(results).filteredOn(result -> !result.isSuccess()).singleElement().satisfies(result -> {
            assertThat(result.throwable()).isInstanceOf(ExpectedException.class);
            assertThat(result.throwable()).hasMessageContaining(message);
        });
    }

    private void assertReservationCount(final long expected) {
        final var count = transactionTemplate.execute(status -> reservationRepository.count());
        assertThat(count).isEqualTo(expected);
    }

    record SameUserData(Long userId, Long firstMachineId, Long secondMachineId) {
    }

    record TwoUserData(Long firstUserId, Long secondUserId, Long firstMachineId, Long secondMachineId) {
    }

    record AdminOverlapData(Long targetUserId, Long adminUserId, Long firstMachineId, Long secondMachineId) {
    }

    record ReservationAttemptResult(boolean success, Throwable throwable) {

        boolean isSuccess() {
            return success;
        }

        static ReservationAttemptResult succeeded() {
            return new ReservationAttemptResult(true, null);
        }

        static ReservationAttemptResult failure(final Throwable throwable) {
            return new ReservationAttemptResult(false, throwable);
        }
    }

}
