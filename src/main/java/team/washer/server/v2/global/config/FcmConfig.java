package team.washer.server.v2.global.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FcmConfig {

    private final FcmProperties fcmProperties;

    /**
     * FirebaseMessaging 빈 등록
     *
     * @return FirebaseMessaging 인스턴스
     * @throws IOException
     *             서비스 계정 JSON 파싱 실패 시
     */
    @Bean
    public FirebaseMessaging firebaseMessaging() throws IOException {
        if (fcmProperties.serviceAccountJson() == null || fcmProperties.serviceAccountJson().isBlank()) {
            throw new IllegalStateException(
                    "FCM service account JSON is not configured. Set FIREBASE_SERVICE_ACCOUNT_JSON environment variable.");
        }

        if (FirebaseApp.getApps().isEmpty()) {
            final var credentialStream = new ByteArrayInputStream(
                    fcmProperties.serviceAccountJson().getBytes(StandardCharsets.UTF_8));
            final var credentials = GoogleCredentials.fromStream(credentialStream);
            final var options = FirebaseOptions.builder().setCredentials(credentials).build();
            FirebaseApp.initializeApp(options);
        }

        return FirebaseMessaging.getInstance();
    }
}
