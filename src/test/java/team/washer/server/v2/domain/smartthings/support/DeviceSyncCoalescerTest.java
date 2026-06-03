package team.washer.server.v2.domain.smartthings.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import team.washer.server.v2.domain.smartthings.service.SyncSmartThingsDevicesService;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceSyncCoalescer 클래스의")
class DeviceSyncCoalescerTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private SyncSmartThingsDevicesService syncSmartThingsDevicesService;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;

    private DeviceSyncCoalescer deviceSyncCoalescer;

    @BeforeEach
    void setUp() {
        deviceSyncCoalescer = new DeviceSyncCoalescer(taskScheduler, syncSmartThingsDevicesService);
    }

    @Nested
    @DisplayName("submit 메서드는")
    class Describe_submit {

        @Nested
        @DisplayName("진행 중인 창이 없을 때")
        class Context_no_pending_window {

            @Test
            @DisplayName("새 실행 창을 예약하고 newlyScheduled=true를 반환해야 한다")
            void it_schedules_new_window() {
                // When
                var submission = deviceSyncCoalescer.submit();

                // Then
                assertThat(submission.newlyScheduled()).isTrue();
                assertThat(submission.pendingRequestCount()).isEqualTo(1);
                then(taskScheduler).should(times(1)).schedule(any(Runnable.class), any(Instant.class));
            }
        }

        @Nested
        @DisplayName("이미 진행 중인 창이 있을 때")
        class Context_with_pending_window {

            @Test
            @DisplayName("새 예약 없이 기존 창에 통합하고 누적 요청 수를 증가시켜야 한다")
            void it_coalesces_into_existing_window() {
                // Given
                var first = deviceSyncCoalescer.submit();

                // When
                var second = deviceSyncCoalescer.submit();

                // Then
                assertThat(first.newlyScheduled()).isTrue();
                assertThat(second.newlyScheduled()).isFalse();
                assertThat(second.pendingRequestCount()).isEqualTo(2);
                assertThat(second.scheduledAt()).isEqualTo(first.scheduledAt());
                then(taskScheduler).should(times(1)).schedule(any(Runnable.class), any(Instant.class));
            }
        }

        @Nested
        @DisplayName("예약된 창이 실행될 때")
        class Context_window_fires {

            @Test
            @DisplayName("통합된 요청에 대해 동기화를 한 번만 실행하고 이후 요청은 새 창을 열어야 한다")
            void it_executes_once_then_reopens_window() {
                // Given
                deviceSyncCoalescer.submit();
                deviceSyncCoalescer.submit(); // 기존 창에 통합 (예약은 1회만)
                then(taskScheduler).should(times(1)).schedule(runnableCaptor.capture(), any(Instant.class));

                // When: 창 실행
                runnableCaptor.getValue().run();

                // Then: 동기화는 한 번만 실행
                then(syncSmartThingsDevicesService).should(times(1)).execute();

                // When: 실행 후 새 요청
                var afterFire = deviceSyncCoalescer.submit();

                // Then: 새 창이 예약되어야 한다 (총 2회 예약)
                assertThat(afterFire.newlyScheduled()).isTrue();
                then(taskScheduler).should(times(2)).schedule(any(Runnable.class), any(Instant.class));
            }
        }
    }
}
