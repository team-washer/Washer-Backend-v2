package team.washer.server.v2.global.config.swagger;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * prod 프로파일에서 Swagger 문서 경로를 부팅마다 랜덤 시크릿 경로로 난독화하는 EnvironmentPostProcessor
 *
 * <p>
 * 예측 가능한 고정 경로({@code /v3/api-docs}, {@code /swagger-ui.html}) 노출을 줄이기 위해,
 * springdoc 빈 생성 이전에 경로 앞에 랜덤 시크릿 세그먼트를 주입합니다. 변경된 경로는
 * {@code ObfuscatedSwaggerPathNotifier}가 기동 시 Discord로 통지합니다.
 */
public class SwaggerPathObfuscator implements EnvironmentPostProcessor {

    private static final String PROD_PROFILE = "prod";
    private static final String PROPERTY_SOURCE_NAME = "obfuscatedSwaggerPaths";
    private static final String API_DOCS_PATH_KEY = "springdoc.api-docs.path";
    private static final String SWAGGER_UI_PATH_KEY = "springdoc.swagger-ui.path";
    private static final int SECRET_BYTE_LENGTH = 4;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void postProcessEnvironment(final ConfigurableEnvironment environment, final SpringApplication application) {
        final var prodActive = environment.matchesProfiles(PROD_PROFILE);
        if (!prodActive) {
            return;
        }

        final var secret = generateSecret();
        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(API_DOCS_PATH_KEY, "/" + secret + "/v3/api-docs");
        properties.put(SWAGGER_UI_PATH_KEY, "/" + secret + "/swagger-ui.html");

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    private String generateSecret() {
        final var bytes = new byte[SECRET_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
