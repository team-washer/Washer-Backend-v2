package team.washer.server.v2.domain.machine.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.global.util.DateTimeUtil;

@DisplayName("Machine 클래스의")
class MachineTest {

    private Machine createMachine() {
        return Machine.builder().name("W-2F-L1").type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
    }

    @Nested
    @DisplayName("통세척 상태 변경 메서드는")
    class Describe_cleaning_state {

        @Test
        @DisplayName("통세척을 시작하면 통세척 중 상태로 변경해야 한다")
        void it_marks_machine_as_cleaning() {
            var machine = createMachine();

            machine.markAsCleaning();

            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.CLEANING);
        }

        @Test
        @DisplayName("정상 기기의 통세척이 끝나면 사용 가능 상태로 변경해야 한다")
        void it_finishes_cleaning_on_normal_machine() {
            var machine = createMachine();
            machine.markAsCleaning();

            machine.finishCleaning();

            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
        }

        @Test
        @DisplayName("예약 해제는 통세척 중 상태를 변경하지 않아야 한다")
        void it_keeps_cleaning_state_when_reservation_is_released() {
            var machine = createMachine();
            machine.markAsCleaning();

            machine.releaseIfHeld();

            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.CLEANING);
        }

        @Test
        @DisplayName("고장난 기기의 통세척 점유를 해제하면 사용 불가 상태를 유지해야 한다")
        void it_keeps_malfunction_machine_unavailable_after_cleaning() {
            var machine = createMachine();
            machine.markAsCleaning();
            ReflectionTestUtils.setField(machine, "status", MachineStatus.MALFUNCTION);

            machine.finishCleaning();

            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("releaseIfHeld 메서드는")
    class Describe_releaseIfHeld {

        @Test
        @DisplayName("예약됨 상태의 기기를 사용 가능 상태로 해제해야 한다")
        void it_releases_reserved_machine() {
            // Given
            final var machine = createMachine();
            machine.markAsReserved();

            // When
            machine.releaseIfHeld();

            // Then
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
        }

        @Test
        @DisplayName("사용 중 상태의 기기를 사용 가능 상태로 해제해야 한다")
        void it_releases_in_use_machine() {
            // Given
            final var machine = createMachine();
            machine.markAsInUse();

            // When
            machine.releaseIfHeld();

            // Then
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
        }

        @Test
        @DisplayName("고장 처리된 기기의 상태를 그대로 유지해야 한다")
        void it_keeps_malfunction_machine_unavailable() {
            // Given
            final var machine = createMachine();
            machine.markAsMalfunction();

            // When
            machine.releaseIfHeld();

            // Then
            assertThat(machine.getStatus()).isEqualTo(MachineStatus.MALFUNCTION);
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.UNAVAILABLE);
        }

        @Test
        @DisplayName("고장난 기기가 예약됨 상태로 어긋나 있어도 사용 불가로 되돌려야 한다")
        void it_restores_unavailable_when_malfunction_machine_is_reserved() {
            // Given
            final var machine = createMachine();
            machine.markAsMalfunction();
            machine.markAsReserved();

            // When
            machine.releaseIfHeld();

            // Then
            assertThat(machine.getStatus()).isEqualTo(MachineStatus.MALFUNCTION);
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.UNAVAILABLE);
        }

        @Test
        @DisplayName("고장난 기기가 사용 중 상태로 어긋나 있어도 사용 불가로 되돌려야 한다")
        void it_restores_unavailable_when_malfunction_machine_is_in_use() {
            // Given
            final var machine = createMachine();
            machine.markAsMalfunction();
            machine.markAsInUse();

            // When
            machine.releaseIfHeld();

            // Then
            assertThat(machine.getStatus()).isEqualTo(MachineStatus.MALFUNCTION);
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.UNAVAILABLE);
        }

        @Test
        @DisplayName("고장난 기기가 사용 가능 상태로 어긋나 있어도 사용 불가로 되돌려야 한다")
        void it_restores_unavailable_when_malfunction_machine_is_available() {
            // Given
            final var machine = createMachine();
            machine.markAsMalfunction();
            machine.markAsAvailable();

            // When
            machine.releaseIfHeld();

            // Then
            assertThat(machine.getStatus()).isEqualTo(MachineStatus.MALFUNCTION);
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.UNAVAILABLE);
        }

        @Test
        @DisplayName("사용 불가로 처리된 정상 기기의 상태를 그대로 유지해야 한다")
        void it_keeps_unavailable_normal_machine_unavailable() {
            // Given
            final var machine = createMachine();
            machine.markAsUnavailable();

            // When
            machine.releaseIfHeld();

            // Then
            assertThat(machine.getStatus()).isEqualTo(MachineStatus.NORMAL);
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.UNAVAILABLE);
        }

        @Test
        @DisplayName("이미 사용 가능한 기기의 상태를 그대로 유지해야 한다")
        void it_keeps_available_machine_available() {
            // Given
            final var machine = createMachine();

            // When
            machine.releaseIfHeld();

            // Then
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
        }
    }

    @Nested
    @DisplayName("전원 차단 선점 메서드는")
    class Describe_shutdown_claim {

        @Test
        @DisplayName("이전 작업의 토큰으로 신규 예약 이후 선점을 해제하지 않는다")
        void it_does_not_release_a_new_claim_with_a_stale_token() {
            // Given
            var machine = createMachine();
            var staleToken = machine.claimShutdown().orElseThrow();
            ReflectionTestUtils.setField(machine,
                    "shutdownClaimedAt",
                    DateTimeUtil.nowInKorea().minus(Machine.SHUTDOWN_CLAIM_TIMEOUT).minusSeconds(1));

            // When
            machine.recoverExpiredShutdownClaim();
            var currentToken = machine.claimShutdown().orElseThrow();

            // Then
            assertThat(currentToken).isNotEqualTo(staleToken);
            assertThat(machine.releaseShutdown(staleToken)).isFalse();
            assertThat(machine.isShutdownInProgress()).isTrue();
            assertThat(machine.releaseShutdown(currentToken)).isTrue();
            assertThat(machine.isShutdownInProgress()).isFalse();
        }

        @Test
        @DisplayName("외부 명령이 시작된 claim은 lease가 만료되어도 신규 예약에 기기를 내주지 않는다")
        void it_keeps_machine_fenced_after_command_started() {
            // Given
            var machine = createMachine();
            var claimToken = machine.claimShutdown().orElseThrow();
            assertThat(machine.beginShutdownCommand(claimToken)).isTrue();
            var expiredAt = DateTimeUtil.nowInKorea().minus(Machine.SHUTDOWN_CLAIM_TIMEOUT).minusSeconds(1);
            ReflectionTestUtils.setField(machine, "shutdownClaimedAt", expiredAt);
            ReflectionTestUtils.setField(machine,
                    "shutdownCommandStartedAt",
                    DateTimeUtil.nowInKorea().minus(Machine.SHUTDOWN_COMMAND_RECOVERY_TIMEOUT).minusSeconds(1));

            // When
            machine.recoverExpiredShutdownClaim();

            // Then
            assertThat(machine.hasActiveShutdownClaim()).isTrue();
            assertThat(machine.isAvailable()).isFalse();
            assertThat(machine.isShutdownCommandRecoveryReady()).isTrue();
            assertThat(machine.isShutdownInProgress()).isTrue();
        }

        @Test
        @DisplayName("복구 claim을 재점유해도 미확정 명령의 보호 상태를 유지한다")
        void it_restarts_command_once_with_reclaimed_claim() {
            // Given
            var machine = createMachine();
            machine.claimShutdown();
            ReflectionTestUtils.setField(machine,
                    "shutdownClaimedAt",
                    DateTimeUtil.nowInKorea().minus(Machine.SHUTDOWN_CLAIM_TIMEOUT).minusSeconds(1));
            ReflectionTestUtils.setField(machine,
                    "shutdownCommandStartedAt",
                    DateTimeUtil.nowInKorea().minus(Machine.SHUTDOWN_COMMAND_RECOVERY_TIMEOUT).minusSeconds(1));

            // When
            assertThat(machine.reclaimShutdownCommand()).isTrue();
            var reclaimedToken = machine.getShutdownClaimToken();

            // Then
            assertThat(machine.getShutdownCommandStartedAt()).isNotNull();
            assertThat(machine.isShutdownCommandRecoveryReady()).isFalse();
            assertThat(machine.hasActiveShutdownClaim()).isTrue();
            assertThat(machine.isAvailable()).isFalse();
            assertThat(machine.beginShutdownCommand(reclaimedToken)).isTrue();
            assertThat(machine.beginShutdownCommand(reclaimedToken)).isFalse();
            assertThat(machine.isShutdownCommandRecoveryReady()).isFalse();
        }
    }
}
