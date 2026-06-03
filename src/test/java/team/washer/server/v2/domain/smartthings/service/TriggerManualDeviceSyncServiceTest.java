package team.washer.server.v2.domain.smartthings.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;

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
import team.washer.server.v2.domain.smartthings.support.DeviceSyncCoalescer;

@ExtendWith(MockitoExtension.class)
@DisplayName("TriggerManualDeviceSyncServiceImpl 클래스의")
class TriggerManualDeviceSyncServiceTest {

    @InjectMocks
    private TriggerManualDeviceSyncServiceImpl triggerManualDeviceSyncService;

    @Mock
    private DeviceSyncCoalescer deviceSyncCoalescer;

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("확인 키 값으로 true가 전달되어 새로 예약될 때")
        class Context_with_confirmed_true_newly_scheduled {

            @Test
            @DisplayName("코얼레서에 접수하고 예약 결과를 반환해야 한다")
            void it_submits_and_returns_scheduled_result() {
                // Given
                var scheduledAt = Instant.now().plusSeconds(5);
                given(deviceSyncCoalescer.submit())
                        .willReturn(new DeviceSyncCoalescer.Submission(true, scheduledAt, 1));

                // When
                var result = triggerManualDeviceSyncService.execute(new TriggerDeviceSyncReqDto(true));

                // Then
                then(deviceSyncCoalescer).should(times(1)).submit();
                assertThat(result.newlyScheduled()).isTrue();
                assertThat(result.scheduledAt()).isEqualTo(scheduledAt);
                assertThat(result.pendingRequestCount()).isEqualTo(1);
                assertThat(result.message()).contains("예약");
            }
        }

        @Nested
        @DisplayName("이미 예약된 요청에 통합될 때")
        class Context_coalesced {

            @Test
            @DisplayName("newlyScheduled=false와 통합 메시지를 반환해야 한다")
            void it_returns_coalesced_result() {
                // Given
                given(deviceSyncCoalescer.submit())
                        .willReturn(new DeviceSyncCoalescer.Submission(false, Instant.now().plusSeconds(3), 2));

                // When
                var result = triggerManualDeviceSyncService.execute(new TriggerDeviceSyncReqDto(true));

                // Then
                assertThat(result.newlyScheduled()).isFalse();
                assertThat(result.pendingRequestCount()).isEqualTo(2);
                assertThat(result.message()).contains("통합");
            }
        }

        @Nested
        @DisplayName("확인 키 값으로 false가 전달될 때")
        class Context_with_confirmed_false {

            @Test
            @DisplayName("ExpectedException(BAD_REQUEST)이 발생하고 접수하지 않아야 한다")
            void it_throws_bad_request_and_skips_submit() {
                // When & Then
                assertThatThrownBy(() -> triggerManualDeviceSyncService.execute(new TriggerDeviceSyncReqDto(false)))
                        .isInstanceOf(ExpectedException.class)
                        .hasMessage("실행 확인 값이 올바르지 않습니다. 확인 키의 값으로 true 를 보내야 합니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

                then(deviceSyncCoalescer).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("확인 키 값이 누락될 때")
        class Context_with_confirmed_null {

            @Test
            @DisplayName("ExpectedException(BAD_REQUEST)이 발생하고 접수하지 않아야 한다")
            void it_throws_bad_request_for_null() {
                // When & Then
                assertThatThrownBy(() -> triggerManualDeviceSyncService.execute(new TriggerDeviceSyncReqDto(null)))
                        .isInstanceOf(ExpectedException.class)
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

                then(deviceSyncCoalescer).shouldHaveNoInteractions();
            }
        }
    }
}
