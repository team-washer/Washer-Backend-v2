package team.washer.server.v2.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fcm")
public record FcmProperties(String serviceAccountJson) {
}
