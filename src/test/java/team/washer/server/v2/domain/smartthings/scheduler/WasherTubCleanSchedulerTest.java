package team.washer.server.v2.domain.smartthings.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import team.washer.server.v2.domain.smartthings.service.ReleaseFinishedWasherTubCleanService;
import team.washer.server.v2.domain.smartthings.service.RunWasherTubCleanService;

@ExtendWith(MockitoExtension.class)
@DisplayName("WasherTubCleanScheduler 클래스의")
class WasherTubCleanSchedulerTest {

    @InjectMocks
    private WasherTubCleanScheduler scheduler;

    @Mock
    private RunWasherTubCleanService runWasherTubCleanService;

    @Mock
    private ReleaseFinishedWasherTubCleanService releaseFinishedWasherTubCleanService;

    @Test
    @DisplayName("통세척 예약 시각에 실행 서비스를 호출해야 한다")
    void it_runs_tub_clean_service() {
        scheduler.runTubClean();

        then(runWasherTubCleanService).should(times(1)).execute();
    }

    @Test
    @DisplayName("통세척 실행 시각은 매주 금요일 오전 10시와 서울 시간대여야 한다")
    void it_schedules_friday_at_ten_in_seoul() throws NoSuchMethodException {
        var annotation = WasherTubCleanScheduler.class.getMethod("runTubClean").getAnnotation(Scheduled.class);

        assertThat(annotation.cron()).isEqualTo("${third-party.smartthings.tub-clean.cron:0 0 10 * * FRI}");
        assertThat(annotation.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("한 번의 실행 실패가 다음 스케줄 실행을 막지 않아야 한다")
    void it_handles_run_failure() {
        willThrow(new RuntimeException("실행 실패")).given(runWasherTubCleanService).execute();

        scheduler.runTubClean();

        then(runWasherTubCleanService).should(times(1)).execute();
    }

    @Test
    @DisplayName("완료된 통세척 점유 해제 서비스를 호출해야 한다")
    void it_releases_finished_tub_clean() {
        scheduler.releaseFinishedTubClean();

        then(releaseFinishedWasherTubCleanService).should(times(1)).execute();
    }
}
