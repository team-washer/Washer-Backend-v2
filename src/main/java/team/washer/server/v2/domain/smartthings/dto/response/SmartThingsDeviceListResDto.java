package team.washer.server.v2.domain.smartthings.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SmartThings 기기 목록 조회 응답 DTO
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SmartThingsDeviceListResDto(@JsonProperty("items") List<DeviceItem> items) {

    /**
     * SmartThings 개별 기기 항목
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeviceItem(@JsonProperty("deviceId") String deviceId, @JsonProperty("label") String label,
            @JsonProperty("name") String name) {
    }
}
