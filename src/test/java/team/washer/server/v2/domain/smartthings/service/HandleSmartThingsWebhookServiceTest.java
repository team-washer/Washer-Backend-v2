package team.washer.server.v2.domain.smartthings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsDeviceEvent;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.enums.SmartThingsDeviceEventStatus;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsDeviceEventRepository;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.smartthings.service.impl.HandleSmartThingsWebhookServiceImpl;
import team.washer.server.v2.domain.smartthings.support.SmartThingsWebhookConfirmationSupport;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsWebhookEnvironment;

@DisplayName("HandleSmartThingsWebhookServiceImpl 클래스의")
class HandleSmartThingsWebhookServiceTest {

    private static final String APP_ID = "app-1";
    private static final String INSTALLED_APP_ID = "installed-app-1";
    private static final String CONFIRMATION_URL = "https://api.smartthings.com/apps/app-1/confirm?token=abc";

    private SmartThingsTokenRepository tokenRepository;
    private SmartThingsDeviceEventRepository deviceEventRepository;
    private SmartThingsWebhookConfirmationSupport confirmationSupport;
    private HandleSmartThingsWebhookServiceImpl service;

    @BeforeEach
    void setUp() {
        tokenRepository = mock(SmartThingsTokenRepository.class);
        deviceEventRepository = mock(SmartThingsDeviceEventRepository.class);
        confirmationSupport = mock(SmartThingsWebhookConfirmationSupport.class);
        service = new HandleSmartThingsWebhookServiceImpl(new ObjectMapper(),
                new SmartThingsWebhookEnvironment(true, APP_ID, null, 0, 0, 0, 0),
                tokenRepository,
                deviceEventRepository,
                confirmationSupport);

        final var token = SmartThingsToken.builder().accessToken("access").refreshToken("refresh")
                .expiresAt(LocalDateTime.now().plusHours(1)).installedAppId(INSTALLED_APP_ID).build();
        given(tokenRepository.findSingletonToken()).willReturn(Optional.of(token));
    }

    private static byte[] json(final String value) {
        return value.replace('\'', '"').getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] confirmation(final String appId) {
        return json("{'messageType':'CONFIRMATION','confirmationData':{'appId':'" + appId + "','confirmationUrl':'"
                + CONFIRMATION_URL + "'}}");
    }

