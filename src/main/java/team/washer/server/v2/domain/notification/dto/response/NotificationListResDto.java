package team.washer.server.v2.domain.notification.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 목록 응답 DTO")
public record NotificationListResDto(@Schema(description = "알림 목록") List<NotificationResDto> notifications) {
}
