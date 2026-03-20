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
    public record ComponentStatus(
            @JsonProperty("washerOperatingState") WasherOperatingState washerOperatingState,
            @JsonProperty("dryerOperatingState") DryerOperatingState dryerOperatingState,
            @JsonProperty("switch") SwitchCapability switchCapability) {
    }

    /**
     * washerOperatingState capability 내부 속성.
     * machineState: "run" | "pause" | "stop"
     * washerJobState: "wash" | "rinse" | "spin" | "finish" | "none" | ...
     * completionTime: ISO 8601 문자열
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WasherOperatingState(
            @JsonProperty("machineState") AttributeState machineState,
            @JsonProperty("washerJobState") AttributeState washerJobState,
            @JsonProperty("completionTime") AttributeState completionTime) {
    }

    /**
     * dryerOperatingState capability 내부 속성.
     * machineState: "run" | "pause" | "stop"
     * dryerJobState: "drying" | "cooling" | "finished" | "none" | ...
     * completionTime: ISO 8601 문자열
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DryerOperatingState(
            @JsonProperty("machineState") AttributeState machineState,
            @JsonProperty("dryerJobState") AttributeState dryerJobState,
            @JsonProperty("completionTime") AttributeState completionTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SwitchCapability(@JsonProperty("switch") AttributeState switchState) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AttributeState(@JsonProperty("value") String value,
            @JsonProperty("timestamp") String timestamp,
            @JsonProperty("unit") String unit) {
    }

    /** 세탁기 machineState 값 반환: "run" | "pause" | "stop" */
    public String getWasherOperatingState() {
        var main = getMainComponent();
        if (main == null || main.washerOperatingState() == null) {
            return null;
        }
        var ms = main.washerOperatingState().machineState();
        return ms != null ? ms.value() : null;
    }

    /** 건조기 machineState 값 반환: "run" | "pause" | "stop" */
    public String getDryerOperatingState() {
        var main = getMainComponent();
        if (main == null || main.dryerOperatingState() == null) {
            return null;
        }
        var ms = main.dryerOperatingState().machineState();
        return ms != null ? ms.value() : null;
    }

    /** washerJobState 값 반환: "wash" | "rinse" | "spin" | "finish" | ... */
    public String getWasherJobState() {
        var main = getMainComponent();
        if (main == null || main.washerOperatingState() == null) {
            return null;
        }
        var js = main.washerOperatingState().washerJobState();
        return js != null ? js.value() : null;
    }

    /** dryerJobState 값 반환: "drying" | "cooling" | "finished" | ... */
    public String getDryerJobState() {
        var main = getMainComponent();
        if (main == null || main.dryerOperatingState() == null) {
            return null;
        }
        var js = main.dryerOperatingState().dryerJobState();
        return js != null ? js.value() : null;
    }

    /** 완료 예정 시간 반환 (세탁기 우선, 없으면 건조기) */
    public String getCompletionTime() {
        var main = getMainComponent();
        if (main == null) {
            return null;
        }
        if (main.washerOperatingState() != null && main.washerOperatingState().completionTime() != null) {
            return main.washerOperatingState().completionTime().value();
        }
        if (main.dryerOperatingState() != null && main.dryerOperatingState().completionTime() != null) {
            return main.dryerOperatingState().completionTime().value();
        }
        return null;
    }

    /** 스위치 상태 값 반환: "on" | "off" */
    public String getSwitchStatus() {
        var main = getMainComponent();
        if (main == null || main.switchCapability() == null) {
            return null;
        }
        var sw = main.switchCapability().switchState();
        return sw != null ? sw.value() : null;
    }

    private ComponentStatus getMainComponent() {
        if (components == null) {
            return null;
        }
        return components.get("main");
    }
}
