package team.washer.server.v2.domain.smartthings.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SmartThings 액세스 토큰 조회 응답 DTO
 */
@Schema(description = "SmartThings 액세스 토큰 조회 응답")
public record SmartThingsAccessTokenResDto(
        @Schema(description = "SmartThings 액세스 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...") String accessToken,

        @Schema(description = "토큰 만료 시각") LocalDateTime expiresAt) {
}
