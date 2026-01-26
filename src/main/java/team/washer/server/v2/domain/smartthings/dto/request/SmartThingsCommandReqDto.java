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
}
