package team.washer.server.v2.global.thirdparty.feign.error;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.util.StreamUtils;

import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();

        if (status >= 400) {
            logFeignError(methodKey, response);
            return createExpectedException(status);
        }
        return FeignException.errorStatus(methodKey, response);
    }

    private void logFeignError(String methodKey, Response response) {
        String url = response.request().url();
        String httpMethod = response.request().httpMethod().name();
        int status = response.status();
        String reason = response.reason();
        String errorBody = extractErrorBody(response);
        Map<String, Collection<String>> responseHeaders = response.headers();
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("Feign 클라이언트 오류 발생 - ");
        logMessage.append("메서드: ").append(methodKey).append(", ");
        logMessage.append("HTTP: ").append(httpMethod).append(" ").append(url).append(", ");
        logMessage.append("상태: ").append(status).append(" ").append(reason).append(", ");
        logMessage.append("응답 헤더: ").append(responseHeaders).append(", ");
        logMessage.append("응답 본문: ").append(errorBody);
        try {
            Map<String, Collection<String>> requestHeaders = response.request().headers();
            logMessage.append(", 요청 헤더: ").append(requestHeaders);

            if (response.request().body() != null) {
                String requestBody = extractRequestBody(response);
                logMessage.append(", 요청 본문: ").append(requestBody);
            }
        } catch (Exception e) {
            logMessage.append(", 요청 정보 로깅 실패: ").append(e.getMessage());
        }

        log.error(logMessage.toString());
    }

    private String extractRequestBody(Response response) {
        byte[] body = response.request().body();
        if (body == null) {
            return "요청 본문 없음";
        }

        String contentType = getContentType(response.request().headers());
        if (isTextBasedContentType(contentType)) {
            return new String(body, StandardCharsets.UTF_8);
        } else {
            return "[바이너리 데이터, " + body.length + " bytes]";
        }
    }

    private String extractErrorBody(Response response) {
        try {
            if (response.body() != null) {
                return StreamUtils.copyToString(response.body().asInputStream(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return "응답 본문 읽기 실패: " + e.getMessage();
        }
        return "응답 본문 없음";
    }

    private String getContentType(Map<String, Collection<String>> headers) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, Collection<String>> entry : headers.entrySet()) {
            if ("content-type".equalsIgnoreCase(entry.getKey()) && entry.getValue() != null
                    && !entry.getValue().isEmpty()) {
                return entry.getValue().iterator().next();
            }
        }
        return null;
    }

    private boolean isTextBasedContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lowerContentType = contentType.toLowerCase();
        return lowerContentType.startsWith("text/") || lowerContentType.contains("application/json")
                || lowerContentType.contains("application/xml")
                || lowerContentType.contains("application/x-www-form-urlencoded")
                || lowerContentType.contains("application/problem+json")
                || lowerContentType.contains("application/graphql");
    }

    private ExpectedException createExpectedException(int status) {
        return switch (status) {
            case 400 -> new ExpectedException("잘못된 요청입니다.", HttpStatus.BAD_REQUEST);
            case 401 -> new ExpectedException("인증이 필요합니다.", HttpStatus.UNAUTHORIZED);
            case 403 -> new ExpectedException("접근이 거부되었습니다.", HttpStatus.FORBIDDEN);
            case 404 -> new ExpectedException("요청하신 리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
            case 429 -> new ExpectedException("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.TOO_MANY_REQUESTS);
            case 500 -> new ExpectedException("외부 서비스 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
            case 502 -> new ExpectedException("게이트웨이 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY);
            case 503 ->
                new ExpectedException("서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.SERVICE_UNAVAILABLE);
            default -> new ExpectedException("외부 요청 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        };
    }
}
