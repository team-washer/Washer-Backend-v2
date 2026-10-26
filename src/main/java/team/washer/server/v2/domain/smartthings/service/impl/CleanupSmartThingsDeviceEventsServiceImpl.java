package team.washer.server.v2.domain.smartthings.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsDeviceEventRepository;
import team.washer.server.v2.domain.smartthings.service.CleanupSmartThingsDeviceEventsService;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsWebhookEnvironment;

/**
 * 보존 기간이 지난 SmartThings webhook event를 삭제한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CleanupSmartThingsDeviceEventsServiceImpl implements CleanupSmartThingsDeviceEventsService {

    private final SmartThingsDeviceEventRepository deviceEventRepository;
    private final SmartThingsWebhookEnvironment environment;

    @Override
    @Transactional
    public int execute() {
        final var threshold = LocalDateTime.now().minusDays(environment.eventRetentionDays());
        final var deleted = deviceEventRepository.deleteCreatedBefore(threshold);
        log.info("SmartThings device events cleaned up deleted={} retentionDays={}",
                deleted,
                environment.eventRetentionDays());
        return deleted;
    }
}
