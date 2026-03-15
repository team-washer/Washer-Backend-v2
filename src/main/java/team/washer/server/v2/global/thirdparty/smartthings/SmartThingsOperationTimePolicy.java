package team.washer.server.v2.global.thirdparty.smartthings;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsOperationEnvironment;

/**
 * SmartThings 기기 제어 운영 시간 정책
 *
 * <p>
 * 운영 시간 제한 옵션({@code third-party.smartthings.operation-schedule.enabled})이 활성화된
 * 경우, 아래 허용 시간대에만 기기 제어 작업을 수행합니다.
 *
 * <ul>
 * <li>일요일: 00:00:00 ~ 19:00:00 (경계 포함)</li>
 * <li>평일(월~금): 08:45:00 ~ 20:00:00 (경계 포함)</li>
 * <li>토요일: 하루 종일</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SmartThingsOperationTimePolicy {

    private final SmartThingsOperationEnvironment environment;

    /**
     * SmartThings 기기 제어 작업이 현재 시각 기준으로 허용되는지 확인합니다.
     *
     * @return 작업이 허용되면 {@code true}, 그렇지 않으면 {@code false}
     */
    public boolean isOperationAllowed() {
        if (!environment.enabled()) {
            return true;
        }

        var now = LocalTime.now();
        return switch (LocalDate.now().getDayOfWeek()) {
            case SATURDAY -> true;
            case SUNDAY -> !now.isAfter(LocalTime.of(19, 0));
            default -> !now.isBefore(LocalTime.of(8, 45)) && !now.isAfter(LocalTime.of(20, 0));
        };
    }
}
