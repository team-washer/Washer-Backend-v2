package team.washer.server.v2.domain.smartthings.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SmartThingsWebhookConfirmationSupport 클래스의")
class SmartThingsWebhookConfirmationSupportTest {

    private final SmartThingsWebhookConfirmationSupport support = new SmartThingsWebhookConfirmationSupport();

    @Nested
    @DisplayName("isAllowed 메서드는")
    class Describe_is_allowed {

        @Test
        @DisplayName("SmartThings 도메인의 HTTPS URL을 허용한다")
        void it_allows_smartthings_https_url() {
            assertThat(support.isAllowed("https://api.smartthings.com/apps/app-1/confirm-registration?token=abc"))
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"http://api.smartthings.com/confirm", "https://smartthings.com.evil.example/confirm",
                "https://evilsmartthings.com/confirm", "https://user@api.smartthings.com/confirm",
                "https://api.smartthings.com:8443/confirm", "https://169.254.169.254/latest", "not a url"})
        @DisplayName("SmartThings 도메인의 HTTPS URL이 아니면 거부한다")
        void it_rejects_other_urls(final String url) {
            assertThat(support.isAllowed(url)).isFalse();
        }
    }

    @Nested
    @DisplayName("confirm 메서드는")
    class Describe_confirm {

        @Test
        @DisplayName("허용되지 않은 URL이면 호출하지 않고 예외를 발생시킨다")
        void it_throws_for_disallowed_url() {
            assertThatThrownBy(() -> support.confirm("https://169.254.169.254/latest"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
