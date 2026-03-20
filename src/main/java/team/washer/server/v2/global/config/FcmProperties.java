package team.washer.server.v2.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("third-party.fcm")
public record FcmProperties(String serviceAccountJson) {
}
