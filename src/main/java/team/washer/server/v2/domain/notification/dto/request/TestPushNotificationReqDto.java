package team.washer.server.v2.domain.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "테스트 푸시 알림 발송 요청 DTO")
public record TestPushNotificationReqDto(
        // 생략(null·공백)은 본인 발송으로 허용하고, 값이 있으면 User.studentId와 같은 형식만 받는다.
        @Pattern(regexp = "^(\\s*|\\d{4,10})$", message = "학번은 4-10자리 숫자여야 합니다") @Schema(description = "발송 대상 학번(4-10자리 숫자). 생략하면 요청한 관리자 본인에게 발송합니다.", example = "2108") String studentId,
        @Size(max = 50, message = "제목은 50자를 초과할 수 없습니다") @Schema(description = "알림 제목. 생략하면 기본 문구를 사용합니다.", example = "테스트 알림") String title,
        @Size(max = 200, message = "본문은 200자를 초과할 수 없습니다") @Schema(description = "알림 본문. 생략하면 기본 문구를 사용합니다.", example = "푸시 알림이 정상적으로 수신되는지 확인합니다.") String body) {
}
