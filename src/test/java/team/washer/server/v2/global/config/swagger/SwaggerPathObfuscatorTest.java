package team.washer.server.v2.global.config.swagger;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("SwaggerPathObfuscator 클래스의")
class SwaggerPathObfuscatorTest {

    private final SwaggerPathObfuscator swaggerPathObfuscator = new SwaggerPathObfuscator();

    @Nested
    @DisplayName("postProcessEnvironment 메서드는")
    class Describe_postProcessEnvironment {

        @Nested
        @DisplayName("prod 프로파일이 활성화된 경우")
        class Context_with_prod_profile {

            @Test
            @DisplayName("springdoc 경로를 랜덤 시크릿이 포함된 경로로 난독화해야 한다")
            void it_obfuscates_swagger_paths() {
                // Given
                var environment = new MockEnvironment();
                environment.setActiveProfiles("prod");

                // When
                swaggerPathObfuscator.postProcessEnvironment(environment, new SpringApplication());

                // Then
                var apiDocsPath = environment.getProperty("springdoc.api-docs.path");
                var swaggerUiPath = environment.getProperty("springdoc.swagger-ui.path");
                assertThat(apiDocsPath).matches("/[0-9a-f]{8}/v3/api-docs");
                assertThat(swaggerUiPath).matches("/[0-9a-f]{8}/swagger-ui\\.html");
            }
        }

        @Nested
        @DisplayName("prod 프로파일이 아닌 경우")
        class Context_without_prod_profile {

            @Test
            @DisplayName("springdoc 경로를 변경하지 않아야 한다")
            void it_does_not_change_paths() {
                // Given
                var environment = new MockEnvironment();
                environment.setActiveProfiles("local");

                // When
                swaggerPathObfuscator.postProcessEnvironment(environment, new SpringApplication());

                // Then
                assertThat(environment.getProperty("springdoc.api-docs.path")).isNull();
                assertThat(environment.getProperty("springdoc.swagger-ui.path")).isNull();
            }
        }
    }
}
