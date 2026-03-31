package team.washer.server.v2.domain.notification.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import team.washer.server.v2.domain.notification.enums.NotificationType;

@Schema(description = "알림 응답 DTO")
public record NotificationResDto(@Schema(description = "알림 ID", example = "1") Long id,
        @Schema(description = "알림 유형", example = "COMPLETION") NotificationType type,
        @Schema(description = "알림 메시지", example = "WASHER-3F-L1의 세탁이 완료되었습니다.") String message,
        @Schema(description = "알림 생성 시간", example = "2026-03-30T14:30:00") LocalDateTime createdAt) {
}
