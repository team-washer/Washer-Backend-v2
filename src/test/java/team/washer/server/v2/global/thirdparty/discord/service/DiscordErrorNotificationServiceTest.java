package team.washer.server.v2.global.thirdparty.discord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import team.washer.server.v2.global.thirdparty.discord.data.DiscordWebhookPayload;
import team.washer.server.v2.global.thirdparty.feign.client.DiscordWebhookClient;

@DisplayName("Discord 운영 오류 알림 서비스")
class DiscordErrorNotificationServiceTest {

    @Nested
    @DisplayName("안전한 이벤트 전송")
    class Describe_safeEvent {

        @Test
        @DisplayName("허용된 진단 정보만 보내고 예외 원문과 stack trace는 제외한다")
        void sendsOnlyAllowListedDiagnosticFieldsWithoutSensitiveInformation() throws Exception {
            // Given
            final DiscordWebhookClient client = mock(DiscordWebhookClient.class);
            final var service = service(client);
            final var exception = new IllegalStateException(
                    "Authorization: Basic basic-secret refresh_token=refresh-secret code=authorization-code");

            // When
            service.notifyError(exception,
                    Map.of("HTTP Method",
                            "POST",
                            "Request Path",
                            "/oauth?access_token=access-secret",
                            "Trace ID",
                            "trace-123",
                            "Authorization",
                            "Basic basic-secret",
                            "Request Body",
                            "refresh_token=refresh-secret"));

            // Then
            final var payload = ArgumentCaptor.forClass(DiscordWebhookPayload.class);
            then(client).should().sendMessage(payload.capture());

            final String json = new ObjectMapper().writeValueAsString(payload.getValue());
            assertThat(json).contains("POST").contains("trace-123").contains("6fb9c512").doesNotContain("basic-secret")
                    .doesNotContain("refresh-secret").doesNotContain("authorization-code")
                    .doesNotContain("access-secret").doesNotContain("Stack Trace")
                    .doesNotContain("IllegalStateException:").doesNotContain("java.lang.IllegalStateException");
        }

        @Test
        @DisplayName("Discord 전송 실패가 호출자에게 전파되지 않는다")
        void isolatesDiscordDeliveryFailure() {
            // Given
            final DiscordWebhookClient client = mock(DiscordWebhookClient.class);
            org.mockito.Mockito.doThrow(new IllegalStateException("webhook failure")).when(client)
                    .sendMessage(org.mockito.ArgumentMatchers.any());
            final var service = service(client);

            // When & Then
            service.notifyError(new IllegalStateException("service failure"), Map.of());
        }

        @Test
        @DisplayName("전송 실패 뒤 같은 이벤트는 다시 전송을 시도한다")
        void retriesSameEventAfterDiscordDeliveryFailure() {
            // Given
            final DiscordWebhookClient client = mock(DiscordWebhookClient.class);
            org.mockito.Mockito.doThrow(new IllegalStateException("webhook failure")).doNothing().when(client)
                    .sendMessage(org.mockito.ArgumentMatchers.any());
            final var clock = new MutableClock(Instant.parse("2026-10-08T00:00:00Z"));
            final var service = service(client, clock);

            // When
            service.notifyError(new IllegalStateException("first"), Map.of("Operation", "lifecycle"));
            service.notifyError(new IllegalStateException("second"), Map.of("Operation", "lifecycle"));
            clock.advance(Duration.ofMinutes(1));
            service.notifyError(new IllegalStateException("third"), Map.of("Operation", "lifecycle"));

            // Then
            then(client).should(org.mockito.Mockito.times(2)).sendMessage(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("중복 억제")
    class Describe_deduplication {

        @Test
        @DisplayName("같은 이벤트는 억제 시간 안에 한 번만 전송한다")
        void suppressesRepeatedEvents() {
            // Given
            final DiscordWebhookClient client = mock(DiscordWebhookClient.class);
            final var service = service(client);

            // When
            service.notifyError(new IllegalStateException("first"), Map.of("Operation", "lifecycle"));
            service.notifyError(new IllegalStateException("second"), Map.of("Operation", "lifecycle"));

            // Then
            then(client).should().sendMessage(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("서로 다른 작업 이벤트는 독립적으로 전송한다")
        void sendsDifferentEventsIndependently() {
            // Given
            final DiscordWebhookClient client = mock(DiscordWebhookClient.class);
            final var service = service(client);

            // When
            service.notifyError(new IllegalStateException(), Map.of("Operation", "lifecycle"));
            service.notifyError(new IllegalStateException(), Map.of("Operation", "device_sync"));

            // Then
            then(client).should(org.mockito.Mockito.times(2)).sendMessage(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("같은 작업의 HTTP 메서드와 기기 내부 ID가 다르면 각각 전송한다")
        void sendsEventsWithDifferentMethodOrMachineIdIndependently() {
            // Given
            final DiscordWebhookClient client = mock(DiscordWebhookClient.class);
            final var service = service(client);

            // When
            service.notifyError(new IllegalStateException(), Map.of("Operation", "machine_sync", "HTTP Method", "GET"));
            service.notifyError(new IllegalStateException(),
                    Map.of("Operation", "machine_sync", "HTTP Method", "POST"));
            service.notifyError(new IllegalStateException(), Map.of("Operation", "machine_shutdown", "Machine ID", 1L));
            service.notifyError(new IllegalStateException(), Map.of("Operation", "machine_shutdown", "Machine ID", 2L));

            // Then
            then(client).should(org.mockito.Mockito.times(4)).sendMessage(org.mockito.ArgumentMatchers.any());
        }
    }

    private static DiscordErrorNotificationService service(final DiscordWebhookClient client) {
        return service(client, Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC));
    }

    private static DiscordErrorNotificationService service(final DiscordWebhookClient client, final Clock clock) {
        return new DiscordErrorNotificationService(client, "test", "6fb9c512", clock);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(final Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(final Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
