package team.washer.server.v2.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 갱신 요청 DTO")
public record RefreshTokenReqDto(
        @NotBlank(message = "Refresh Token은 필수입니다") @Schema(description = "Refresh Token", example = "eyJhbGci...") String refreshToken) {
}
