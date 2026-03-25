package team.washer.server.v2.domain.notification.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import team.washer.server.v2.domain.machine.enums.MachineType;

@Getter
@AllArgsConstructor
public enum NotificationType {
    COMPLETION("완료 알림", "{machineName}의 {action} 완료되었습니다. 빠른 시간 내에 수거해 주시기 바랍니다."), MALFUNCTION("이상 알림",
            "{machineName} 기기에 이상이 감지되었습니다. 빠른 시간 내에 확인해 주시기 바랍니다."), WARNING("경고 알림",
                    "{machineName}의 기기에서 불편신고가 들어왔습니다. 벌점 1점이 부과됩니다.\n\n신고 사유: {reason}"), INTERRUPTION("중단 알림",
                            "{machineName}의 {action} 예기치 않게 중단되었습니다. 예약이 패널티 없이 취소되었습니다."), AUTO_CANCELLED(
                                    "예약 자동 취소 알림", "{machineName}의 예약이 시간 초과로 자동 취소되었습니다. 패널티가 부과되었습니다.");

    private final String description;
    private final String messageTemplate;

    public String formatMessage(String machineName) {
        return messageTemplate.replace("{machineName}", machineName);
    }

    public String formatMessage(String machineName, MachineType machineType) {
        return messageTemplate.replace("{machineName}", machineName).replace("{action}", machineType.getActionNoun());
    }

    public String formatMessage(String machineName, String reason) {
        return messageTemplate.replace("{machineName}", machineName).replace("{reason}", reason);
    }
}
