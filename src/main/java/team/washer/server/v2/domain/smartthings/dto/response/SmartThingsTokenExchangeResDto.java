package team.washer.server.v2.domain.smartthings.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SmartThings 토큰 교환 응답")
public record SmartThingsTokenExchangeResDto(
        @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...") @JsonProperty("access_token") String accessToken,

        @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...") @JsonProperty("refresh_token") String refreshToken,

        @Schema(description = "토큰 타입", example = "Bearer") @JsonProperty("token_type") String tokenType,

        @Schema(description = "만료 시간(초)", example = "3600") @JsonProperty("expires_in") Integer expiresIn,

        @Schema(description = "스코프", example = "r:devices:* w:devices:*") @JsonProperty("scope") String scope) {
}
