package team.washer.server.v2.domain.admin.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "호실 세탁 금지 응답 DTO")
public record WashingBanResDto(@Schema(description = "금지 ID") Long id,
        @Schema(description = "호실 번호", example = "301") String roomNumber,
        @Schema(description = "금지 등록 시각") LocalDateTime bannedAt) {
}
