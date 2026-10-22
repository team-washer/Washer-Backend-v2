package team.washer.server.v2.domain.smartthings.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SmartThings webhook 구독 동기화 결과 DTO
 */
@Schema(description = "SmartThings webhook 구독 동기화 결과")
public record SmartThingsSubscriptionSyncResDto(@Schema(description = "새로 만든 구독 수") int created,
        @Schema(description = "필요 없어 삭제한 구독 수") int deleted, @Schema(description = "이미 있어 유지한 구독 수") int kept) {
}
