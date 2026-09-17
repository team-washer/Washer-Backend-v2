package team.washer.server.v2.global.common.error.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 입력값 하나에 대한 오류. 입력값 자체는 개인정보가 담길 수 있어 응답에 포함하지 않는다.
 *
 * @param field
 *            오류가 발생한 필드 또는 파라미터 이름
 * @param message
 *            사용자에게 보여줄 오류 문구
 */
@Schema(description = "입력값 오류")
public record FieldErrorResDto(@Schema(description = "필드 이름", example = "name") String field,
        @Schema(description = "오류 문구", example = "이름은 필수입니다") String message) {
}
