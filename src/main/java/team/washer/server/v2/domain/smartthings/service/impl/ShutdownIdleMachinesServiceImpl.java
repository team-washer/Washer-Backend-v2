package team.washer.server.v2.domain.smartthings.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.service.SendDeviceCommandService;
import team.washer.server.v2.domain.smartthings.service.ShutdownIdleMachinesService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShutdownIdleMachinesServiceImpl implements ShutdownIdleMachinesService {

    private final MachineRepository machineRepository;
    private final ReservationRepository reservationRepository;
    private final SendDeviceCommandService sendDeviceCommandService;

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.CONFIRMED,
            ReservationStatus.RUNNING);

    @Override
    @Transactional(readOnly = true)
    public void execute() {
        var machines = machineRepository.findAll();

        for (var machine : machines) {
            try {
                var hasActiveReservation = reservationRepository.existsByMachineAndStatusIn(machine, ACTIVE_STATUSES);

                if (!hasActiveReservation) {
                    sendDeviceCommandService.execute(machine.getDeviceId(), SmartThingsCommandReqDto.powerOff());
                    log.info("Powered off idle machine: {}", machine.getName());
                }
            } catch (Exception e) {
                log.error("Failed to shutdown idle machine: {}", machine.getName(), e);
            }
        }
    }
}
