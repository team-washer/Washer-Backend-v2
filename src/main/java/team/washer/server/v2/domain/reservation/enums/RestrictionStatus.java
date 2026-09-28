package team.washer.server.v2.domain.reservation.enums;

/**
 * Redis에 저장된 예약 제한(쿨다운·호실 차단)의 조회 결과입니다.
 * <p>
 * 조회 실패를 제한 없음과 구분하여, 호출자가 장애 시 허용·거부 정책을 직접 결정하도록 합니다.
 * </p>
 */
public enum RestrictionStatus {
    /** 적용 중인 제한이 없음 */
    NONE,
    /** 제한이 적용 중임 */
    RESTRICTED,
    /** Redis 오류로 제한 여부를 확인할 수 없음 */
    UNAVAILABLE
}
