package team.washer.server.v2.global.thirdparty.feign.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import feign.Request;
import feign.Response;
import team.washer.server.v2.global.common.logging.SensitiveLogSanitizer;

@DisplayName("Feign 오류 디코더 테스트")
class FeignErrorDecoderTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(FeignErrorDecoder.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("요청 및 응답의 인증정보를 로그에 기록하지 않는다")
    void doesNotLogRequestOrResponseSecrets() {
        // Given
        final var request = Request.create(Request.HttpMethod.POST,
                "https://discord.example/api/v10/webhooks/123456/webhook-secret?code=authorization-code",
                Map.of("Authorization",
                        List.of("Basic basic-secret"),
                        "Cookie",
                        List.of("refresh_token=cookie-secret")),
                "grant_type=authorization_code&refresh_token=refresh-secret&access_token=access-secret"
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8,
                null);
        final var response = Response.builder().request(request).status(400).reason("Bad Request")
                .body("response-token=response-secret", StandardCharsets.UTF_8).build();

        // When
        new FeignErrorDecoder().decode("OAuthClient#exchangeToken", response);

        // Then
        final String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("OAuthClient#exchangeToken").contains("status=400").doesNotContain("basic-secret")
                .doesNotContain("authorization-code").doesNotContain("refresh-secret").doesNotContain("access-secret")
                .doesNotContain("cookie-secret").doesNotContain("response-secret").doesNotContain("webhook-secret")
                .doesNotContain("?code=");
    }

    @Test
    @DisplayName("endpoint에서 안전한 경로만 유지한다")
    void keepsOnlySafeEndpointParts() {
        // Given / When / Then
        assertThat(SensitiveLogSanitizer
                .sanitizeEndpoint("https://smartthings.example:8443/oauth?client_secret=secret#token"))
                .isEqualTo("https://smartthings.example:8443/oauth");
        assertThat(SensitiveLogSanitizer
                .sanitizeEndpoint("https://discord.example/api/v10/webhooks/123456/webhook-secret"))
                .isEqualTo("https://discord.example/api/v10/webhooks/123456/[REDACTED]");
        assertThat(SensitiveLogSanitizer.sanitizeEndpoint("/oauth?access_token=secret#fragment")).isEqualTo("/oauth");
        assertThat(SensitiveLogSanitizer.sanitizeEndpoint("https://smartthings.example/oauth?client_secret=secret"))
                .isEqualTo("https://smartthings.example/oauth");
        assertThat(SensitiveLogSanitizer.sanitizeEndpoint(null)).isEqualTo("unknown");
    }
}
