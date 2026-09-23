package team.washer.server.v2.domain.reservation.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.ProcessReservationLifecycleService;
import team.washer.server.v2.domain.reservation.service.impl.ReservationLifecycleProcessor.CompletedMachine;
import team.washer.server.v2.domain.reservation.service.impl.ReservationLifecycleProcessor.RunningTarget;
import team.washer.server.v2.domain.reservation.support.LongRunningReservationMonitor;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.exception.SmartThingsPermissionException;
import team.washer.server.v2.domain.smartthings.support.DeviceShutdownSupport;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.global.thirdparty.discord.service.DiscordErrorNotificationService;
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

    private final ReservationLifecycleProcessor reservationLifecycleProcessor;
    private final DeviceStatusQuerySupport deviceStatusQuerySupport;
    private final DeviceShutdownSupport deviceShutdownSupport;
    private final LongRunningReservationMonitor longRunningReservationMonitor;

    @Autowired(required = false)
    private DiscordErrorNotificationService discordErrorNotificationService;

    @Override
    public void execute() {
        processReservedToRunning();

        var runningTargets = reservationLifecycleProcessor.findRunningTargets();
        longRunningReservationMonitor.retainOnly(runningTargets.stream().map(RunningTarget::reservationId).toList());
        processRunningToCompleted(runningTargets);
        reportLongRunningReservations(runningTargets);
    }

    private void processReservedToRunning() {
        for (var target : reservationLifecycleProcessor.findReservedTargets()) {
            try {
                var status = deviceStatusQuerySupport.queryDeviceStatus(target.deviceId());
                reservationLifecycleProcessor.processReservedToRunning(target.reservationId(), status);
            } catch (Exception e) {
                log.error("Failed to process RESERVED reservation: {}", target.reservationId(), e);
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
                log.warn("running reservation status query failed reservationId={} deviceId={} consecutiveFailures={} "
                        + "reason={}", target.reservationId(), target.deviceId(), failures, e.getMessage());
                continue;
            }
            try {
                var completedMachine = reservationLifecycleProcessor.processRunningToCompleted(target.reservationId(),
                        status);
                if (completedMachine.isPresent() && !shutdownCompletedMachine(completedMachine.get(), status)) {
                    return;
                }
            } catch (Exception e) {
                log.error("Failed to process RUNNING reservation: {}", target.reservationId(), e);
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
            log.error("long running reservation report failed", e);
        }
    }

    /**
     * 완료된 예약의 기기 전원을 차단한다.
     *
     * @return 남은 예약 처리를 계속해도 되면 {@code true}, SmartThings 권한 오류로 이번 주기를 중단해야 하면
     *         {@code false}
     */
    private boolean shutdownCompletedMachine(CompletedMachine completedMachine, SmartThingsDeviceStatusResDto status) {
        try {
            deviceShutdownSupport.shutdownAfterCompletion(completedMachine.machineName(),
                    completedMachine.deviceId(),
                    completedMachine.isWasher(),
                    status);
            return true;
        } catch (SmartThingsPermissionException e) {
            log.warn(
                    "power off after completion SmartThings permission error detected, stopping batch. machine={} deviceId={} reason={}",
                    completedMachine.machineName(),
                    completedMachine.deviceId(),
                    e.getMessage());
            notifyPermissionError(completedMachine, e);
            return false;
        } catch (Exception e) {
            log.error("power off after completion failed machine={} deviceId={} reason={}",
                    completedMachine.machineName(),
                    completedMachine.deviceId(),
                    e.getMessage());
            return true;
        }
    }

    private void notifyPermissionError(CompletedMachine completedMachine, SmartThingsPermissionException e) {
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
