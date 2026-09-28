package team.washer.server.v2.global.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("민감정보 로그 sanitizer는")
class SensitiveLogSanitizerTest {

    @Nested
    @DisplayName("외부 오류 문자열을 처리할 때")
    class ExternalError {

        @Test
        @DisplayName("웹훅 토큰과 SQL 입력값을 마스킹한다")
        void masksWebhookAndSqlValues() {
            // Given
            final var value = "POST https://discord.com/api/webhooks/123456/webhook-secret "
                    + "Duplicate entry 's25004@gsm.hs.kr' for key 'user.email'";

            // When
            final var sanitized = SensitiveLogSanitizer.sanitize(value);

            // Then
            assertThat(sanitized).contains("https://discord.com/api/webhooks/123456/[REDACTED]")
                    .contains("Duplicate entry '[REDACTED]'").doesNotContain("webhook-secret")
                    .doesNotContain("s25004@gsm.hs.kr");
        }

        @Test
        @DisplayName("공백 비인용 값과 이중 escape JSON을 마스킹한다")
        void masksUnquotedAndEscapedJsonValues() {
            // Given
            final var value = "password=correct horse battery next=value {\\\"refresh_token\\\":\\\"refresh-secret\\\"}";

            // When
            final var sanitized = SensitiveLogSanitizer.sanitize(value);

            // Then
            assertThat(sanitized).contains("password=[REDACTED] next=value")
                    .contains("refresh_token\\\":\\\"[REDACTED]").doesNotContain("correct horse battery")
                    .doesNotContain("refresh-secret");
        }

        @Test
        @DisplayName("민감정보가 아닌 escape 문자열은 원문을 유지한다")
        void preservesUnrelatedEscapedJson() {
            final var value = "payload={\\\"title\\\":\\\"서버 오류\\\",\\\"count\\\":3}";

            assertThat(SensitiveLogSanitizer.sanitize(value)).isEqualTo(value);
        }

        @Test
        @DisplayName("버전이 포함된 Discord 웹훅 URL의 토큰도 마스킹한다")
        void masksVersionedWebhook() {
            final var value = "POST https://discord.com/api/v10/webhooks/123456/webhook-secret";

            assertThat(SensitiveLogSanitizer.sanitize(value))
                    .isEqualTo("POST https://discord.com/api/v10/webhooks/123456/[REDACTED]");
        }

        @Test
        @DisplayName("다른 식별자에 포함된 token 부분은 마스킹 키로 취급하지 않는다")
        void preservesNonSensitiveIdentifier() {
            final var value = "mytoken=diagnostic-value";

            assertThat(SensitiveLogSanitizer.sanitize(value)).isEqualTo(value);
        }
    }
}
