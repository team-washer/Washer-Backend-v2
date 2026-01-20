package team.washer.server.v2.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 스케줄러 설정 클래스.
 *
 * <p>스케줄링 작업을 위한 전용 쓰레드풀을 구성합니다.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(final ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskScheduler());
    }

    /**
     * 스케줄링 작업을 위한 쓰레드풀 스케줄러를 생성합니다.
     *
     * @return 설정된 ThreadPoolTaskScheduler
     */
    @Bean(name = "taskScheduler")
    public Executor taskScheduler() {
        final var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2); // 현재 스케줄러 1개 + 여유분
        scheduler.setThreadNamePrefix("Scheduler-");
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        scheduler.initialize();
        return scheduler;
    }
}
