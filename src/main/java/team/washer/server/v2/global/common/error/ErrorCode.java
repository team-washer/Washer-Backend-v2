package team.washer.server.v2.global.common.error;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 클라이언트가 메시지 문구와 무관하게 분기할 수 있도록 응답 {@code data.errorCode}로 내려주는 안정적인 오류 코드입니다.
 * <p>
 * 상수 이름이 곧 응답 값이므로 한 번 배포된 이름은 변경하지 않습니다.
 * </p>
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {
    RESERVATION_RESTRICTION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "예약 제한 정보를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}
