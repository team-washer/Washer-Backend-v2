package team.washer.server.v2.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "로그인 요청 DTO")
public record TokenReqDto(
        @NotBlank(message = "인증 코드는 필수입니다") @Schema(description = "DataGSM OAuth 인증 코드", example = "abc123xyz") String authCode,
        @NotBlank(message = "리다이렉트 URI는 필수입니다") @Schema(description = "OAuth 리다이렉트 URI", example = "https://example.com/callback") String redirectUri,
        @Pattern(regexp = "^[A-Za-z0-9._~-]{43,128}$", message = "code verifier 형식이 올바르지 않습니다") @Schema(description = "PKCE 사용 시 토큰 교환에 전달할 선택적 code verifier", example = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~", requiredMode = Schema.RequiredMode.NOT_REQUIRED) String codeVerifier) {
}
