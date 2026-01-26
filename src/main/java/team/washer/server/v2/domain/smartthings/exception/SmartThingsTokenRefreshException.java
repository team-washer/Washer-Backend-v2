package team.washer.server.v2.domain.smartthings.exception;

import org.springframework.http.HttpStatus;

import team.washer.server.v2.global.common.error.exception.ExpectedException;

/**
 * SmartThings 토큰 갱신 중 발생하는 예외
 */
public class SmartThingsTokenRefreshException extends ExpectedException {

    public SmartThingsTokenRefreshException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public SmartThingsTokenRefreshException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
        initCause(cause);
    }
}
