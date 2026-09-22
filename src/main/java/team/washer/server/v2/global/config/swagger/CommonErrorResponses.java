package team.washer.server.v2.global.config.swagger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import team.washer.server.v2.global.common.error.dto.response.CommonErrorResponseResDto;

/**
 * Controller에서 공통으로 반환하는 오류 상태와 응답 wrapper를 OpenAPI에 표시합니다.
 *
 * <p>
 * 409와 503은 엔드포인트별 원인이 달라 공통 응답에 포함하지 않습니다. 해당 상태를 반환하는 API는 작업별
 * {@link ApiResponse}로 의미를 명시해야 합니다.
 * </p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "요청 검증 또는 형식 오류", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class))),
        @ApiResponse(responseCode = "403", description = "권한 부족", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class))),
        @ApiResponse(responseCode = "404", description = "요청한 리소스 없음", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class))),
        @ApiResponse(responseCode = "405", description = "지원하지 않는 HTTP 메서드", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class))),
        @ApiResponse(responseCode = "413", description = "요청 본문 크기 초과", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class))),
        @ApiResponse(responseCode = "415", description = "지원하지 않는 요청 형식", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class))),
        @ApiResponse(responseCode = "500", description = "처리하지 못한 서버 오류", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class)))})
public @interface CommonErrorResponses {
}
