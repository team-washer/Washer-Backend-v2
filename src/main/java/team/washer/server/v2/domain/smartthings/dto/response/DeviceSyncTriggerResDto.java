package team.washer.server.v2.domain.smartthings.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 기기 목록 수동 동기화 접수 결과 DTO
 */
@Schema(description = "기기 목록 수동 동기화 접수 결과")
public record DeviceSyncTriggerResDto(@Schema(description = "처리 결과 메시지") String message,
        @Schema(description = "새 실행 창을 예약했으면 true, 기존 예약에 통합되었으면 false") boolean newlyScheduled,
        @Schema(description = "동기화 실행 예정 시각") Instant scheduledAt,
        @Schema(description = "현재 창에 통합된 누적 요청 수") int pendingRequestCount) {
}
