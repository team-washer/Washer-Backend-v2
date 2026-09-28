package team.washer.server.v2.global.thirdparty.discord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import team.washer.server.v2.global.thirdparty.discord.data.DiscordWebhookPayload;
import team.washer.server.v2.global.thirdparty.feign.client.DiscordWebhookClient;

class DiscordErrorNotificationServiceTest {

    @Test
    void sendsOnlyAllowListedDiagnosticFieldsWithoutExceptionMessage() throws Exception {
        final DiscordWebhookClient client = mock(DiscordWebhookClient.class);
        final var service = new DiscordErrorNotificationService(client);
        final var exception = new IllegalStateException(
                "Authorization: Basic basic-secret refresh_token=refresh-secret code=authorization-code");

        service.notifyError(exception,
                "외부 호출 실패",
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

        final var payload = ArgumentCaptor.forClass(DiscordWebhookPayload.class);
        then(client).should().sendMessage(payload.capture());

        final String json = new ObjectMapper().writeValueAsString(payload.getValue());
        assertThat(json).contains("POST").contains("trace-123").doesNotContain("basic-secret")
                .doesNotContain("refresh-secret").doesNotContain("authorization-code").doesNotContain("access-secret")
                .doesNotContain("IllegalStateException:");
    }
}
