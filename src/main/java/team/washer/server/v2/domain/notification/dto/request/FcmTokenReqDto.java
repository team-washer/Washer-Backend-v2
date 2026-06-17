package team.washer.server.v2.domain.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import team.washer.server.v2.global.common.constants.NotificationConstants;

public record FcmTokenReqDto(
        @NotBlank(message = "FCM 토큰은 필수입니다") @Size(max = NotificationConstants.FCM_TOKEN_MAX_LENGTH, message = "FCM 토큰은 4096자를 초과할 수 없습니다") @Schema(description = "FCM 토큰", example = "dGVzdC10b2tlbi1leGFtcGxl") String token) {
}
