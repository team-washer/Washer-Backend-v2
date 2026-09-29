package team.washer.server.v2.global.thirdparty.smartthings.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import team.washer.server.v2.domain.machine.entity.Machine;

@DisplayName("SmartThings 호출 제한 설정은")
class SmartThingsFeignConfigTest {

    @Test
    @DisplayName("전원 차단 선점 만료 시간보다 짧다")
    void staysWithinShutdownClaimTimeout() {
        // Given & When
        final var maximumRequestLifetime = SmartThingsFeignConfig.MAX_REQUEST_LIFETIME_MILLIS;

        // Then
        assertThat(maximumRequestLifetime).isLessThan(Machine.SHUTDOWN_CLAIM_TIMEOUT.toMillis());
        assertThat(maximumRequestLifetime).isLessThan(Machine.SHUTDOWN_COMMAND_RECOVERY_TIMEOUT.toMillis());
    }
}
