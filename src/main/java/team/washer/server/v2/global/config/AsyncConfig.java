package team.washer.server.v2.global.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String FCM_TASK_EXECUTOR = "fcmTaskExecutor";
    public static final String OPERATIONAL_ALERT_TASK_EXECUTOR = "operationalAlertTaskExecutor";

    private static final int FCM_POOL_SIZE = 4;
    private static final int FCM_QUEUE_CAPACITY = 200;
    private static final int FCM_AWAIT_TERMINATION_SECONDS = 20;
    private static final int OPERATIONAL_ALERT_QUEUE_CAPACITY = 100;
    private static final int OPERATIONAL_ALERT_AWAIT_TERMINATION_SECONDS = 5;

    /**
     * FCM 전송 전용 실행기.
     *
     * <p>
     * Firebase 지연이 요청 스레드와 DB 연결, 다른 비동기 작업으로 번지지 않도록 스레드와 대기열을 고정 크기로 분리한다. 대기열이
     * 가득 차면 작업을 거절하고, 호출 측({@code FcmNotificationSupport})이 해당 푸시를 버린다. 종료 시에는 진행
     * 중인 전송을 제한 시간만큼만 기다리며, 데몬 스레드라 남은 전송이 프로세스 종료를 붙잡지 않는다.
     * </p>
     */
    @Bean(name = FCM_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor fcmTaskExecutor() {
        final var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(FCM_POOL_SIZE);
        executor.setMaxPoolSize(FCM_POOL_SIZE);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setQueueCapacity(FCM_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("Fcm-");
        executor.setDaemon(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(FCM_AWAIT_TERMINATION_SECONDS);
        return executor;
    }

    /**
     * 운영 오류 알림 전용 실행기입니다.
     *
     * <p>
     * Discord가 느리거나 사용할 수 없어도 요청과 스케줄러의 핵심 처리를 지연시키지 않기 위해 포화 작업은 버립니다.
     * </p>
     */
    @Bean(name = OPERATIONAL_ALERT_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor operationalAlertTaskExecutor() {
        final var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(OPERATIONAL_ALERT_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("OperationalAlert-");
        executor.setDaemon(true);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(OPERATIONAL_ALERT_AWAIT_TERMINATION_SECONDS);
        executor.setRejectedExecutionHandler((task, threadPool) -> log.warn(
                "operational alert rejected reason=queue_capacity activeCount={} queueSize={}",
                threadPool.getActiveCount(),
                threadPool.getQueue().size()));
        return executor;
    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        final var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Async-");
        executor.initialize();
        return executor;
    }
}
