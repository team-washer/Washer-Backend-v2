package team.washer.server.v2.global.thirdparty.feign.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import feign.Request;
import feign.Response;

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
    void doesNotLogRequestOrResponseSecrets() {
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

        new FeignErrorDecoder().decode("OAuthClient#exchangeToken", response);

        final String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("OAuthClient#exchangeToken").contains("status=400").doesNotContain("basic-secret")
                .doesNotContain("authorization-code").doesNotContain("refresh-secret").doesNotContain("access-secret")
                .doesNotContain("cookie-secret").doesNotContain("response-secret").doesNotContain("webhook-secret")
                .doesNotContain("?code=");
    }

    @Test
    void keepsOnlySafeEndpointParts() {
        assertThat(FeignErrorDecoder.safeEndpoint("https://smartthings.example/oauth?client_secret=secret"))
                .isEqualTo("https://smartthings.example/oauth");
        assertThat(FeignErrorDecoder.safeEndpoint(null)).isEqualTo("unknown");
    }
}
