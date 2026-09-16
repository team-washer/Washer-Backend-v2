package team.washer.server.v2.global.common.error.exception;

import lombok.Getter;
import team.washer.server.v2.global.common.error.ErrorCode;

/**
 * 응답에 {@link ErrorCode}를 함께 내려야 하는 예외입니다.
 * <p>
 * SDK의 {@code ExpectedException}은 오류 코드를 담을 수 없으므로, 클라이언트가 코드로 분기해야 하는 경우에만
 * 사용합니다.
 * </p>
 */
@Getter
public class ErrorCodeException extends RuntimeException {

    private final ErrorCode errorCode;

    public ErrorCodeException(final ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCodeException(final ErrorCode errorCode, final Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
