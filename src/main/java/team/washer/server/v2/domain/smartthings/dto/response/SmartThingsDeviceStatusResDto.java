package team.washer.server.v2.domain.smartthings.dto.response;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SmartThings 기기 상태 응답")
@JsonIgnoreProperties(ignoreUnknown = true)
public record SmartThingsDeviceStatusResDto(
        @Schema(description = "컴포넌트 목록") @JsonProperty("components") Map<String, ComponentStatus> components) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ComponentStatus(@JsonProperty("washerOperatingState") CapabilityStatus washerOperatingState,

            @JsonProperty("dryerOperatingState") CapabilityStatus dryerOperatingState,

            @JsonProperty("washerJobState") CapabilityStatus washerJobState,

            @JsonProperty("dryerJobState") CapabilityStatus dryerJobState,

            @JsonProperty("completionTime") CapabilityStatus completionTime,

            @JsonProperty("switch") CapabilityStatus switchStatus) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CapabilityStatus(@JsonProperty("value") Value value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Value(@JsonProperty("value") String value,

            @JsonProperty("timestamp") String timestamp,

            @JsonProperty("unit") String unit) {
    }

    public String getWasherOperatingState() {
        return getCapabilityValue("main", "washerOperatingState");
    }

    public String getDryerOperatingState() {
        return getCapabilityValue("main", "dryerOperatingState");
    }

    public String getWasherJobState() {
        return getCapabilityValue("main", "washerJobState");
    }

    public String getDryerJobState() {
        return getCapabilityValue("main", "dryerJobState");
    }

    public String getCompletionTime() {
        return getCapabilityValue("main", "completionTime");
    }

    public String getSwitchStatus() {
        return getCapabilityValue("main", "switch");
    }

    private String getCapabilityValue(String componentId, String capabilityName) {
        if (components == null || !components.containsKey(componentId)) {
            return null;
        }

        var component = components.get(componentId);
        if (component == null) {
            return null;
        }

        var capability = switch (capabilityName) {
            case "washerOperatingState" -> component.washerOperatingState();
            case "dryerOperatingState" -> component.dryerOperatingState();
            case "washerJobState" -> component.washerJobState();
            case "dryerJobState" -> component.dryerJobState();
            case "completionTime" -> component.completionTime();
            case "switch" -> component.switchStatus();
            default -> null;
        };

        if (capability == null || capability.value() == null) {
            return null;
        }

        return capability.value().value();
    }
}
