package team.washer.server.v2.global.config.swagger;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

/**
 * 공통 오류 응답 중 전역 예외 처리기에서 발생할 수 있는 상태를 OpenAPI operation에 추가합니다.
 */
@Component
public class CommonErrorResponsesCustomizer implements OperationCustomizer {

    private static final String COMMON_ERROR_SCHEMA_REF = "#/components/schemas/CommonErrorResponseResDto";

    @Override
    public Operation customize(final Operation operation, final HandlerMethod handlerMethod) {
        if (!hasCommonErrorResponses(handlerMethod)) {
            return operation;
        }

        final var responses = operation.getResponses() == null ? new ApiResponses() : operation.getResponses();
        responses.putIfAbsent("409", commonErrorResponse("동시성 충돌 또는 현재 상태와 충돌하는 요청"));
        responses.putIfAbsent("503", commonErrorResponse("Redis 또는 외부 서비스 일시 장애"));
        operation.setResponses(responses);
        return operation;
    }

    private boolean hasCommonErrorResponses(final HandlerMethod handlerMethod) {
        return AnnotationUtils.findAnnotation(handlerMethod.getMethod(), CommonErrorResponses.class) != null
                || AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), CommonErrorResponses.class) != null;
    }

    private ApiResponse commonErrorResponse(final String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref(COMMON_ERROR_SCHEMA_REF))));
    }
}
