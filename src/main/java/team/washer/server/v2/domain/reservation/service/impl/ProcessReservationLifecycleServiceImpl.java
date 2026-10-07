package team.washer.server.v2.domain.reservation.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.ProcessReservationLifecycleService;
import team.washer.server.v2.domain.reservation.service.impl.ReservationLifecycleProcessor.CompletedMachine;
import team.washer.server.v2.domain.reservation.service.impl.ReservationLifecycleProcessor.RunningTarget;
import team.washer.server.v2.domain.reservation.support.LongRunningReservationMonitor;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.DeviceShutdownSupport;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.domain.smartthings.support.MachineShutdownClaimSupport;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
import team.washer.server.v2.global.thirdparty.discord.service.DiscordErrorNotificationService;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsErrorMapper;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * 예약 라이프사이클 스케줄링 진입점.
 *
 * <p>
 * 처리 대상 조회와 개별 예약의 DB 갱신은 {@link ReservationLifecycleProcessor}가 독립 트랜잭션으로
 * 수행하고, 이 클래스는 트랜잭션 밖에서 SmartThings 외부 API를 호출한 뒤 그 결과를 넘겨주는 조율만 담당한다. 외부 API
 * 호출이 DB 커넥션을 점유하지 않도록 하기 위함이다.
 *
 * <p>
 * 예약이 완료되면 트랜잭션이 끝난 뒤 완료 판정에 사용한 기기 상태로 전원을 차단한다. 서버는 전원을 켜지 않으므로 다음 예약자가 직접
 * 기기를 켜야만 동작한다. 전원 차단이 SmartThings 권한 오류로 실패하면 남은 예약도 같은 이유로 실패하므로, 유휴 기기 종료
 * 스케줄러와 마찬가지로 Discord로 한 번만 알리고 이번 주기를 중단한다.
 *
 * <p>
 * RUNNING 예약의 기기 상태 조회 실패는 연속 실패 횟수와 함께 기록하고, 매 주기 끝에 장기 실행 예약을
 * {@link LongRunningReservationMonitor}로 보고한다. 장기 실행 예약도 자동으로 완료하지 않고 완료 신호를 받을
 * 때까지 기존 판정을 따른다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessReservationLifecycleServiceImpl implements ProcessReservationLifecycleService {

    public static final String LIFECYCLE_RUN_ID_MDC_KEY = "lifecycleRunId";

    private final ReservationLifecycleProcessor reservationLifecycleProcessor;
    private final DeviceStatusQuerySupport deviceStatusQuerySupport;
    private final DeviceShutdownSupport deviceShutdownSupport;
    private final MachineShutdownClaimSupport machineShutdownClaimSupport;
    private final LongRunningReservationMonitor longRunningReservationMonitor;

    @Autowired(required = false)
    private DiscordErrorNotificationService discordErrorNotificationService;

    @Override
    public void execute() {
        MDC.put(LIFECYCLE_RUN_ID_MDC_KEY, UUID.randomUUID().toString());
        try {
            processReservedToRunning();

            var runningTargets = reservationLifecycleProcessor.findRunningTargets();
            longRunningReservationMonitor
                    .retainOnly(runningTargets.stream().map(RunningTarget::reservationId).toList());
            processRunningToCompleted(runningTargets);
            reportLongRunningReservations(runningTargets);
        } finally {
            MDC.remove(LIFECYCLE_RUN_ID_MDC_KEY);
        }
    }

    private void processReservedToRunning() {
        for (var target : reservationLifecycleProcessor.findReservedTargets()) {
            try {
                var status = deviceStatusQuerySupport.queryDeviceStatus(target.deviceId());
                reservationLifecycleProcessor.processReservedToRunning(target.reservationId(), status);
            } catch (Exception e) {
                log.error("lifecycle event=reserved_processing_failed reservationId={} errorType={}",
                        target.reservationId(),
                        e.getClass().getSimpleName());
            }
        }
    }

    private void processRunningToCompleted(List<RunningTarget> targets) {
        for (var target : targets) {
            SmartThingsDeviceStatusResDto status;
            try {
                status = deviceStatusQuerySupport.queryDeviceStatus(target.deviceId());
                longRunningReservationMonitor.recordQuerySuccess(target.reservationId());
            } catch (Exception e) {
                var failures = longRunningReservationMonitor.recordQueryFailure(target.reservationId());
                log.warn("lifecycle event=status_query_failed reservationId={} consecutiveFailures={} errorType={}",
                        target.reservationId(),
                        failures,
                        e.getClass().getSimpleName());
                continue;
            }
            try {
                var completedMachine = reservationLifecycleProcessor.processRunningToCompleted(target.reservationId(),
                        status);
                if (completedMachine.isPresent() && !shutdownCompletedMachine(completedMachine.get(), status)) {
                    return;
                }
            } catch (Exception e) {
                log.error("lifecycle event=running_processing_failed reservationId={} errorType={}",
                        target.reservationId(),
                        e.getClass().getSimpleName());
            }
        }
    }

    /**
     * 장기 실행 기준을 넘긴 RUNNING 예약을 운영 로그로 보고한다. 상태 전이 처리와 같은 조회 결과를 사용하며, 식별만 하고 예약 상태를
     * 바꾸지 않는다.
     */
    private void reportLongRunningReservations(List<RunningTarget> targets) {
        var longRunningTargets = targets.stream().filter(RunningTarget::longRunning).toList();
        if (longRunningTargets.isEmpty()) {
            return;
        }
        try {
            longRunningReservationMonitor.report(longRunningTargets, DateTimeUtil.nowInKorea());
        } catch (Exception e) {
            log.error("lifecycle event=long_running_report_failed errorType={}", e.getClass().getSimpleName());
        }
    }

    /**
     * 완료된 예약의 기기 전원을 차단한다.
     *
     * @return 남은 예약 처리를 계속해도 되면 {@code true}, SmartThings 권한 오류로 이번 주기를 중단해야 하면
     *         {@code false}
     */
    private boolean shutdownCompletedMachine(CompletedMachine completedMachine, SmartThingsDeviceStatusResDto status) {
        final var claim = new MachineShutdownClaimSupport.ShutdownClaim(completedMachine.machineId(),
                completedMachine.shutdownClaimToken());
        try {
            deviceShutdownSupport.shutdownAfterCompletion(completedMachine.machineName(),
                    completedMachine.deviceId(),
                    completedMachine.isWasher(),
                    status,
                    () -> machineShutdownClaimSupport.beginCommand(claim));
            releaseClaim(claim, completedMachine);
            log.info("lifecycle event=shutdown_after_completion machineId={} outcome=completed claimReleased=true",
                    completedMachine.machineId());
            return true;
        } catch (ErrorCodeException e) {
            if (e.getErrorCode() != ErrorCode.SMARTTHINGS_PERMISSION_DENIED) {
                if (SmartThingsErrorMapper.shouldReleaseCommandClaim(e)) {
                    releaseClaim(claim, completedMachine);
                }
                log.error("lifecycle event=shutdown_after_completion machineId={} outcome=failed errorCode={}",
                        completedMachine.machineId(),
                        e.getErrorCode());
                return true;
            }
            log.warn("lifecycle event=shutdown_after_completion machineId={} outcome=permission_denied errorCode={}",
                    completedMachine.machineId(),
                    e.getErrorCode());
            releaseClaim(claim, completedMachine);
            notifyPermissionError(completedMachine, e);
            return false;
        } catch (Exception e) {
            log.error("lifecycle event=shutdown_after_completion machineId={} outcome=unknown errorType={}",
                    completedMachine.machineId(),
                    e.getClass().getSimpleName());
            return true;
        }
    }

    private void releaseClaim(MachineShutdownClaimSupport.ShutdownClaim claim, CompletedMachine completedMachine) {
        try {
            machineShutdownClaimSupport.release(claim);
        } catch (Exception e) {
            log.error("lifecycle event=shutdown_claim_release_failed machineId={} errorType={}",
                    completedMachine.machineId(),
                    e.getClass().getSimpleName());
        }
    }

    private void notifyPermissionError(CompletedMachine completedMachine, ErrorCodeException e) {
        if (discordErrorNotificationService == null) {
            return;
        }
        discordErrorNotificationService.notifyError(e,
                "예약 완료 기기 종료 - SmartThings 권한 오류",
                Map.of("감지된 기기",
                        completedMachine.machineName(),
                        "조치 필요",
                        "SmartThings OAuth 재인증 또는 x:devices:* 스코프 확인"));
    }
}
