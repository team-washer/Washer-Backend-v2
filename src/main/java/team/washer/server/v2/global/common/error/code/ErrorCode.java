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
                                                                                                    "기기 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요."),

    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."), ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,
            "JWT 토큰이 만료되었습니다."), ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED,
                    "유효하지 않은 JWT 토큰입니다."), REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,
                            "JWT 토큰이 만료되었습니다."), REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED,
                                    "유효하지 않은 Refresh Token입니다."), WITHDRAWN_REJOIN_RESTRICTED(HttpStatus.FORBIDDEN,
                                            "탈퇴 후 30일이 지나지 않아 재가입할 수 없습니다."), USER_NOT_FOUND(HttpStatus.NOT_FOUND,
                                                    "사용자를 찾을 수 없습니다"), RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND,
                                                            "예약을 찾을 수 없습니다"), MACHINE_NOT_FOUND(HttpStatus.NOT_FOUND,
                                                                    "기기를 찾을 수 없습니다"), ROOM_NOT_FOUND(
                                                                            HttpStatus.BAD_REQUEST,
                                                                            "호실 정보가 존재하지 않습니다."), ROOM_WASHING_BANNED(
                                                                                    HttpStatus.FORBIDDEN,
                                                                                    "해당 호실은 현재 세탁이 금지된 상태입니다."), USER_FLOOR_RESTRICTED(
                                                                                            HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS,
                                                                                            "1~4층 기숙사생이 아니라면 서비스를 이용할 수 없습니다."), USER_PENALTY_ACTIVE(
                                                                                                    HttpStatus.BAD_REQUEST,
                                                                                                    "현재 예약이 제한되어 있습니다."), RESERVATION_COOLDOWN_ACTIVE(
                                                                                                            HttpStatus.BAD_REQUEST,
                                                                                                            "예약 취소 후 잠시 동안 같은 종류의 기기를 예약할 수 없습니다."), ROOM_RESERVATION_RESTRICTED(
                                                                                                                    HttpStatus.BAD_REQUEST,
                                                                                                                    "48시간 내 취소 횟수를 초과하여 예약이 제한됩니다"), RESERVATION_TIME_RESTRICTED(
                                                                                                                            HttpStatus.BAD_REQUEST,
                                                                                                                            "현재 시간에는 예약할 수 없습니다."), MACHINE_UNAVAILABLE(
                                                                                                                                    HttpStatus.BAD_REQUEST,
                                                                                                                                    "해당 기기를 사용할 수 없습니다."), MACHINE_SHUTDOWN_IN_PROGRESS(
                                                                                                                                            HttpStatus.CONFLICT,
                                                                                                                                            "기기 종료 처리 중입니다. 잠시 후 다시 시도해 주세요"), MACHINE_ALREADY_RESERVED(
                                                                                                                                                    HttpStatus.CONFLICT,
                                                                                                                                                    "해당 기기는 이미 예약되어 있습니다. 다른 기기를 선택해 주세요."), MACHINE_IN_USE(
                                                                                                                                                            HttpStatus.CONFLICT,
                                                                                                                                                            "해당 기기는 현재 사용 중입니다. 잠시 후 다시 시도해 주세요."), USER_ACTIVE_RESERVATION(
                                                                                                                                                                    HttpStatus.BAD_REQUEST,
                                                                                                                                                                    "이미 활성 예약이 있습니다. 기존 예약을 확인해 주세요."), ROOM_MACHINE_TYPE_RESERVED(
                                                                                                                                                                            HttpStatus.BAD_REQUEST,
                                                                                                                                                                            "같은 호실에 같은 종류의 기기 예약이 있습니다."), RESERVATION_CANCELLATION_CONFLICT(
                                                                                                                                                                                    HttpStatus.CONFLICT,
                                                                                                                                                                                    "기기 사용이 시작되어 예약을 취소할 수 없습니다."), RESERVATION_STATE_INVALID(
                                                                                                                                                                                            HttpStatus.BAD_REQUEST,
                                                                                                                                                                                            "현재 상태에서는 예약을 처리할 수 없습니다."), RESERVATION_ACCESS_DENIED(
                                                                                                                                                                                                    HttpStatus.FORBIDDEN,
                                                                                                                                                                                                    "예약을 처리할 권한이 없습니다.");

    private final HttpStatus status;
    private final String message;
}
