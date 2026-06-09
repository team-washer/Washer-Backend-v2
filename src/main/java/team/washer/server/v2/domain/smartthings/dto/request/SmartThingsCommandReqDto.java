package team.washer.server.v2.domain.smartthings.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SmartThings 기기 명령 요청")
public record SmartThingsCommandReqDto(@Schema(description = "명령 목록") List<Command> commands) {

    public record Command(@Schema(description = "컴포넌트 ID", example = "main") String component,

            @Schema(description = "Capability", example = "switch") String capability,

            @Schema(description = "명령", example = "off") String command,

            @Schema(description = "인자 목록") List<Object> arguments) {
    }

    public static SmartThingsCommandReqDto powerOff() {
        return new SmartThingsCommandReqDto(List.of(new Command("main", "switch", "off", List.of())));
    }

    public static SmartThingsCommandReqDto powerOn() {
        return new SmartThingsCommandReqDto(List.of(new Command("main", "switch", "on", List.of())));
    }

    /**
     * 세탁기를 안전하게 정지시킨다. 전원 차단(switch off)과 달리 사이클을 정상 종료하므로 작동 중 기기에 사용한다. 기기에서 원격
     * 제어(Smart Control)가 활성화되어 있어야 명령이 적용된다.
     */
    public static SmartThingsCommandReqDto stopWasher() {
        return new SmartThingsCommandReqDto(
                List.of(new Command("main", "washerOperatingState", "setMachineState", List.of("stop"))));
    }

    /**
     * 건조기를 안전하게 정지시킨다. 전원 차단(switch off)과 달리 사이클을 정상 종료하므로 작동 중 기기에 사용한다. 기기에서 원격
     * 제어(Smart Control)가 활성화되어 있어야 명령이 적용된다.
     */
    public static SmartThingsCommandReqDto stopDryer() {
        return new SmartThingsCommandReqDto(
                List.of(new Command("main", "dryerOperatingState", "setMachineState", List.of("stop"))));
    }
}
