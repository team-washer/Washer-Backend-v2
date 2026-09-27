package team.washer.server.v2.domain.notification.service;

import team.washer.server.v2.domain.notification.dto.request.TestPushNotificationReqDto;
import team.washer.server.v2.domain.notification.dto.response.TestPushNotificationResDto;

public interface SendTestPushNotificationService {

    TestPushNotificationResDto execute(TestPushNotificationReqDto reqDto);
}
