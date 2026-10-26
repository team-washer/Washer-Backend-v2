package team.washer.server.v2.domain.smartthings.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsSubscriptionSyncResDto;
import team.washer.server.v2.domain.smartthings.service.SyncSmartThingsSubscriptionsService;
import team.washer.server.v2.global.config.swagger.CommonErrorResponses;

/**
 * SmartThings webhook 구독 관리 컨트롤러 (관리자용)
 */
@RestController
@RequestMapping("/api/v2/admin/smartthings/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Admin SmartThings Subscription", description = "SmartThings webhook 구독 관리 API (관리자용)")
@CommonErrorResponses
public class AdminSmartThingsSubscriptionController {

    private final SyncSmartThingsSubscriptionsService syncSmartThingsSubscriptionsService;

    /**
     * webhook 구독 동기화
     *
     * @return 생성·삭제·유지한 구독 수
     */
    @PostMapping("/sync")
    @Operation(summary = "webhook 구독 동기화", description = "세탁기 상태, 건조기 상태, 전원 capability 구독을 location 단위로 맞춥니다. "
            + "이미 있는 구독은 유지하고 없는 구독만 만들며, 이 서버가 만든 구독 중 필요 없는 것만 삭제합니다. "
            + "webhook 수신이 비활성화되어 있거나 설치 앱 ID가 없으면 409를 반환합니다.")
    public SmartThingsSubscriptionSyncResDto syncSubscriptions() {
        return syncSmartThingsSubscriptionsService.execute();
    }
}
