package team.washer.server.v2.domain.smartthings.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.smartthings.dto.request.TriggerDeviceSyncReqDto;
import team.washer.server.v2.domain.smartthings.service.impl.TriggerManualDeviceSyncServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("TriggerManualDeviceSyncServiceImpl 클래스의")
class TriggerManualDeviceSyncServiceTest {

    @InjectMocks
    private TriggerManualDeviceSyncServiceImpl triggerManualDeviceSyncService;

    @Mock
    private SyncSmartThingsDevicesService syncSmartThingsDevicesService;

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("확인 키 값으로 true가 전달될 때")
        class Context_with_confirmed_true {

            @Test
            @DisplayName("3초 대기 후 기기 동기화를 실행하고 결과를 반환해야 한다")
            void it_executes_sync_after_delay() {
                // Given
                var request = new TriggerDeviceSyncReqDto(true);

                // When
                var startedAt = System.currentTimeMillis();
                var result = triggerManualDeviceSyncService.execute(request);
                var elapsed = System.currentTimeMillis() - startedAt;

                // Then
                then(syncSmartThingsDevicesService).should(times(1)).execute();
                assertThat(elapsed).isGreaterThanOrEqualTo(3000L);
                assertThat(result.message()).isEqualTo("기기 목록 동기화를 실행했습니다.");
                assertThat(result.executedAt()).isNotNull();
            }
        }

        @Nested
        @DisplayName("확인 키 값으로 false가 전달될 때")
        class Context_with_confirmed_false {

            @Test
            @DisplayName("ExpectedException이 발생하고 BAD_REQUEST 상태를 반환하며 동기화를 실행하지 않아야 한다")
            void it_throws_bad_request_and_skips_sync() {
                // Given
                var request = new TriggerDeviceSyncReqDto(false);

                // When & Then
                assertThatThrownBy(() -> triggerManualDeviceSyncService.execute(request))
                        .isInstanceOf(ExpectedException.class)
                        .hasMessage("실행 확인 값이 올바르지 않습니다. 확인 키의 값으로 true 를 보내야 합니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

                then(syncSmartThingsDevicesService).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("확인 키 값이 누락될 때")
        class Context_with_confirmed_null {

            @Test
            @DisplayName("ExpectedException이 발생하고 BAD_REQUEST 상태를 반환하며 동기화를 실행하지 않아야 한다")
            void it_throws_bad_request_for_null() {
                // Given
                var request = new TriggerDeviceSyncReqDto(null);

                // When & Then
                assertThatThrownBy(() -> triggerManualDeviceSyncService.execute(request))
                        .isInstanceOf(ExpectedException.class)
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

                then(syncSmartThingsDevicesService).shouldHaveNoInteractions();
            }
        }
    }
}
