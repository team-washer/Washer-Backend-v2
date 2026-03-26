package team.washer.server.v2.domain.smartthings.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.exception.SmartThingsPermissionException;
import team.washer.server.v2.domain.smartthings.service.SendDeviceCommandService;
import team.washer.server.v2.domain.smartthings.service.ShutdownIdleMachinesService;
import team.washer.server.v2.global.thirdparty.discord.service.DiscordErrorNotificationService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShutdownIdleMachinesServiceImpl implements ShutdownIdleMachinesService {

    private final MachineRepository machineRepository;
    private final ReservationRepository reservationRepository;
    private final SendDeviceCommandService sendDeviceCommandService;

    @Autowired(required = false)
    private DiscordErrorNotificationService discordErrorNotificationService;

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.RESERVED,
            ReservationStatus.RUNNING);

    @Override
    @Transactional(readOnly = true)
    public void execute() {
        var machines = machineRepository.findAll();
        if (machines.isEmpty()) {
            return;
        }

        var offMachines = new ArrayList<String>();
        var failedMachines = new ArrayList<String>();
        var skippedCount = 0;

        for (var machine : machines) {
            try {
                var hasActiveReservation = reservationRepository.existsByMachineAndStatusIn(machine, ACTIVE_STATUSES);

                if (hasActiveReservation) {
                    skippedCount++;
                } else {
                    sendDeviceCommandService.execute(machine.getDeviceId(), SmartThingsCommandReqDto.powerOff());
                    offMachines.add(machine.getName());
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
                failedMachines.add(machine.getName());
                log.error("idle shutdown failed to turn off machine={} reason={}", machine.getName(), e.getMessage());
            }
        }

        if (!offMachines.isEmpty() || !failedMachines.isEmpty()) {
            log.info("idle shutdown batch done. turned_off={} {} skipped_active_reservation={} failed={}{}",
                    offMachines.size(),
                    offMachines,
                    skippedCount,
                    failedMachines.size(),
                    failedMachines.isEmpty() ? "" : " " + failedMachines);
        }
    }
}
