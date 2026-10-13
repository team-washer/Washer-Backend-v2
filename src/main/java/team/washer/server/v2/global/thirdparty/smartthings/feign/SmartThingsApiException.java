package team.washer.server.v2.global.thirdparty.smartthings.feign;

/**
 * SmartThings가 반환한 HTTP 상태를 호출 계층에 전달하는 내부 예외이다. 외부 응답 본문이나 URL은 보존하지 않아 사용자
 * 응답으로 유출되지 않는다.
 */
public class SmartThingsApiException extends RuntimeException {

    private final int status;

    public SmartThingsApiException(final int status) {
        super("SmartThings API request failed with status=" + status);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
