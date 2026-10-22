package team.washer.server.v2.domain.smartthings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCreateSubscriptionReqDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceListResDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceListResDto.DeviceItem;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsSubscriptionListResDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsSubscriptionListResDto.CapabilitySubscription;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsSubscriptionListResDto.SubscriptionItem;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.smartthings.service.impl.SyncSmartThingsSubscriptionsServiceImpl;
import team.washer.server.v2.domain.smartthings.support.SmartThingsTokenProvider;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsWebhookEnvironment;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsApiException;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsFeignClient;

@DisplayName("SyncSmartThingsSubscriptionsServiceImpl 클래스의")
class SyncSmartThingsSubscriptionsServiceTest {

    private static final String INSTALLED_APP_ID = "installed-app-1";
    private static final String AUTHORIZATION = "Bearer access";

    private SmartThingsTokenRepository tokenRepository;
    private SmartThingsTokenProvider tokenProvider;
    private SmartThingsFeignClient feignClient;

    @BeforeEach
    void setUp() {
        tokenRepository = mock(SmartThingsTokenRepository.class);
        tokenProvider = mock(SmartThingsTokenProvider.class);
        feignClient = mock(SmartThingsFeignClient.class);
        given(tokenProvider.getValidAccessToken()).willReturn("access");
        given(tokenRepository.findSingletonToken()).willReturn(Optional.of(token(INSTALLED_APP_ID)));
        given(feignClient.getDeviceList(AUTHORIZATION)).willReturn(new SmartThingsDeviceListResDto(
                List.of(new DeviceItem("washer-1", "Washer-3F-R1", "Washer", "location-1"),
                        new DeviceItem("dryer-1", "Dryer-3F-L1", "Dryer", "location-1"))));
    }

    private static SmartThingsToken token(final String installedAppId) {
        return SmartThingsToken.builder().accessToken("access").refreshToken("refresh")
                .expiresAt(LocalDateTime.now().plusHours(1)).installedAppId(installedAppId).build();
    }

    private SyncSmartThingsSubscriptionsServiceImpl service(final boolean enabled) {
        return new SyncSmartThingsSubscriptionsServiceImpl(
                new SmartThingsWebhookEnvironment(enabled, "app-1", null, 0, 0, 0, 0),
                tokenRepository,
                tokenProvider,
                feignClient);
    }

    private static SubscriptionItem capabilitySubscription(final String id,
            final String capability,
            final String name) {
        return new SubscriptionItem(id, "CAPABILITY", new CapabilitySubscription("location-1", capability, "*", name));
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Test
        @DisplayName("없는 구독만 만들고 이미 있는 구독은 유지한다")
        void it_creates_only_missing_subscriptions() {
            // Given
            given(feignClient.getSubscriptions(AUTHORIZATION, INSTALLED_APP_ID))
                    .willReturn(new SmartThingsSubscriptionListResDto(
                            List.of(capabilitySubscription("sub-1", "switch", "washer_switch"))));

            // When
            final var result = service(true).execute();

            // Then
            assertThat(result.created()).isEqualTo(2);
            assertThat(result.kept()).isEqualTo(1);
            assertThat(result.deleted()).isZero();
            final var captor = ArgumentCaptor.forClass(SmartThingsCreateSubscriptionReqDto.class);
            then(feignClient).should(times(2)).createSubscription(anyString(), anyString(), captor.capture());
            assertThat(captor.getAllValues()).extracting(request -> request.capability().capability())
                    .containsExactlyInAnyOrder("washerOperatingState", "dryerOperatingState");
            assertThat(captor.getAllValues()).allSatisfy(request -> {
                assertThat(request.sourceType()).isEqualTo("CAPABILITY");
                assertThat(request.capability().locationId()).isEqualTo("location-1");
                assertThat(request.capability().stateChangeOnly()).isTrue();
            });
        }

        @Test
        @DisplayName("서버가 만든 구독 중 필요 없는 것만 삭제하고 다른 구독은 건드리지 않는다")
        void it_deletes_only_stale_owned_subscriptions() {
            // Given
            given(feignClient.getSubscriptions(AUTHORIZATION, INSTALLED_APP_ID))
                    .willReturn(new SmartThingsSubscriptionListResDto(List.of(
                            capabilitySubscription("sub-1", "washerOperatingState", "washer_washerOperatingState"),
                            capabilitySubscription("sub-2", "dryerOperatingState", "washer_dryerOperatingState"),
                            capabilitySubscription("sub-3", "switch", "washer_switch"),
                            capabilitySubscription("sub-4", "switch", "washer_switch"),
                            capabilitySubscription("sub-5", "powerMeter", "washer_powerMeter"),
                            capabilitySubscription("sub-6", "powerMeter", "manual_powerMeter"))));

            // When
            final var result = service(true).execute();

            // Then
            assertThat(result.created()).isZero();
            assertThat(result.kept()).isEqualTo(3);
            assertThat(result.deleted()).isEqualTo(2);
            then(feignClient).should().deleteSubscription(AUTHORIZATION, INSTALLED_APP_ID, "sub-4");
            then(feignClient).should().deleteSubscription(AUTHORIZATION, INSTALLED_APP_ID, "sub-5");
            then(feignClient).should(never()).deleteSubscription(AUTHORIZATION, INSTALLED_APP_ID, "sub-6");
        }

        @Test
        @DisplayName("webhook 수신이 꺼져 있으면 SmartThings를 호출하지 않고 409 예외를 발생시킨다")
        void it_rejects_when_webhook_is_disabled() {
            // When & Then
            assertThatThrownBy(() -> service(false).execute()).isInstanceOfSatisfying(ExpectedException.class,
                    e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
            then(feignClient).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("설치 앱 ID가 없으면 SmartThings를 호출하지 않고 409 예외를 발생시킨다")
        void it_rejects_when_installed_app_id_is_missing() {
            // Given
            given(tokenRepository.findSingletonToken()).willReturn(Optional.of(token(null)));

            // When & Then
            assertThatThrownBy(() -> service(true).execute()).isInstanceOfSatisfying(ExpectedException.class,
                    e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
            then(feignClient).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("SmartThings가 403으로 거부하면 502 예외로 변환한다")
        void it_converts_forbidden_response() {
            // Given
            given(feignClient.getSubscriptions(AUTHORIZATION, INSTALLED_APP_ID))
                    .willReturn(new SmartThingsSubscriptionListResDto(List.of()));
            given(feignClient.getDeviceList(AUTHORIZATION)).willThrow(new SmartThingsApiException(403));

            // When & Then
            assertThatThrownBy(() -> service(true).execute()).isInstanceOfSatisfying(ExpectedException.class,
                    e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
            then(feignClient).should(never()).createSubscription(anyString(), anyString(), any());
        }
    }
}
