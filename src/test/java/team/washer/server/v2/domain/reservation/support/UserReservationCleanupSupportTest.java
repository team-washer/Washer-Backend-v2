package team.washer.server.v2.domain.reservation.support;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

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
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.config.JpaAuditingConfig;
import team.washer.server.v2.global.config.QueryDslConfig;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * 사용자 삭제 정리가 실제 잠금 쿼리와 자동 flush 아래에서 다른 사용자의 예약을 보존하는지 검증합니다.
 *
 * <p>
 * 삭제 대상 사용자의 만료 예약이 기기 락을 기다리는 동안 다른 사용자의 예약이 같은 기기에 커밋된 상태를 재현합니다.
 */
@DataJpaTest
@Import({QueryDslConfig.class, JpaAuditingConfig.class, UserReservationCleanupSupport.class})
@TestPropertySource(properties = {"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.docker.compose.enabled=false"})
@DisplayName("UserReservationCleanupSupport 예약 정리 통합 테스트")
class UserReservationCleanupSupportTest {

    private static final int TIMEOUT_MINUTES = ReservationStatus.RESERVED.getTimeoutMinutes();

    @Autowired
    private UserReservationCleanupSupport userReservationCleanupSupport;

    @Autowired
    private TestEntityManager entityManager;

    private User deletedUser;
    private User otherUser;
    private Machine washer;
    private Reservation expiredReservation;

    @BeforeEach
    void setUp() {
        deletedUser = entityManager
                .persist(User.builder().name("김세탁").studentId("2101").roomNumber("301").grade(1).floor(3).build());
        otherUser = entityManager
                .persist(User.builder().name("이건조").studentId("2102").roomNumber("302").grade(1).floor(3).build());
        washer = entityManager.persist(Machine.builder().name("W3L1").type(MachineType.WASHER).deviceId("device-w3l1")
                .floor(3).position(Position.LEFT).number(1).availability(MachineAvailability.RESERVED).build());
        expiredReservation = persistReservation(deletedUser,
                DateTimeUtil.nowInKorea().minusMinutes(TIMEOUT_MINUTES + 30));
    }

    @Nested
    @DisplayName("같은 기기에 다른 사용자의 활성 예약이 있으면")
    class WithOtherUsersReservation {

        @Test
        @DisplayName("만료 예약만 취소하고 기기와 다른 사용자의 예약은 RESERVED로 유지한다")
        void 기기와_다른_사용자의_예약을_유지한다() {
            // Given
            final Reservation otherReservation = persistReservation(otherUser, DateTimeUtil.nowInKorea());
            flushAndClear();

            // When
            cleanUp();

            // Then
            assertThat(find(Reservation.class, expiredReservation.getId()).getStatus())
                    .isEqualTo(ReservationStatus.CANCELLED);
            assertThat(find(Reservation.class, otherReservation.getId()).getStatus())
                    .isEqualTo(ReservationStatus.RESERVED);
            assertThat(find(Machine.class, washer.getId()).getAvailability()).isEqualTo(MachineAvailability.RESERVED);
        }
    }

    @Nested
    @DisplayName("같은 기기에 다른 사용자의 활성 예약이 없으면")
    class WithoutOtherUsersReservation {

        @Test
        @DisplayName("만료 예약을 취소하고 기기를 AVAILABLE로 해제한다")
        void 기기를_해제한다() {
            // Given
            flushAndClear();

            // When
            cleanUp();

            // Then
            assertThat(find(Reservation.class, expiredReservation.getId()).getStatus())
                    .isEqualTo(ReservationStatus.CANCELLED);
            assertThat(find(Machine.class, washer.getId()).getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
        }
    }

    private void cleanUp() {
        final User user = find(User.class, deletedUser.getId());
        userReservationCleanupSupport
                .cancelAndReleaseMachines(userReservationCleanupSupport.lockActiveReservations(user));
        flushAndClear();
    }

    private Reservation persistReservation(final User user, final LocalDateTime reservedAt) {
        return entityManager.persist(Reservation.builder().user(user).machine(washer).status(ReservationStatus.RESERVED)
                .reservedAt(reservedAt).build());
    }

    private <T> T find(final Class<T> type, final Long id) {
        return entityManager.find(type, id);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
