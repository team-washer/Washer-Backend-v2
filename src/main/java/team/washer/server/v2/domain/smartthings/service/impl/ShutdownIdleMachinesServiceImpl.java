package team.washer.server.v2.domain.smartthings.service.impl;

import java.util.ArrayList;
import java.util.List;
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
import team.washer.server.v2.global.thirdparty.discord.service.DiscordErrorNotificationService;

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

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.RESERVED,
            ReservationStatus.RUNNING);

    @Override
    public void execute() {
        var machines = machineRepository.findAll();
        if (machines.isEmpty()) {
            return;
        }

        var activeMachineIds = Set.copyOf(reservationRepository.findMachineIdsByStatusIn(ACTIVE_STATUSES));

        var idleCandidates = machines.stream().filter(machine -> !activeMachineIds.contains(machine.getId())).toList();
        var skippedActiveCount = machines.size() - idleCandidates.size();
        if (idleCandidates.isEmpty()) {
            return;
        }

        var statusMap = deviceStatusQuerySupport
                .queryAllDevicesStatus(idleCandidates.stream().map(Machine::getDeviceId).toList());

        var poweredOff = new ArrayList<String>();
        var unauthorizedStopped = new ArrayList<String>();
        var skippedOperating = new ArrayList<String>();
        var failed = new ArrayList<String>();

        for (var machine : idleCandidates) {
            try {
                var status = statusMap.get(machine.getDeviceId());
                var result = deviceShutdownSupport.shutdown(machine, status);
                switch (result) {
                    case POWERED_OFF -> poweredOff.add(machine.getName());
                    case STOPPED -> {
                        unauthorizedStopped.add(machine.getName());
                        log.warn("unauthorized usage detected, machine safely stopped machine={} deviceId={}",
                                machine.getName(),
                                machine.getDeviceId());
                    }
                    case SKIPPED_OPERATING -> skippedOperating.add(machine.getName());
                    case SKIPPED_UNKNOWN -> {
                        // 상태 불명, 안전을 위해 종료하지 않음
                    }
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

        if (!poweredOff.isEmpty() || !unauthorizedStopped.isEmpty() || !skippedOperating.isEmpty()
                || !failed.isEmpty()) {
            log.info(
                    "idle shutdown batch done. powered_off={} {} unauthorized_stopped={} {} skipped_operating={} {} skipped_active_reservation={} failed={}{}",
                    poweredOff.size(),
                    poweredOff,
                    unauthorizedStopped.size(),
                    unauthorizedStopped,
                    skippedOperating.size(),
                    skippedOperating,
                    skippedActiveCount,
                    failed.size(),
                    failed.isEmpty() ? "" : " " + failed);
        }
    }
}
