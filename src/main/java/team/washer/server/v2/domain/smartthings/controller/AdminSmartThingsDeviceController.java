package team.washer.server.v2.domain.smartthings.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.smartthings.dto.request.TriggerDeviceSyncReqDto;
import team.washer.server.v2.domain.smartthings.dto.response.DeviceSyncTriggerResDto;
import team.washer.server.v2.domain.smartthings.service.TriggerManualDeviceSyncService;

/**
 * SmartThings 기기 관리 컨트롤러 (관리자용)
 *
 * <p>
 * 자동 동기화 스케줄러와 별개로, 관리자가 임의 시점에 기기 목록 동기화를 수동으로 촉발할 수 있습니다.
 */
@RestController
@RequestMapping("/api/v2/admin/smartthings/devices")
@RequiredArgsConstructor
@Tag(name = "Admin SmartThings Device", description = "SmartThings 기기 관리 API (관리자용)")
public class AdminSmartThingsDeviceController {

    private final TriggerManualDeviceSyncService triggerManualDeviceSyncService;

    /**
     * 기기 목록 수동 동기화
     *
     * @param request
     *            실행 확인 키를 담은 요청 본문
     * @return 동기화 실행 결과
     */
    @PostMapping("/sync")
    @Operation(summary = "기기 목록 수동 동기화", description = "SmartThings 전체 기기 목록을 즉시 재동기화합니다. "
            + "오발 방지를 위해 본문의 확인 키 값으로 반드시 true를 전달해야 하며, 접수 후 3초 뒤에 실행됩니다.")
    public DeviceSyncTriggerResDto syncDevices(@Valid @RequestBody TriggerDeviceSyncReqDto request) {
        return triggerManualDeviceSyncService.execute(request);
    }
}