    private static byte[] deviceEvent(final String installedAppId,
            final String eventId,
            final String capability,
            final String attribute,
            final String value) {
        return json("{'messageType':'EVENT','eventData':{'installedApp':{'installedAppId':'" + installedAppId
                + "','locationId':'location-1'},'events':[{'eventTime':'2026-10-15T15:06:53.123Z',"
                + "'eventType':'DEVICE_EVENT','deviceEvent':{'eventId':'" + eventId
                + "','locationId':'location-1','deviceId':'device-1','componentId':'main','capability':'" + capability
                + "','attribute':'" + attribute + "','value':'" + value
                + "','valueType':'string','stateChange':true,'subscriptionName':'washer_dryerOperatingState'}}]}}");
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("CONFIRMATION 메시지를 받으면")
        class Context_with_confirmation {

            @Test
            @DisplayName("설정된 앱 ID이고 허용된 주소면 등록 확인 주소를 호출한다")
            void it_confirms_registration() {
                // Given
                given(confirmationSupport.isAllowed(CONFIRMATION_URL)).willReturn(true);
                given(confirmationSupport.confirm(CONFIRMATION_URL)).willReturn(true);

                // When
                service.execute(confirmation(APP_ID));

                // Then
                then(confirmationSupport).should().confirm(CONFIRMATION_URL);
            }

            @Test
            @DisplayName("다른 앱 ID면 호출하지 않고 403 예외를 발생시킨다")
            void it_rejects_other_app() {
                // When & Then
                assertThatThrownBy(() -> service.execute(confirmation("other-app"))).isInstanceOfSatisfying(
                        ExpectedException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
                then(confirmationSupport).should(never()).confirm(any());
            }

            @Test
            @DisplayName("허용되지 않은 주소면 호출하지 않고 403 예외를 발생시킨다")
            void it_rejects_disallowed_url() {
                // Given
                given(confirmationSupport.isAllowed(CONFIRMATION_URL)).willReturn(false);

                // When & Then
                assertThatThrownBy(() -> service.execute(confirmation(APP_ID))).isInstanceOfSatisfying(
                        ExpectedException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
                then(confirmationSupport).should(never()).confirm(any());
            }
        }

        @Nested
        @DisplayName("기기 EVENT 메시지를 받으면")
        class Context_with_device_event {

            @Test
            @DisplayName("추적 대상 attribute면 필요한 필드만 RECEIVED 상태로 저장한다")
            void it_stores_tracked_event() {
                // Given
                given(deviceEventRepository.existsByEventId("event-1")).willReturn(false);

                // When
                service.execute(
                        deviceEvent(INSTALLED_APP_ID, "event-1", "dryerOperatingState", "dryerJobState", "finished"));

                // Then
                final var captor = ArgumentCaptor.forClass(SmartThingsDeviceEvent.class);
                then(deviceEventRepository).should().saveAndFlush(captor.capture());
                final var saved = captor.getValue();
                assertThat(saved.getEventId()).isEqualTo("event-1");
                assertThat(saved.getDeviceId()).isEqualTo("device-1");
                assertThat(saved.getCapability()).isEqualTo("dryerOperatingState");
                assertThat(saved.getAttribute()).isEqualTo("dryerJobState");
                assertThat(saved.getValue()).isEqualTo("finished");
                assertThat(saved.getEventTime()).isNotNull();
                assertThat(saved.getStatus()).isEqualTo(SmartThingsDeviceEventStatus.RECEIVED);
            }

            @Test
            @DisplayName("추적 대상이 아닌 attribute면 저장하지 않는다")
            void it_ignores_untracked_event() {
                // When
                service.execute(
                        deviceEvent(INSTALLED_APP_ID, "event-1", "powerConsumptionReport", "powerConsumption", "10"));

                // Then
                then(deviceEventRepository).should(never()).saveAndFlush(any());
            }

            @Test
            @DisplayName("이미 저장된 eventId면 다시 저장하지 않는다")
            void it_skips_duplicated_event() {
                // Given
                given(deviceEventRepository.existsByEventId("event-1")).willReturn(true);

                // When
                service.execute(deviceEvent(INSTALLED_APP_ID, "event-1", "switch", "switch", "off"));

                // Then
                then(deviceEventRepository).should(never()).saveAndFlush(any());
            }

            @Test
            @DisplayName("다른 인스턴스가 먼저 저장해 unique 제약에 걸려도 예외 없이 중복으로 처리한다")
            void it_treats_unique_violation_as_duplicate() {
                // Given
                given(deviceEventRepository.existsByEventId("event-1")).willReturn(false);
                given(deviceEventRepository.saveAndFlush(any()))
                        .willThrow(new DataIntegrityViolationException("duplicate"));

                // When & Then
                assertThatCode(
                        () -> service.execute(deviceEvent(INSTALLED_APP_ID, "event-1", "switch", "switch", "off")))
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("우리 설치 앱의 event가 아니면 저장하지 않고 403 예외를 발생시킨다")
            void it_rejects_other_installed_app() {
                // When & Then
                assertThatThrownBy(
                        () -> service.execute(deviceEvent("other-installed-app", "event-1", "switch", "switch", "off")))
                        .isInstanceOfSatisfying(ExpectedException.class,
                                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
                then(deviceEventRepository).should(never()).saveAndFlush(any());
            }

            @Test
            @DisplayName("저장된 설치 앱 ID가 없으면 403 예외를 발생시킨다")
            void it_rejects_when_installed_app_id_is_unknown() {
                // Given
                final var token = SmartThingsToken.builder().accessToken("access").refreshToken("refresh")
                        .expiresAt(LocalDateTime.now().plusHours(1)).build();
                given(tokenRepository.findSingletonToken()).willReturn(Optional.of(token));

                // When & Then
                assertThatThrownBy(
                        () -> service.execute(deviceEvent(INSTALLED_APP_ID, "event-1", "switch", "switch", "off")))
                        .isInstanceOfSatisfying(ExpectedException.class,
                                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
            }
        }

        @Nested
        @DisplayName("설치 앱 삭제 lifecycle EVENT를 받으면")
        class Context_with_lifecycle_delete {

            @Test
            @DisplayName("기기 event로 저장하지 않는다")
            void it_does_not_store_device_event() {
                // Given
                final var body = json("{'messageType':'EVENT','eventData':{'installedApp':{'installedAppId':'"
                        + INSTALLED_APP_ID + "'},'events':[{'eventTime':'2026-10-15T15:06:53Z',"
                        + "'eventType':'INSTALLED_APP_LIFECYCLE_EVENT','installedAppLifecycleEvent':{"
                        + "'installedAppId':'" + INSTALLED_APP_ID + "','lifecycle':'DELETE','delete':{}}}]}}");

                // When
                service.execute(body);

                // Then
                then(deviceEventRepository).should(never()).saveAndFlush(any());
            }
        }

        @Test
        @DisplayName("본문이 JSON이 아니면 400 예외를 발생시킨다")
        void it_rejects_invalid_json() {
            assertThatThrownBy(() -> service.execute("not json".getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOfSatisfying(ExpectedException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }
}
