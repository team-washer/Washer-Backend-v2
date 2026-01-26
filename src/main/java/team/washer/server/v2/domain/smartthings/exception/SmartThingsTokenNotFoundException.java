package team.washer.server.v2.domain.smartthings.exception;

import org.springframework.http.HttpStatus;

import team.washer.server.v2.global.common.error.exception.ExpectedException;

public class SmartThingsTokenNotFoundException extends ExpectedException {

    public SmartThingsTokenNotFoundException() {
        super("SmartThings 토큰이 존재하지 않습니다", HttpStatus.NOT_FOUND);
    }

    public SmartThingsTokenNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
