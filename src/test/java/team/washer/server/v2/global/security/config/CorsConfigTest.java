package team.washer.server.v2.global.security.config;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import team.washer.server.v2.global.security.data.CorsEnvironment;

@DisplayName("CorsConfig 클래스의")
class CorsConfigTest {

    @Nested
    @DisplayName("configure 메서드는")
    class Describe_configure {

        @Test
        @DisplayName("기본 설정에서 모든 Origin의 POST preflight 요청을 허용한다")
        void it_allows_post_preflight_with_default_origin() {
            // 준비
            var corsEnvironment = new CorsEnvironment();
            var corsConfigurationSource = (UrlBasedCorsConfigurationSource) new CorsConfig(corsEnvironment).configure();
            var request = new MockHttpServletRequest(HttpMethod.OPTIONS.name(), "/api/v2/admin/machines");
            request.addHeader("Origin", "http://localhost:3000");

            // 실행
            var corsConfiguration = corsConfigurationSource.getCorsConfiguration(request);

            // 검증
            assertThat(corsConfiguration).isNotNull();
            assertThat(corsConfiguration.getAllowCredentials()).isFalse();
            assertThat(corsConfiguration.checkOrigin("http://localhost:3000")).isEqualTo("*");
            assertThat(corsConfiguration.checkHttpMethod(HttpMethod.POST)).contains(HttpMethod.POST);
            assertThat(corsConfiguration.checkHeaders(List.of("authorization", "content-type")))
                    .containsExactly("authorization", "content-type");
        }

        @Test
        @DisplayName("Origin 패턴이 설정되면 명시된 패턴을 사용한다")
        void it_uses_allowed_origin_patterns() {
            // 준비
            var corsEnvironment = new CorsEnvironment();
            corsEnvironment.setAllowedOriginPatterns(List.of("http://localhost:*"));
            corsEnvironment.setAllowCredentials(true);
            var corsConfigurationSource = (UrlBasedCorsConfigurationSource) new CorsConfig(corsEnvironment).configure();
            var request = new MockHttpServletRequest(HttpMethod.OPTIONS.name(), "/api/v2/admin/machines");
            request.addHeader("Origin", "http://localhost:3000");

            // 실행
            var corsConfiguration = corsConfigurationSource.getCorsConfiguration(request);

            // 검증
            assertThat(corsConfiguration).isNotNull();
            assertThat(corsConfiguration.getAllowCredentials()).isTrue();
            assertThat(corsConfiguration.getAllowedOriginPatterns()).containsExactly("http://localhost:*");
            assertThat(corsConfiguration.checkOrigin("http://localhost:3000")).isEqualTo("http://localhost:3000");
        }

        @Test
        @DisplayName("Origin 설정이 null이어도 예외가 발생하지 않는다")
        void it_handles_null_origin_properties() {
            // 준비
            var corsEnvironment = new CorsEnvironment();
            corsEnvironment.setAllowedOrigins(null);
            corsEnvironment.setAllowedOriginPatterns(null);

            // 실행 및 검증
            assertThatCode(() -> new CorsConfig(corsEnvironment).configure()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("인증 정보 허용 시 모든 Origin을 allowed-origins로 설정하면 예외가 발생한다")
        void it_rejects_all_origins_with_credentials() {
            // 준비
            var corsEnvironment = new CorsEnvironment();
            corsEnvironment.setAllowCredentials(true);
            corsEnvironment.setAllowedOrigins(List.of("*"));

            // 실행 및 검증
            assertThatThrownBy(() -> new CorsConfig(corsEnvironment).configure())
                    .isInstanceOf(IllegalStateException.class).hasMessage("인증 정보를 허용할 때는 모든 Origin을 허용할 수 없습니다.");
        }
    }
}
