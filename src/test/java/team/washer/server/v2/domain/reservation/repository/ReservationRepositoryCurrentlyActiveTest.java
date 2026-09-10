package team.washer.server.v2.domain.reservation.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.config.JpaAuditingConfig;
import team.washer.server.v2.global.config.QueryDslConfig;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * 만료 판정 규칙이 엔티티({@link Reservation#isCurrentlyActive()})와 QueryDSL 조건 양쪽에 각각
 * 구현되어 있으므로, 실제 DB에 대해 두 판정이 항상 같은 답을 내는지 검증합니다.
 *
 * <p>
 * 모킹 없이 실제 쿼리를 실행하는 유일한 지점이므로, 한쪽 규칙만 바뀌면 여기서 실패해야 합니다.
 */
@DataJpaTest
@Import({QueryDslConfig.class, JpaAuditingConfig.class})
@TestPropertySource(properties = {"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.docker.compose.enabled=false"})
@DisplayName("ReservationRepository 활성 예약 조회 쿼리 테스트")
class ReservationRepositoryCurrentlyActiveTest {

    private static final String ROOM_NUMBER = "301";
    private static final int TIMEOUT_MINUTES = ReservationStatus.RESERVED.getTimeoutMinutes();

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User owner;
    private Machine washer;

    private Reservation freshReserved;
    private Reservation justBeforeTimeoutReserved;
    private Reservation exactlyAtTimeoutReserved;
    private Reservation longExpiredReserved;
    private Reservation futureReserved;
    private Reservation longRunning;
    private Reservation completed;
    private Reservation cancelled;

    @BeforeEach
    void setUp() {
        final LocalDateTime now = DateTimeUtil.nowInKorea();

        owner = persist(User.builder().name("김세탁").studentId("2101").roomNumber(ROOM_NUMBER).grade(1).floor(3).build());
        washer = persist(Machine.builder().name("W3L1").type(MachineType.WASHER).deviceId("device-w3l1").floor(3)
                .position(Position.LEFT).number(1).build());

        // 경계 직전: 타임아웃 30초 전이므로 활성
        freshReserved = persistReservation(ReservationStatus.RESERVED, now.minusMinutes(1));
        justBeforeTimeoutReserved = persistReservation(ReservationStatus.RESERVED,
                now.minusMinutes(TIMEOUT_MINUTES).plusSeconds(30));
        // 경계 동률: 경과 시간이 정확히 타임아웃과 같으므로 만료
        exactlyAtTimeoutReserved = persistReservation(ReservationStatus.RESERVED, now.minusMinutes(TIMEOUT_MINUTES));
        longExpiredReserved = persistReservation(ReservationStatus.RESERVED, now.minusMinutes(TIMEOUT_MINUTES + 30));
        // 미래 예약 시각도 만료로 취급되지 않아야 한다
        futureReserved = persistReservation(ReservationStatus.RESERVED, now.plusMinutes(1));
        // RUNNING은 타임아웃 대상이 아니므로 아무리 오래되어도 활성
        longRunning = persistReservation(ReservationStatus.RUNNING, now.minusHours(3));
        completed = persistReservation(ReservationStatus.COMPLETED, now.minusMinutes(1));
        cancelled = persistReservation(ReservationStatus.CANCELLED, now.minusMinutes(1));

        entityManager.flush();
        entityManager.clear();
    }

    @Nested
    @DisplayName("findCurrentlyActiveByUser 메서드는")
    class FindCurrentlyActiveByUser {

        @Test
        @DisplayName("만료되지 않은 활성 예약만 반환한다")
        void 만료되지_않은_활성_예약만_반환한다() {
            assertActiveIdsAre(() -> reservationRepository.findCurrentlyActiveByUser(owner));
        }
    }

    @Nested
    @DisplayName("findCurrentlyActiveByMachine 메서드는")
    class FindCurrentlyActiveByMachine {

        @Test
        @DisplayName("만료되지 않은 활성 예약만 반환한다")
        void 만료되지_않은_활성_예약만_반환한다() {
            assertActiveIdsAre(() -> reservationRepository.findCurrentlyActiveByMachine(washer));
        }
    }

    @Nested
    @DisplayName("findCurrentlyActiveByRoomNumber 메서드는")
    class FindCurrentlyActiveByRoomNumber {

        @Test
        @DisplayName("만료되지 않은 활성 예약만 반환한다")
        void 만료되지_않은_활성_예약만_반환한다() {
            assertActiveIdsAre(() -> reservationRepository.findCurrentlyActiveByRoomNumber(ROOM_NUMBER));
        }
    }

    @Nested
    @DisplayName("쿼리 조건과 엔티티 판정은")
    class QueryAndEntityRule {

        @Test
        @DisplayName("동일한 예약 집합에 대해 같은 결론을 낸다")
        void 동일한_예약_집합에_대해_같은_결론을_낸다() {
            final List<Long> byEntityRule = reservationRepository.findAll().stream()
                    .filter(Reservation::isCurrentlyActive).map(Reservation::getId).sorted().toList();
            final List<Long> byQueryRule = reservationRepository.findCurrentlyActiveByUser(owner).stream()
                    .map(Reservation::getId).sorted().toList();

            assertThat(byQueryRule).isEqualTo(byEntityRule);
        }
    }

    private void assertActiveIdsAre(final Supplier<List<Reservation>> query) {
        assertThat(query.get()).extracting(Reservation::getId).containsExactlyInAnyOrder(freshReserved.getId(),
                justBeforeTimeoutReserved.getId(),
                futureReserved.getId(),
                longRunning.getId());

        assertThat(query.get()).extracting(Reservation::getId).doesNotContain(exactlyAtTimeoutReserved.getId(),
                longExpiredReserved.getId(),
                completed.getId(),
                cancelled.getId());
    }

    private Reservation persistReservation(final ReservationStatus status, final LocalDateTime reservedAt) {
        return persist(Reservation.builder().user(owner).machine(washer).status(status).reservedAt(reservedAt).build());
    }

    private <T> T persist(final T entity) {
        return entityManager.persist(entity);
    }
}
