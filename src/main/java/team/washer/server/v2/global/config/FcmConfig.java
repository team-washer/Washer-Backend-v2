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
                    "FCM service account JSON is not configured. Set THIRD_PARTY_FCM_SERVICE_ACCOUNT_JSON environment variable.");
        }

        if (FirebaseApp.getApps().isEmpty()) {
            final var credentialStream = new ByteArrayInputStream(
                    fcmProperties.serviceAccountJson().getBytes(StandardCharsets.UTF_8));
            final var credentials = GoogleCredentials.fromStream(credentialStream);
            // SDK 기본값은 제한 시간이 없어 Firebase 지연 시 전송 스레드가 무기한 묶일 수 있다
            final var options = FirebaseOptions.builder().setCredentials(credentials)
                    .setConnectTimeout(Math.toIntExact(fcmProperties.connectTimeout().toMillis()))
                    .setReadTimeout(Math.toIntExact(fcmProperties.readTimeout().toMillis())).build();
            FirebaseApp.initializeApp(options);
        }

        return FirebaseMessaging.getInstance();
    }
}
