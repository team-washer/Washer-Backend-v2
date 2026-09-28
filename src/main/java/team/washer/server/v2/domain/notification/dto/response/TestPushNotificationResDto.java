package team.washer.server.v2.domain.notification.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "테스트 푸시 알림 발송 응답 DTO")
public record TestPushNotificationResDto(@Schema(description = "발송 대상 학번", example = "2108") String studentId,
        @Schema(description = "발송 대상 이름", example = "홍길동") String name,
        @Schema(description = "Firebase 메시지 ID", example = "projects/washer/messages/0:1234567890") String messageId,
        @Schema(description = "발송 시각", example = "2026-09-27T14:03:11") LocalDateTime sentAt) {
}
