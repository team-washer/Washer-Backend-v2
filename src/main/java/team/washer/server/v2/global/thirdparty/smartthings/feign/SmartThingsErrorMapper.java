package team.washer.server.v2.global.thirdparty.smartthings.feign;

import org.springframework.http.HttpStatus;

import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;

/** SmartThings 외부 오류를 서버의 안정적인 오류 계약으로 변환한다. */
public final class SmartThingsErrorMapper {

    private SmartThingsErrorMapper() {
    }

    public static ErrorCodeException toStatusException(final Throwable cause) {
        return new ErrorCodeException(resolve(cause, ErrorCode.SMARTTHINGS_STATUS_UNAVAILABLE), cause);
    }

    public static ErrorCodeException toCommandException(final Throwable cause) {
        return new ErrorCodeException(resolve(cause, ErrorCode.SMARTTHINGS_COMMAND_UNAVAILABLE), cause);
    }

    public static HttpStatus toOAuthStatus(final int externalStatus) {
        return externalStatus == HttpStatus.REQUEST_TIMEOUT.value()
                || externalStatus == HttpStatus.TOO_MANY_REQUESTS.value() || externalStatus >= 500
                        ? HttpStatus.BAD_GATEWAY
                        : HttpStatus.BAD_REQUEST;
    }

    private static ErrorCode resolve(final Throwable cause, final ErrorCode fallback) {
        if (cause instanceof SmartThingsApiException apiException) {
            final var status = apiException.getStatus();
            return switch (status) {
                case 401 -> ErrorCode.SMARTTHINGS_TOKEN_INVALID;
                case 403 -> ErrorCode.SMARTTHINGS_PERMISSION_DENIED;
                case 429 -> ErrorCode.SMARTTHINGS_RATE_LIMITED;
                case 408 -> fallback;
                default -> status >= 400 && status < 500 ? ErrorCode.SMARTTHINGS_RESPONSE_INVALID : fallback;
            };
        }
        return fallback;
    }

    public static boolean shouldReleaseCommandClaim(final ErrorCodeException exception) {
        if (isSmartThingsError(exception.getErrorCode())) {
            return switch (exception.getErrorCode()) {
                case SMARTTHINGS_TOKEN_UNAVAILABLE, SMARTTHINGS_TOKEN_INVALID, SMARTTHINGS_PERMISSION_DENIED,
                        SMARTTHINGS_RATE_LIMITED, SMARTTHINGS_RESPONSE_INVALID ->
                    true;
                default -> false;
            };
        }
        return !exception.getErrorCode().getStatus().is5xxServerError();
    }

    private static boolean isSmartThingsError(final ErrorCode errorCode) {
        return switch (errorCode) {
            case SMARTTHINGS_TOKEN_UNAVAILABLE, SMARTTHINGS_TOKEN_INVALID, SMARTTHINGS_PERMISSION_DENIED,
                    SMARTTHINGS_RATE_LIMITED, SMARTTHINGS_STATUS_UNAVAILABLE, SMARTTHINGS_COMMAND_UNAVAILABLE,
                    SMARTTHINGS_RESPONSE_INVALID ->
                true;
            default -> false;
        };
    }
}
