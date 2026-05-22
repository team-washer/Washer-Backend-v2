package team.washer.server.v2.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 상태 응답")
public record TokenStatusResDto(@Schema(description = "Refresh Token 유효 여부") boolean valid) {
}
