package team.washer.server.v2.global.thirdparty.smartthings.feign;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import feign.RetryableException;
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

    private static ErrorCode resolve(final Throwable cause, final ErrorCode fallback) {
        if (cause instanceof SmartThingsApiException apiException) {
            return switch (apiException.getStatus()) {
                case 401 -> ErrorCode.SMARTTHINGS_TOKEN_INVALID;
                case 403 -> ErrorCode.SMARTTHINGS_PERMISSION_DENIED;
                case 429 -> ErrorCode.SMARTTHINGS_RATE_LIMITED;
                default -> fallback;
            };
        }
        if (cause instanceof RetryableException || cause instanceof SocketTimeoutException
                || cause instanceof ConnectException || cause instanceof UnknownHostException) {
            return fallback;
        }
        return fallback;
    }
}
