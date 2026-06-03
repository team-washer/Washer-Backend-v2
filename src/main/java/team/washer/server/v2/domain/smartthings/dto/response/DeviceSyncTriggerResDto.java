package team.washer.server.v2.domain.smartthings.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 기기 목록 수동 동기화 실행 결과 DTO
 */
@Schema(description = "기기 목록 수동 동기화 실행 결과")
public record DeviceSyncTriggerResDto(@Schema(description = "처리 결과 메시지") String message,
        @Schema(description = "동기화 실행 시각") Instant executedAt) {
}
