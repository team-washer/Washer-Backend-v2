package team.washer.server.v2.global.common.error.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OpenAPI에 표시할 공통 오류 응답 구조입니다. 실제 런타임 응답은 SDK의 {@code CommonApiResponse}가
 * 생성합니다.
 */
@Schema(name = "CommonErrorResponse", description = "서버 공통 오류 응답")
public record CommonErrorResponseResDto(@Schema(description = "HTTP 상태 이름", example = "BAD_REQUEST") String status,
        @Schema(description = "HTTP 상태 코드", example = "400") Integer code,
        @Schema(description = "사용자에게 표시할 오류 메시지", example = "입력값이 올바르지 않습니다.") String message,
        @Schema(description = "오류 상세 정보") ErrorDetailResDto data) {
}
