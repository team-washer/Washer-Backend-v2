package team.washer.server.v2.domain.reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 예약 관련 환경 설정
 */
@ConfigurationProperties(prefix = "reservation")
public record ReservationEnvironment(boolean disableTimeRestriction) {
}
