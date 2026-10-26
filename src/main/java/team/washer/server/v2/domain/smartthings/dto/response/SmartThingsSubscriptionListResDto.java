package team.washer.server.v2.domain.smartthings.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SmartThings 설치 앱 구독 목록 조회 응답 DTO
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SmartThingsSubscriptionListResDto(@JsonProperty("items") List<SubscriptionItem> items) {

    /**
     * SmartThings 개별 구독 항목. CAPABILITY 구독이 아니면 {@code capability}가 비어 있다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubscriptionItem(@JsonProperty("id") String id, @JsonProperty("sourceType") String sourceType,
            @JsonProperty("capability") CapabilitySubscription capability) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CapabilitySubscription(@JsonProperty("locationId") String locationId,
            @JsonProperty("capability") String capability, @JsonProperty("attribute") String attribute,
            @JsonProperty("subscriptionName") String subscriptionName) {
    }
}
