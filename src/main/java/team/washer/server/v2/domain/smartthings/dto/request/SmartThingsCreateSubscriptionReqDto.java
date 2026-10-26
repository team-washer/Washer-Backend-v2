package team.washer.server.v2.domain.smartthings.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SmartThings 구독 생성 요청")
public record SmartThingsCreateSubscriptionReqDto(
        @Schema(description = "구독 원천 유형", example = "CAPABILITY") String sourceType,

        @Schema(description = "location 단위 capability 구독 조건") CapabilitySubscription capability) {

    public record CapabilitySubscription(@Schema(description = "location ID") String locationId,

            @Schema(description = "Capability", example = "washerOperatingState") String capability,

            @Schema(description = "Attribute. *이면 capability의 모든 attribute", example = "*") String attribute,

            @Schema(description = "값 조건. *이면 모든 값", example = "*") String value,

            @Schema(description = "값이 바뀐 event만 받을지 여부", example = "true") boolean stateChangeOnly,

            @Schema(description = "구독 이름", example = "washer_washerOperatingState") String subscriptionName) {
    }
}
