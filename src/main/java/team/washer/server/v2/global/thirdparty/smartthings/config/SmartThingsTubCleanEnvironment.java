package team.washer.server.v2.global.thirdparty.smartthings.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * SmartThings 무세제 통세척 자동 실행 설정.
 *
 * @param enabled
 *            자동 실행 활성화 여부
 * @param cycle
 *            기기 모델에 맞는 SmartThings 세탁 코스 코드
 */
@ConfigurationProperties(prefix = "third-party.smartthings.tub-clean")
public record SmartThingsTubCleanEnvironment(boolean enabled, String cycle) {

    public SmartThingsTubCleanEnvironment {
        cycle = cycle == null ? null : cycle.trim();
    }

    public boolean hasCycle() {
        return StringUtils.hasText(cycle);
    }
}
