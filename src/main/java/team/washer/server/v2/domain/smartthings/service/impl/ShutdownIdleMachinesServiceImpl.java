package team.washer.server.v2.domain.smartthings.service.impl;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.exception.SmartThingsPermissionException;
import team.washer.server.v2.domain.smartthings.service.ShutdownIdleMachinesService;
import team.washer.server.v2.domain.smartthings.support.DeviceShutdownSupport;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.global.common.constants.ReservationConstants;
import team.washer.server.v2.global.thirdparty.discord.service.DiscordErrorNotificationService;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * 활성 예약이 없는 기기의 전원을 차단한다.
 *
 * <p>
 * 작동 중인 기기도 차단 대상이다. 예외는 통세척 중인 기기와, 예약 완료 후 배수 유예 시간이 지나지 않은 작동 중 세탁기다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShutdownIdleMachinesServiceImpl implements ShutdownIdleMachinesService {

    private final MachineRepository machineRepository;
    private final ReservationRepository reservationRepository;
    private final DeviceStatusQuerySupport deviceStatusQuerySupport;
    private final DeviceShutdownSupport deviceShutdownSupport;

    @Autowired(required = false)
    private DiscordErrorNotificationService discordErrorNotificationService;

    @Override
    public void execute() {
        var machines = machineRepository.findAll();
        if (machines.isEmpty()) {
            return;
        }

        // 만료 예약만 남은 기기는 실제로 아무도 쓰지 않으므로 유휴 전원 차단 대상에 포함한다
        var activeMachineIds = Set.copyOf(reservationRepository.findCurrentlyActiveMachineIds());

        // 통세척은 예약 없이 기기를 작동시키므로 전원 차단 대상에서 제외한다
        var idleCandidates = machines.stream()
                .filter(machine -> !activeMachineIds.contains(machine.getId()) && !machine.isCleaning()).toList();
        var skippedCount = machines.size() - idleCandidates.size();
        if (idleCandidates.isEmpty()) {
            return;
        }

        var statusMap = deviceStatusQuerySupport
                .queryAllDevicesStatus(idleCandidates.stream().map(Machine::getDeviceId).toList());

        var poweredOff = new ArrayList<String>();
        var operatingPoweredOff = new ArrayList<String>();
        var skippedDrainGrace = new ArrayList<String>();
        var failed = new ArrayList<String>();

        for (var machine : idleCandidates) {
            try {
                var status = statusMap.get(machine.getDeviceId());
                var isOperating = deviceShutdownSupport.isOperating(status, machine.isWasher());
                if (isOperating && machine.isWasher() && isWithinDrainGrace(machine)) {
                    skippedDrainGrace.add(machine.getName());
                    continue;
                }

                var result = deviceShutdownSupport.shutdown(machine, status);
                if (result != DeviceShutdownSupport.ShutdownResult.POWERED_OFF) {
                    continue;
                }
                if (isOperating) {
                    operatingPoweredOff.add(machine.getName());
                    log.warn("operating device without active reservation powered off machine={} deviceId={}",
                            machine.getName(),
                            machine.getDeviceId());
                } else {
                    poweredOff.add(machine.getName());
                }
            } catch (SmartThingsPermissionException e) {
                log.warn("idle shutdown SmartThings permission error detected, stopping batch. machine={} reason={}",
                        machine.getName(),
                        e.getMessage());
                if (discordErrorNotificationService != null) {
                    discordErrorNotificationService.notifyError(e,
                            "유휴 기기 종료 스케줄러 - SmartThings 권한 오류",
                            Map.of("감지된 기기",
                                    machine.getName(),
                                    "조치 필요",
                                    "SmartThings OAuth 재인증 또는 x:devices:* 스코프 확인"));
                }
                break;
            } catch (Exception e) {
                failed.add(machine.getName());
                log.error("idle shutdown failed to turn off machine={} reason={}", machine.getName(), e.getMessage());
            }
        }

        if (!poweredOff.isEmpty() || !operatingPoweredOff.isEmpty() || !skippedDrainGrace.isEmpty()
                || !failed.isEmpty()) {
            log.info(
                    "idle shutdown batch done. powered_off={} {} operating_powered_off={} {} skipped_drain_grace={} {} skipped_active_or_cleaning={} failed={}{}",
                    poweredOff.size(),
                    poweredOff,
                    operatingPoweredOff.size(),
                    operatingPoweredOff,
                    skippedDrainGrace.size(),
                    skippedDrainGrace,
                    skippedCount,
                    failed.size(),
                    failed.isEmpty() ? "" : " " + failed);
        }
    }

    /**
     * 세탁기의 마지막 완료 예약 기준 시각에 배수 유예 시간을 더한 시각이 아직 지나지 않았는지 판정한다. 기준 시각은 완료 예정 시각이며,
     * 값이 없으면 실제 완료 시각이다. SmartThings가 현재 보고하는 완료 시각은 사이클 종료 후 다음 사이클 기준으로 되돌아가므로
     * 사용하지 않는다.
     */
    private boolean isWithinDrainGrace(Machine machine) {
        return reservationRepository
                .findFirstByMachineIdAndStatusOrderByActualCompletionTimeDesc(machine.getId(),
                        ReservationStatus.COMPLETED)
                .map(reservation -> reservation.getExpectedCompletionTime() != null
                        ? reservation.getExpectedCompletionTime()
                        : reservation.getActualCompletionTime())
                .map(baseTime -> DateTimeUtil.nowInKorea()
                        .isBefore(baseTime.plusMinutes(ReservationConstants.WASHER_DRAIN_GRACE_MINUTES)))
                .orElse(false);
    }
}
