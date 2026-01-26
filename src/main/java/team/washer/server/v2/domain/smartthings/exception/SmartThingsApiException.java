package team.washer.server.v2.domain.smartthings.exception;

import org.springframework.http.HttpStatus;

import team.washer.server.v2.global.common.error.exception.ExpectedException;

public class SmartThingsApiException extends ExpectedException {

    public SmartThingsApiException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public SmartThingsApiException(String message, HttpStatus statusCode) {
        super(message, statusCode);
    }
}
