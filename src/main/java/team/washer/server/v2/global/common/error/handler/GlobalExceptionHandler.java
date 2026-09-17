package team.washer.server.v2.global.common.error.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import feign.RetryableException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.themoment.sdk.response.CommonApiResponse;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.dto.response.ErrorDetailResDto;
import team.washer.server.v2.global.common.error.dto.response.FieldErrorResDto;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
import team.washer.server.v2.global.common.trace.TraceIdFilter;
import team.washer.server.v2.global.thirdparty.discord.service.DiscordErrorNotificationService;

/**
 * 모든 오류를 {@link CommonApiResponse} 형식으로 변환하는 전역 예외 처리기.
 *
 * <p>
 * 기존 {@code message} 계약은 유지하고, 오류 코드·입력값 오류·추적 ID는 {@code data}에
 * {@link ErrorDetailResDto}로 담는다. 응답 문구에는 예외 메시지, 내부 클래스명, SQL 등 내부 정보를 노출하지
 * 않는다.
 *
 * <p>
 * 운영 Discord 알림은 예상하지 못한 500 오류와 중요 외부 시스템(Redis·SmartThings) 장애에만 보낸다. 외부 장애를
 * 감싼 5xx {@link ExpectedException}도 여기에 포함된다. 사용자 요청 오류(4xx)는 알림 대상이 아니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String REQUIRED_FIELD_MESSAGE = "필수 값입니다.";
    private static final String INVALID_FORMAT_MESSAGE = "형식이 올바르지 않습니다.";

    @Autowired(required = false)
    private DiscordErrorNotificationService discordErrorNotificationService;

    /**
     * 서비스가 의도적으로 던진 예외. 5xx는 {@code FeignErrorDecoder}와 SmartThings 호출부가 외부 시스템 장애를
     * 감싼 경우이므로 사용자 요청 오류와 달리 운영 알림을 보낸다.
     */
    @ExceptionHandler(ExpectedException.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> expectedException(ExpectedException ex,
            HttpServletRequest request) {
        if (ex.getStatusCode().is5xxServerError()) {
            log.error("expected server exception status={} path={} message={}",
                    ex.getStatusCode(),
                    request.getRequestURI(),
                    ex.getMessage(),
                    ex);
            notifyOperators(ex, request);
        } else {
            log.warn("expected exception status={} message={}", ex.getStatusCode(), ex.getMessage());
            log.trace("expected exception detail", ex);
        }
        return error(ex.getStatusCode(), ex.getStatusCode().name(), ex.getMessage(), null);
    }

    /**
     * 서비스가 {@link ErrorCode}를 지정해 던진 예외. 5xx는 Redis 등 외부 시스템 장애로 판정을 거부한 경우이므로 운영
     * 알림을 보낸다.
     */
    @ExceptionHandler(ErrorCodeException.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> errorCodeException(ErrorCodeException ex,
            HttpServletRequest request) {
        final ErrorCode errorCode = ex.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("error code server exception errorCode={} path={}", errorCode, request.getRequestURI(), ex);
            notifyOperators(ex, request);
            return error(errorCode);
        }
        return clientError(errorCode, ex, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> methodArgumentNotValidException(
            MethodArgumentNotValidException ex) {
        final var fieldErrors = new ArrayList<FieldErrorResDto>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.add(new FieldErrorResDto(error.getField(), error.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors().forEach(
                error -> fieldErrors.add(new FieldErrorResDto(error.getObjectName(), error.getDefaultMessage())));
        return validationFailed(fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> handlerMethodValidationException(
            HandlerMethodValidationException ex) {
        final var fieldErrors = new ArrayList<FieldErrorResDto>();
        ex.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error -> {
            final var field = error instanceof FieldError fieldError
                    ? fieldError.getField()
                    : result.getMethodParameter().getParameterName();
            fieldErrors.add(new FieldErrorResDto(field, error.getDefaultMessage()));
        }));
        return validationFailed(fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> constraintViolationException(
            ConstraintViolationException ex) {
        final var fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorResDto(leafName(violation), violation.getMessage())).toList();
        return validationFailed(fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> httpMessageNotReadableException(
            HttpMessageNotReadableException ex) {
        return clientError(ErrorCode.INVALID_REQUEST_BODY, ex, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> methodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex) {
        return clientError(ErrorCode.TYPE_MISMATCH,
                ex,
                List.of(new FieldErrorResDto(ex.getName(), INVALID_FORMAT_MESSAGE)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> missingServletRequestParameterException(
            MissingServletRequestParameterException ex) {
        return missingParameter(ex.getParameterName(), ex);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> missingRequestHeaderException(
            MissingRequestHeaderException ex) {
        return missingParameter(ex.getHeaderName(), ex);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> missingServletRequestPartException(
            MissingServletRequestPartException ex) {
        return missingParameter(ex.getRequestPartName(), ex);
    }

    /**
     * 인증 실패. 컨트롤러에서 던져진 경우와 Spring Security 진입점에서 위임된 경우를 모두 처리한다.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> authenticationException(AuthenticationException ex) {
        return clientError(ErrorCode.UNAUTHORIZED, ex, null);
    }

    /**
     * 권한 부족. 컨트롤러에서 던져진 경우와 Spring Security 접근 거부 처리기에서 위임된 경우를 모두 처리한다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> accessDeniedException(AccessDeniedException ex) {
        return clientError(ErrorCode.FORBIDDEN, ex, null);
    }

    /**
     * 없는 경로. 현재 Spring은 정적 리소스 핸들러가 모든 경로를 받으므로 {@link NoResourceFoundException}이
     * 던져지고, 정적 리소스 매핑을 끈 경우에만 {@link NoHandlerFoundException}이 던져진다.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> notFoundException(Exception ex) {
        return clientError(ErrorCode.NOT_FOUND, ex, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> httpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex,
            HttpServletResponse response) {
        final var supportedMethods = ex.getSupportedHttpMethods();
        if (supportedMethods != null && !supportedMethods.isEmpty()) {
            response.setHeader(HttpHeaders.ALLOW,
                    supportedMethods.stream().map(Object::toString).collect(Collectors.joining(", ")));
        }
        return clientError(ErrorCode.METHOD_NOT_ALLOWED, ex, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> httpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException ex) {
        return clientError(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ex, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> maxUploadSizeExceededException(
            MaxUploadSizeExceededException ex) {
        return clientError(ErrorCode.PAYLOAD_TOO_LARGE, ex, null);
    }

    /**
     * 낙관적·비관적 락 충돌. 같은 자원에 대한 동시 요청이 겹친 경우로, 사용자가 다시 시도하면 해소될 수 있다.
     */
    @ExceptionHandler({ConcurrencyFailureException.class, OptimisticLockException.class})
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> concurrencyFailureException(Exception ex) {
        return clientError(ErrorCode.CONFLICT, ex, null);
    }

    /**
     * Redis 연결 실패와 SmartThings 등 외부 API의 네트워크 장애. 일시적인 장애이므로 503으로 응답하고 운영 알림을 보낸다.
     */
    @ExceptionHandler({RedisConnectionFailureException.class, RetryableException.class})
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> externalServiceUnavailableException(Exception ex,
            HttpServletRequest request) {
        log.error("external service unavailable exception={} path={}",
                ex.getClass().getSimpleName(),
                request.getRequestURI(),
                ex);
        notifyOperators(ex, request);
        return error(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonApiResponse<ErrorDetailResDto>> unexpectedException(Exception ex,
            HttpServletRequest request) {
        // 전용 처리기가 없는 Spring MVC 요청 오류(406 등)는 자체 상태 코드를 따르고 운영 알림 대상에서 제외한다
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            final var status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            logClientError(status.name(), ex);
            return error(status, status.name(), ErrorCode.CLIENT_ERROR.getMessage(), null);
        }

        log.error("unexpected exception exception={} path={}",
                ex.getClass().getSimpleName(),
                request.getRequestURI(),
                ex);
        notifyOperators(ex, request);
        return error(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<CommonApiResponse<ErrorDetailResDto>> validationFailed(List<FieldErrorResDto> fieldErrors) {
        log.warn("request rejected errorCode={} fields={}",
                ErrorCode.VALIDATION_FAILED,
                fieldErrors.stream().map(FieldErrorResDto::field).toList());
        // 사용자에게 바로 보여줄 수 있도록 첫 번째 입력값 오류 문구를 message로 사용한다
        final var message = fieldErrors.stream().map(FieldErrorResDto::message).filter(text -> text != null).findFirst()
                .orElse(ErrorCode.VALIDATION_FAILED.getMessage());
        return error(ErrorCode.VALIDATION_FAILED.getStatus(), ErrorCode.VALIDATION_FAILED.name(), message, fieldErrors);
    }

    private ResponseEntity<CommonApiResponse<ErrorDetailResDto>> missingParameter(String name, Exception ex) {
        return clientError(ErrorCode.MISSING_PARAMETER,
                ex,
                List.of(new FieldErrorResDto(name, REQUIRED_FIELD_MESSAGE)));
    }

    private ResponseEntity<CommonApiResponse<ErrorDetailResDto>> clientError(ErrorCode errorCode,
            Exception ex,
            List<FieldErrorResDto> fieldErrors) {
        logClientError(errorCode.name(), ex);
        return error(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), fieldErrors);
    }

    private static void logClientError(String errorCode, Exception ex) {
        log.warn("request rejected errorCode={} exception={} reason={}",
                errorCode,
                ex.getClass().getSimpleName(),
                ex.getMessage());
        log.trace("request rejected detail", ex);
    }

    private static ResponseEntity<CommonApiResponse<ErrorDetailResDto>> error(ErrorCode errorCode) {
        return error(errorCode.getStatus(), errorCode.name(), errorCode.getMessage(), null);
    }

    private static ResponseEntity<CommonApiResponse<ErrorDetailResDto>> error(HttpStatus status,
            String errorCode,
            String message,
            List<FieldErrorResDto> fieldErrors) {
        final var detail = new ErrorDetailResDto(errorCode, fieldErrors, TraceIdFilter.currentTraceId());
        // SDK 응답 래퍼에 의존하지 않고 HTTP 상태를 직접 지정해, 보안 필터에서 위임된 오류도 같은 상태 코드로 응답한다
        return ResponseEntity.status(status).body(new CommonApiResponse<>(status, status.value(), message, detail));
    }

    private void notifyOperators(Exception ex, HttpServletRequest request) {
        if (discordErrorNotificationService == null) {
            return;
        }
        final Map<String, Object> requestInfo = new HashMap<>();
        requestInfo.put("HTTP Method", request.getMethod());
        requestInfo.put("Request Path", request.getRequestURI());
        final var traceId = TraceIdFilter.currentTraceId();
        if (traceId != null) {
            requestInfo.put("Trace ID", traceId);
        }
        discordErrorNotificationService.notifyError(ex, null, requestInfo);
    }

    private static String leafName(ConstraintViolation<?> violation) {
        String name = null;
        for (final Path.Node node : violation.getPropertyPath()) {
            name = node.getName();
        }
        return name;
    }
}
