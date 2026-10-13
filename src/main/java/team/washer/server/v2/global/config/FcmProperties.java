package team.washer.server.v2.global.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * FCM 연동 설정.
 *
 * @param serviceAccountJson
 *            Firebase 서비스 계정 JSON
 * @param connectTimeout
 *            Firebase HTTP 연결 제한 시간
 * @param readTimeout
 *            Firebase HTTP 응답 대기 제한 시간
 */
@ConfigurationProperties("third-party.fcm")
public record FcmProperties(String serviceAccountJson, @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout) {
}
