package team.washer.server.v2.domain.smartthings.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("MachineOperatingState 변환 및 판정")
class MachineOperatingStateTest {

    @Nested
    @DisplayName("from 원시 문자열 변환")
    class From {

        @Test
        @DisplayName("SmartThings가 내려주는 소문자 값을 대응하는 상수로 변환한다")
        void 소문자_값을_변환한다() {
            // Given & When & Then
            assertThat(MachineOperatingState.from("run")).isEqualTo(MachineOperatingState.RUN);
            assertThat(MachineOperatingState.from("pause")).isEqualTo(MachineOperatingState.PAUSE);
            assertThat(MachineOperatingState.from("stop")).isEqualTo(MachineOperatingState.STOP);
        }

        @Test
        @DisplayName("대소문자를 구분하지 않고 변환한다")
        void 대소문자를_구분하지_않는다() {
            // Given
            final var rawValue = "RuN";

            // When
            final var result = MachineOperatingState.from(rawValue);

            // Then
            assertThat(result).isEqualTo(MachineOperatingState.RUN);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "running", "unknownState"})
        @DisplayName("값이 없거나 알 수 없는 값이면 UNKNOWN을 반환한다")
        void 알_수_없는_값은_UNKNOWN이다(final String rawValue) {
            // Given & When
            final var result = MachineOperatingState.from(rawValue);

            // Then
            assertThat(result).isEqualTo(MachineOperatingState.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("JSON 직렬화")
    class Serialization {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("SmartThings와 동일한 원시 소문자 값으로 직렬화한다")
        void 소문자_값으로_직렬화한다() throws Exception {
            // Given & When & Then
            assertThat(objectMapper.writeValueAsString(MachineOperatingState.RUN)).isEqualTo("\"run\"");
            assertThat(objectMapper.writeValueAsString(MachineOperatingState.PAUSE)).isEqualTo("\"pause\"");
            assertThat(objectMapper.writeValueAsString(MachineOperatingState.STOP)).isEqualTo("\"stop\"");
        }

        @Test
        @DisplayName("UNKNOWN은 상태를 알 수 없다는 뜻이므로 null로 직렬화한다")
        void UNKNOWN은_null로_직렬화한다() throws Exception {
            // Given & When
            final var result = objectMapper.writeValueAsString(MachineOperatingState.UNKNOWN);

            // Then
            assertThat(result).isEqualTo("null");
        }
    }

    @Nested
    @DisplayName("isOperating 사이클 점유 판정")
    class IsOperating {

        @Test
        @DisplayName("run과 pause는 사이클을 점유 중인 상태로 판정한다")
        void run과_pause는_점유_중이다() {
            // Given & When & Then
            assertThat(MachineOperatingState.RUN.isOperating()).isTrue();
            assertThat(MachineOperatingState.PAUSE.isOperating()).isTrue();
        }

        @Test
        @DisplayName("stop과 UNKNOWN은 사이클을 점유하지 않은 상태로 판정한다")
        void stop과_UNKNOWN은_점유_중이_아니다() {
            // Given & When & Then
            assertThat(MachineOperatingState.STOP.isOperating()).isFalse();
            assertThat(MachineOperatingState.UNKNOWN.isOperating()).isFalse();
        }
    }
}
