package team.washer.server.v2.domain.smartthings.service;

import team.washer.server.v2.domain.smartthings.dto.request.TriggerDeviceSyncReqDto;
import team.washer.server.v2.domain.smartthings.dto.response.DeviceSyncTriggerResDto;

/**
 * 관리자가 임의 시점에 SmartThings 기기 목록 동기화를 수동으로 촉발하는 서비스
 */
public interface TriggerManualDeviceSyncService {

    DeviceSyncTriggerResDto execute(TriggerDeviceSyncReqDto request);
}
