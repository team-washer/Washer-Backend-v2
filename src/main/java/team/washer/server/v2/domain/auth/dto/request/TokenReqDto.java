package team.washer.server.v2.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "로그인 요청 DTO")
public record TokenReqDto(
        @NotBlank(message = "인증 코드는 필수입니다") @Schema(description = "DataGSM OAuth 인증 코드", example = "abc123xyz") String authCode,
        @NotBlank(message = "리다이렉트 URI는 필수입니다") @Schema(description = "OAuth 리다이렉트 URI", example = "https://example.com/callback") String redirectUri,
        @Pattern(regexp = CODE_VERIFIER_REGEX, message = CODE_VERIFIER_FORMAT_MESSAGE) @Schema(description = "PKCE S256 authorize 요청에서 code_challenge를 함께 보낸 경우에만 전달하는 code verifier. 기존 앱은 생략할 수 있습니다.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) String codeVerifier) {
    public static final String CODE_VERIFIER_REGEX = "^[A-Za-z0-9._~-]{43,128}$";
    public static final String CODE_VERIFIER_FORMAT_MESSAGE = "code verifier 형식이 올바르지 않습니다";
}
