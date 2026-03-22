package team.washer.server.v2.global.thirdparty.smartthings.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SmartThings 운영 시간 제한 설정
 *
 * <p>
 * {@code enabled} 가 {@code true} 이면 정해진 요일/시간대에만 기기 제어가 허용됩니다.
 */
@ConfigurationProperties(prefix = "third-party.smartthings.operation-schedule")
public record SmartThingsOperationEnvironment(boolean enabled) {
}
