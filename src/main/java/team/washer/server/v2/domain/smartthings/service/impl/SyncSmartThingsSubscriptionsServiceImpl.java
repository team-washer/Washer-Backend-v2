package team.washer.server.v2.domain.smartthings.service.impl;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCreateSubscriptionReqDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceListResDto.DeviceItem;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsSubscriptionListResDto.SubscriptionItem;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsSubscriptionSyncResDto;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.smartthings.service.SyncSmartThingsSubscriptionsService;
import team.washer.server.v2.domain.smartthings.support.SmartThingsTokenProvider;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsWebhookEnvironment;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsApiException;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsFeignClient;

/**
 * SmartThings webhook 구독을 원하는 상태로 맞춘다.
 *
 * <p>
 * PoC 결과에 따라 location 단위 CAPABILITY 구독 3개(세탁기 상태, 건조기 상태, 전원)로 전체 기기를 덮는다. 이미
 * 있는 구독은 유지하고 없는 구독만 만들며, 이 서버가 만든 구독({@value #SUBSCRIPTION_NAME_PREFIX} 접두사) 중
 * 필요 없는 것만 지워 여러 번 실행해도 결과가 같다. 구독 생성은 SmartThings API 유료 정책의 영향을 받을 수 있어 관리자가
 * 직접 실행할 때만 동작한다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncSmartThingsSubscriptionsServiceImpl implements SyncSmartThingsSubscriptionsService {

    static final String SUBSCRIPTION_NAME_PREFIX = "washer_";
    private static final String CAPABILITY_SOURCE_TYPE = "CAPABILITY";
    private static final String ALL = "*";
    private static final List<String> SUBSCRIBED_CAPABILITIES = List
            .of("washerOperatingState", "dryerOperatingState", "switch");

    private final SmartThingsWebhookEnvironment environment;
    private final SmartThingsTokenRepository tokenRepository;
    private final SmartThingsTokenProvider tokenProvider;
    private final SmartThingsFeignClient feignClient;

    @Override
    public SmartThingsSubscriptionSyncResDto execute() {
        if (!environment.enabled()) {
            // 수신 경로가 꺼진 상태로 구독을 만들면 전달 실패가 쌓여 SmartThings가 구독을 비활성화할 수 있다.
            throw new ExpectedException("SmartThings webhook 수신이 비활성화되어 있어 구독을 만들 수 없습니다.", HttpStatus.CONFLICT);
        }
        final var installedAppId = tokenRepository.findSingletonToken().map(SmartThingsToken::getInstalledAppId)
                .orElse(null);
        if (installedAppId == null) {
            throw new ExpectedException("SmartThings 설치 앱 ID가 없습니다. SmartThings 관리자 재인증이 필요합니다.", HttpStatus.CONFLICT);
        }

        final var authorization = "Bearer " + tokenProvider.getValidAccessToken();
        try {
            final var desired = desiredSubscriptions(authorization);
            final var existing = feignClient.getSubscriptions(authorization, installedAppId).items();

            final var matched = new HashSet<SubscriptionKey>();
            var kept = 0;
            var deleted = 0;
            for (final var item : existing == null ? List.<SubscriptionItem>of() : existing) {
                final var key = keyOf(item);
                if (key != null && desired.contains(key) && matched.add(key)) {
                    kept++;
                } else if (isOwnedByServer(item)) {
                    feignClient.deleteSubscription(authorization, installedAppId, item.id());
                    deleted++;
                }
            }

            var created = 0;
            for (final var key : desired) {
                if (!matched.contains(key)) {
                    feignClient.createSubscription(authorization, installedAppId, toRequest(key));
                    created++;
                }
            }

            log.info("SmartThings subscriptions synced created={} deleted={} kept={}", created, deleted, kept);
            return new SmartThingsSubscriptionSyncResDto(created, deleted, kept);
        } catch (SmartThingsApiException e) {
            log.warn("SmartThings subscription sync rejected status={}", e.getStatus());
            if (e.getStatus() == HttpStatus.FORBIDDEN.value()) {
                throw new ExpectedException("SmartThings가 구독 요청을 거부했습니다. 권한 범위나 API 이용 요금제를 확인해 주세요.",
                        HttpStatus.BAD_GATEWAY);
            }
            throw new ExpectedException("SmartThings 구독 동기화에 실패했습니다.", HttpStatus.BAD_GATEWAY);
        }
    }

    /** 기기 목록의 location마다 구독할 capability 조합을 만든다. */
    private Set<SubscriptionKey> desiredSubscriptions(final String authorization) {
        final var items = feignClient.getDeviceList(authorization).items();
        final var locationIds = (items == null ? List.<DeviceItem>of() : items).stream().map(DeviceItem::locationId)
                .filter(Objects::nonNull).distinct().toList();
        if (locationIds.isEmpty()) {
            throw new ExpectedException("구독할 SmartThings location을 찾을 수 없습니다.", HttpStatus.CONFLICT);
        }

        final var desired = new LinkedHashSet<SubscriptionKey>();
        for (final var locationId : locationIds) {
            for (final var capability : SUBSCRIBED_CAPABILITIES) {
                desired.add(new SubscriptionKey(locationId, capability));
            }
        }
        return desired;
    }

    private SubscriptionKey keyOf(final SubscriptionItem item) {
        final var capability = item.capability();
        if (!CAPABILITY_SOURCE_TYPE.equals(item.sourceType()) || capability == null
                || !ALL.equals(capability.attribute())) {
            return null;
        }
        return new SubscriptionKey(capability.locationId(), capability.capability());
    }

    private boolean isOwnedByServer(final SubscriptionItem item) {
        return item.capability() != null && item.capability().subscriptionName() != null
                && item.capability().subscriptionName().startsWith(SUBSCRIPTION_NAME_PREFIX);
    }

    private SmartThingsCreateSubscriptionReqDto toRequest(final SubscriptionKey key) {
        return new SmartThingsCreateSubscriptionReqDto(CAPABILITY_SOURCE_TYPE,
                new SmartThingsCreateSubscriptionReqDto.CapabilitySubscription(key
                        .locationId(), key.capability(), ALL, ALL, true, SUBSCRIPTION_NAME_PREFIX + key.capability()));
    }

    private record SubscriptionKey(String locationId, String capability) {
    }
}
