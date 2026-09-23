package team.washer.server.v2.global.common.error.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 오류 응답의 {@code data}에 담기는 부가 정보. 기존 {@code message}는 그대로 두고 선택적으로 추가되는 필드다.
 *
 * @param errorCode
 *            오류 유형을 나타내는 안정적인 코드
 * @param fieldErrors
 *            입력값별 오류 목록. 입력 오류가 아니면 생략
 * @param traceId
 *            서버 로그와 연결하기 위한 추적 ID
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "오류 응답 부가 정보")
public record ErrorDetailResDto(
        @Schema(description = "오류 코드", example = "VALIDATION_FAILED", requiredMode = Schema.RequiredMode.REQUIRED) String errorCode,
        @Schema(description = "입력값별 오류 목록. 입력 검증 오류가 아니면 생략될 수 있습니다.", requiredMode = Schema.RequiredMode.NOT_REQUIRED) List<FieldErrorResDto> fieldErrors,
        @Schema(description = "추적 ID. 로그 추적이 필요할 때 포함됩니다.", example = "3f2c9a1e-8b7d-4c21-9f0e-2a6b5d4c3e1f", requiredMode = Schema.RequiredMode.NOT_REQUIRED) String traceId) {
}
