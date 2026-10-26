package team.washer.server.v2.domain.smartthings.service.impl;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsDeviceEvent;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsDeviceEventRepository;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.smartthings.service.HandleSmartThingsWebhookService;
import team.washer.server.v2.domain.smartthings.support.SmartThingsWebhookConfirmationSupport;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsWebhookEnvironment;

/**
 * 서명 검증을 통과한 SmartThings webhook 메시지를 처리한다.
 *
 * <p>
 * 요청 스레드에서는 inbox 저장까지만 하고 lifecycle 판정이나 알림 발송을 기다리지 않는다. event마다 별도 트랜잭션으로
 * 저장해 한 event의 중복(unique 위반)이 같은 요청의 다른 event 저장을 되돌리지 않도록 메서드 단위 트랜잭션을 두지 않는다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HandleSmartThingsWebhookServiceImpl implements HandleSmartThingsWebhookService {

    private static final String MAIN_COMPONENT = "main";
    private static final int MAX_FIELD_LENGTH = 64;

    /** 예약 lifecycle 판정에 쓰는 capability별 attribute. 그 외 event는 저장하지 않는다. */
    private static final Map<String, Set<String>> TRACKED_ATTRIBUTES = Map.of("washerOperatingState",
            Set.of("machineState", "washerJobState", "completionTime"),
            "dryerOperatingState",
            Set.of("machineState", "dryerJobState", "completionTime"),
            "switch",
            Set.of("switch"));

    private final ObjectMapper objectMapper;
    private final SmartThingsWebhookEnvironment environment;
    private final SmartThingsTokenRepository tokenRepository;
    private final SmartThingsDeviceEventRepository deviceEventRepository;
    private final SmartThingsWebhookConfirmationSupport confirmationSupport;

    @Override
    public void execute(final byte[] rawBody) {
        final var payload = parsePayload(rawBody);
        final var messageType = payload.path("messageType").asText("");
        switch (messageType) {
            case "CONFIRMATION" -> handleConfirmation(payload.path("confirmationData"));
            case "EVENT" -> handleEvent(payload.path("eventData"));
            default -> log.info("SmartThings webhook message ignored messageType={}", messageType);
        }
    }

    private JsonNode parsePayload(final byte[] rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (IOException e) {
            throw new ExpectedException("SmartThings webhook 요청 본문을 해석할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private void handleConfirmation(final JsonNode confirmationData) {
        final var appId = text(confirmationData, "appId");
        final var confirmationUrl = text(confirmationData, "confirmationUrl");
        if (environment.appId() == null || environment.appId().isBlank() || !environment.appId().equals(appId)) {
            log.warn("SmartThings webhook confirmation rejected reason=app_id_mismatch");
            throw new ExpectedException("허용되지 않은 SmartThings 앱의 등록 확인 요청입니다.", HttpStatus.FORBIDDEN);
        }
        if (!confirmationSupport.isAllowed(confirmationUrl)) {
            log.warn("SmartThings webhook confirmation rejected reason=url_not_allowed");
            throw new ExpectedException("허용되지 않은 SmartThings 등록 확인 주소입니다.", HttpStatus.FORBIDDEN);
        }
        if (!confirmationSupport.confirm(confirmationUrl)) {
            throw new ExpectedException("SmartThings webhook 등록 확인에 실패했습니다.", HttpStatus.BAD_GATEWAY);
        }
        log.info("SmartThings webhook target confirmed appId={}", appId);
    }

    private void handleEvent(final JsonNode eventData) {
        final var installedAppId = text(eventData.path("installedApp"), "installedAppId");
        final var expectedInstalledAppId = tokenRepository.findSingletonToken().map(SmartThingsToken::getInstalledAppId)
                .orElse(null);
        // SmartThings가 서명한 요청이라도 다른 설치 앱의 event일 수 있으므로 우리 설치 앱 ID와 일치할 때만 받는다.
        if (expectedInstalledAppId == null || !expectedInstalledAppId.equals(installedAppId)) {
            log.warn("SmartThings webhook event rejected reason=installed_app_mismatch");
            throw new ExpectedException("허용되지 않은 SmartThings 설치 앱의 이벤트입니다.", HttpStatus.FORBIDDEN);
        }

        var stored = 0;
        var duplicated = 0;
        var ignored = 0;
        for (final var event : eventData.path("events")) {
            final var eventType = text(event, "eventType");
            if ("DEVICE_EVENT".equals(eventType)) {
                switch (storeDeviceEvent(event)) {
                    case STORED -> stored++;
                    case DUPLICATED -> duplicated++;
                    case IGNORED -> ignored++;
                }
            } else if ("INSTALLED_APP_LIFECYCLE_EVENT".equals(eventType)) {
                handleLifecycle(event.path("installedAppLifecycleEvent"), installedAppId);
            } else {
                ignored++;
            }
        }
        log.info("SmartThings webhook events handled stored={} duplicated={} ignored={}", stored, duplicated, ignored);
    }

    private StoreOutcome storeDeviceEvent(final JsonNode event) {
        final var deviceEvent = event.path("deviceEvent");
        final var eventId = text(deviceEvent, "eventId");
        final var deviceId = text(deviceEvent, "deviceId");
        final var componentId = text(deviceEvent, "componentId");
        final var capability = text(deviceEvent, "capability");
        final var attribute = text(deviceEvent, "attribute");
        if (!MAIN_COMPONENT.equals(componentId) || !isTracked(capability, attribute)) {
            return StoreOutcome.IGNORED;
        }

        final var value = scalarText(deviceEvent.path("value"));
        final var eventTime = parseEventTime(text(event, "eventTime"));
        if (!fitsColumn(eventId) || !fitsColumn(deviceId) || eventTime == null
                || (value != null && value.length() > MAX_FIELD_LENGTH)) {
            log.warn("SmartThings device event ignored reason=invalid_field capability={} attribute={}",
                    capability,
                    attribute);
            return StoreOutcome.IGNORED;
        }
        if (deviceEventRepository.existsByEventId(eventId)) {
            return StoreOutcome.DUPLICATED;
        }

        try {
            deviceEventRepository.saveAndFlush(
                    SmartThingsDeviceEvent.builder().eventId(eventId).deviceId(deviceId).componentId(componentId)
                            .capability(capability).attribute(attribute).value(value).eventTime(eventTime).build());
            return StoreOutcome.STORED;
        } catch (DataIntegrityViolationException e) {
            // 다른 인스턴스가 같은 eventId를 먼저 저장한 경우다.
            return StoreOutcome.DUPLICATED;
        }
    }

    private void handleLifecycle(final JsonNode lifecycleEvent, final String installedAppId) {
        final var lifecycle = text(lifecycleEvent, "lifecycle");
        if ("DELETE".equals(lifecycle)) {
            // 설치 앱이 삭제되면 구독도 함께 사라지므로 polling만으로 동작하게 된다. 재인증과 구독 재생성이 필요하다.
            log.warn("SmartThings installed app deleted installedAppId={}", installedAppId);
            return;
        }
        log.info("SmartThings installed app lifecycle event ignored lifecycle={}", lifecycle);
    }

    private boolean isTracked(final String capability, final String attribute) {
        return capability != null && attribute != null
                && TRACKED_ATTRIBUTES.getOrDefault(capability, Set.of()).contains(attribute);
    }

    private boolean fitsColumn(final String value) {
        return value != null && value.length() <= MAX_FIELD_LENGTH;
    }

    private LocalDateTime parseEventTime(final String eventTime) {
        if (eventTime == null) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(eventTime), ZoneId.systemDefault());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String text(final JsonNode node, final String fieldName) {
        final var value = node.path(fieldName);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private String scalarText(final JsonNode value) {
        return value.isValueNode() && !value.isNull() ? value.asText() : null;
    }

    private enum StoreOutcome {
        STORED, DUPLICATED, IGNORED
    }
}
