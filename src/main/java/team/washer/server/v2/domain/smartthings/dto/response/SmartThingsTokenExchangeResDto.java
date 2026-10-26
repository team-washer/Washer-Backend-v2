package team.washer.server.v2.domain.smartthings.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SmartThings 토큰 교환 응답")
public record SmartThingsTokenExchangeResDto(
        @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...") @JsonProperty("access_token") String accessToken,

        @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...") @JsonProperty("refresh_token") String refreshToken,

        @Schema(description = "토큰 타입", example = "Bearer") @JsonProperty("token_type") String tokenType,

        @Schema(description = "만료 시간(초)", example = "3600") @JsonProperty("expires_in") Integer expiresIn,

        @Schema(description = "스코프", example = "r:devices:* w:devices:*") @JsonProperty("scope") String scope,

        @Schema(description = "설치 앱 ID. webhook 구독 관리에 사용하며 갱신 응답에는 없을 수 있다", example = "5f1c2b3a-0000-0000-0000-000000000000") @JsonProperty("installed_app_id") String installedAppId,

        @Schema(description = "API 접근 등급. 공식 문서에 의미가 정의되지 않아 관측용으로만 기록한다", example = "0") @JsonProperty("access_tier") Integer accessTier,

        @Schema(description = "사용량 제한 적용 여부. 공식 문서에 의미가 정의되지 않아 관측용으로만 기록한다", example = "false") @JsonProperty("quota_enforced") Boolean quotaEnforced) {
}
