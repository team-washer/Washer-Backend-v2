package team.washer.server.v2.global.common.response.wrapper;

import java.util.Arrays;
import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import team.washer.server.v2.global.common.response.data.response.CommonApiResDto;

@RestControllerAdvice
public class ApiResponseWrapper implements ResponseBodyAdvice<Object> {

    private static final String[] NOT_WRAPPING_URL = {"/api-docs/**"};

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {

        if (isNotWrappingURL(request.getURI().getPath())) {
            return body;
        }

        if (body instanceof CommonApiResDto<?>) {
            return byPassResponse(body, response);
        }

        if (body instanceof Map) {
            Map<String, Object> bodyMap = (Map<String, Object>) body;
            CommonApiResDto<Object> errorResponse = exceptionResponse(response, bodyMap);
            if (errorResponse != null)
                return errorResponse;
        }

        if (body == null) {
            response.setStatusCode(HttpStatus.NO_CONTENT);
            return null;
        }

        CommonApiResDto<Object> commonApiResponse = new CommonApiResDto<>(HttpStatus.OK, HttpStatus.OK.value(), "OK",
                body);

        response.setStatusCode(HttpStatus.OK);
        return commonApiResponse;
    }

    private static Object byPassResponse(Object body, ServerHttpResponse response) {
        CommonApiResDto<?> commonApiMessageResponse = (CommonApiResDto<?>) body;
        response.setStatusCode(commonApiMessageResponse.getStatus());
        return body;
    }

    private static CommonApiResDto<Object> exceptionResponse(ServerHttpResponse response, Map<String, Object> bodyMap) {
        if (bodyMap.containsKey("status")) {
            int statusCode = (int) bodyMap.get("status");
            if (statusCode >= 400 && statusCode < 600) {
                HttpStatus status = HttpStatus.valueOf(statusCode);
                CommonApiResDto<Object> errorResponse = CommonApiResDto.error(status.getReasonPhrase(), status);
                response.setStatusCode(HttpStatusCode.valueOf(statusCode));
                return errorResponse;
            }
        }
        return null;
    }

    private boolean isNotWrappingURL(String requestURI) {
        return Arrays.stream(NOT_WRAPPING_URL).anyMatch(pattern -> matcher.match(pattern, requestURI));
    }
}
