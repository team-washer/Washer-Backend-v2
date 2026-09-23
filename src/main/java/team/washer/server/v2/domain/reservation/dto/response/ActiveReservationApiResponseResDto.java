package team.washer.server.v2.domain.reservation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 활성 예약 조회의 SDK 공통 응답 wrapper를 OpenAPI에 표시하기 위한 schema입니다.
 */
@Schema(name = "ActiveReservationApiResponse", description = "활성 예약 조회 공통 응답")
public record ActiveReservationApiResponseResDto(@Schema(description = "HTTP 상태 이름", example = "OK") String status,
        @Schema(description = "HTTP 상태 코드", example = "200") Integer code,
        @Schema(description = "응답 메시지", example = "OK") String message,
        @Schema(description = "활성 예약") ReservationResDto data) {
}
