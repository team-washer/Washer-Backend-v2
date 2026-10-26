package team.washer.server.v2.domain.smartthings.service;

import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsSubscriptionSyncResDto;

public interface SyncSmartThingsSubscriptionsService {
    SmartThingsSubscriptionSyncResDto execute();
}
