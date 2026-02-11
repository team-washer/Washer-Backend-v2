package team.washer.server.v2.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 응답")
public record TokenResDto(@Schema(description = "Access Token", example = "eyJhbGci...") String accessToken,
        @Schema(description = "Access Token 만료 시간(초)", example = "3600") Long expiresIn,
        @Schema(description = "Refresh Token", example = "eyJhbGci...") String refreshToken) {
}
