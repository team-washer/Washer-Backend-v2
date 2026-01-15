package team.washer.server.v2.domain.notification.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NotificationType {
    COMPLETION("완료 알림", "{machineName}의 세탁이 완료되었습니다. 빠른 시간 내에 수거해 주시기 바랍니다."), MALFUNCTION("이상 알림",
            "{machineName} 기기에 이상이 감지되었습니다. 빠른 시간 내에 확인해 주시기 바랍니다."), WARNING("경고 알림",
                    "{machineName}의 기기에서 불편신고가 들어왔습니다. 벌점 1점이 부과됩니다.\n\n신고 사유: {reason}");

    private final String description;
    private final String messageTemplate;
}
