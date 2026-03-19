package team.washer.server.v2.domain.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record FcmTokenReqDto(
        @NotBlank(message = "FCM 토큰은 필수입니다") @Schema(description = "FCM 토큰", example = "dGVzdC10b2tlbi1leGFtcGxl") String token) {
}
