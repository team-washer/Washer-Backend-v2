package team.washer.server.v2.domain.smartthings.exception;

/**
 * SmartThings API에서 403 Forbidden 응답을 받았을 때 발생하는 예외. 토큰은 유효하지만 기기 제어 스코프 또는 위치
 * 권한이 없는 경우 발생합니다.
 */
public class SmartThingsPermissionException extends RuntimeException {

    public SmartThingsPermissionException(String message) {
        super(message);
    }
}
