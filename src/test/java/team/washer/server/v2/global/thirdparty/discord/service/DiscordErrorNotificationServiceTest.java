package team.washer.server.v2.global.thirdparty.discord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.time.Clock;
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
                    "context-secret",
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
                    .doesNotContain("IllegalStateException:").doesNotContain("java.lang.IllegalStateException")
                    .doesNotContain("context-secret");
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
            service.notifyError(new IllegalStateException("service failure"), "작업 실패", Map.of());
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
            service.notifyError(new IllegalStateException("first"), "scheduler", Map.of("Operation", "lifecycle"));
            service.notifyError(new IllegalStateException("second"), "scheduler", Map.of("Operation", "lifecycle"));

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
            service.notifyError(new IllegalStateException(), "scheduler", Map.of("Operation", "lifecycle"));
            service.notifyError(new IllegalStateException(), "scheduler", Map.of("Operation", "device_sync"));

            // Then
            then(client).should(org.mockito.Mockito.times(2)).sendMessage(org.mockito.ArgumentMatchers.any());
        }
    }

    private static DiscordErrorNotificationService service(final DiscordWebhookClient client) {
        return new DiscordErrorNotificationService(client,
                "test",
                "6fb9c512",
                Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC));
    }
}
