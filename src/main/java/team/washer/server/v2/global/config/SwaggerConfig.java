package team.washer.server.v2.global.config;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import team.washer.server.v2.global.common.response.data.response.CommonApiResDto;

@OpenAPIDefinition(info = @Info(title = "Washer API", description = "기숙사 세탁기 예약 관리 시스템", version = "v2"))
@Configuration
public class SwaggerConfig {

    @Bean
    public OperationCustomizer operationCustomizer() {
        return (operation, handlerMethod) -> {
            final var returnType = handlerMethod.getMethod().getReturnType();
            final var isAlreadyWrapped = CommonApiResDto.class.isAssignableFrom(returnType);
            if (!isAlreadyWrapped) {
                wrapResponseWithCommonApiResDto(operation);
            }

            return operation;
        };
    }

    private void wrapResponseWithCommonApiResDto(final Operation operation) {
        operation.getResponses().forEach((statusCode, response) -> {
            if (statusCode.startsWith("2")) {
                if (response == null || response.getContent() == null) {
                    return;
                }

                response.getContent().forEach((mediaTypeKey, mediaType) -> {
                    final var originalSchema = mediaType.getSchema();
                    mediaType.schema(createWrappedSchema(originalSchema));
                });
            }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Schema<?> createWrappedSchema(final Schema<?> originalSchema) {
        final var wrapperSchema = new Schema<>();
        wrapperSchema.addProperty("status", new Schema<String>().type("string").example("OK"));
        wrapperSchema.addProperty("code", new Schema<Integer>().type("integer").format("int32").example(200));
        wrapperSchema.addProperty("message", new Schema<String>().type("string").example("정상 처리되었습니다."));
        if (originalSchema != null) {
            wrapperSchema.addProperty("data", originalSchema);
        }
        return wrapperSchema;
    }
}
