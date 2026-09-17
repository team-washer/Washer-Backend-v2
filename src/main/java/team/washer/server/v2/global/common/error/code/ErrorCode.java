package team.washer.server.v2.global.common.error.code;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 프레임워크·인프라 예외를 응답으로 변환할 때 사용하는 안정적인 오류 코드.
 *
 * <p>
 * 클라이언트는 {@code message} 문구 대신 이 코드로 오류 유형을 분기해야 한다. 코드 이름은 응답 계약이므로 변경하지 않는다.
 * {@code ExpectedException}은 별도 코드를 갖지 않으므로 HTTP 상태 이름(예: {@code NOT_FOUND})을
 * 오류 코드로 사용한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."), INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST,
            "요청 본문 형식이 올바르지 않습니다."), TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다."), MISSING_PARAMETER(
                    HttpStatus.BAD_REQUEST,
                    "필수 요청 값이 누락되었습니다."), UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."), FORBIDDEN(
                            HttpStatus.FORBIDDEN,
                            "접근 권한이 없습니다."), NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."), METHOD_NOT_ALLOWED(
                                    HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."), CONFLICT(
                                            HttpStatus.CONFLICT, "다른 요청과 충돌했습니다. 잠시 후 다시 시도해 주세요."), PAYLOAD_TOO_LARGE(
                                                    HttpStatus.CONTENT_TOO_LARGE,
                                                    "파일 크기가 허용된 최대 크기를 초과했습니다."), UNSUPPORTED_MEDIA_TYPE(
                                                            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                                                            "지원하지 않는 요청 형식입니다."), CLIENT_ERROR(HttpStatus.BAD_REQUEST,
                                                                    "요청을 처리할 수 없습니다."), SERVICE_UNAVAILABLE(
                                                                            HttpStatus.SERVICE_UNAVAILABLE,
                                                                            "일시적으로 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."), INTERNAL_SERVER_ERROR(
                                                                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                                                                    "서버 내부 오류가 발생했습니다."), RESERVATION_RESTRICTION_UNAVAILABLE(
                                                                                            HttpStatus.SERVICE_UNAVAILABLE,
                                                                                            "예약 제한 정보를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요."), MACHINE_STATE_UNAVAILABLE(
                                                                                                    HttpStatus.SERVICE_UNAVAILABLE,
                                                                                                    "기기 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}
