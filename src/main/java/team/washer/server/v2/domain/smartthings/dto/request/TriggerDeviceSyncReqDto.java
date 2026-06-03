package team.washer.server.v2.domain.smartthings.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 기기 목록 수동 동기화 요청 DTO
 *
 * <p>
 * 오발 방지를 위해 본문의 키 자체가 경고 문구이며, 값으로 {@code true}가 전달되어야만 동기화가 실행됩니다. 값이 누락되거나
 * {@code false}이면 예외가 발생합니다.
 */
@Schema(description = "기기 목록 수동 동기화 요청")
public record TriggerDeviceSyncReqDto(
        @Schema(description = "실행 확인 키. 값으로 true를 전달해야만 동기화가 실행됩니다.", example = "true", requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("정말 실행하시겠습니까 이 작업은 SmartThings 전체 기기 목록을 즉시 재동기화하며 누락된 기기를 비활성화하는 결과를 촉발합니다") Boolean confirmed) {
}
