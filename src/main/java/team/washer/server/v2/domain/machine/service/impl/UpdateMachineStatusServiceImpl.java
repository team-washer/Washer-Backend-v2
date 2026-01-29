package team.washer.server.v2.domain.machine.service.impl;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.machine.dto.response.MachineStatusUpdateResDto;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.UpdateMachineStatusService;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Service
@RequiredArgsConstructor
public class UpdateMachineStatusServiceImpl implements UpdateMachineStatusService {

    private final MachineRepository machineRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    @Override
    public MachineStatusUpdateResDto execute(Long machineId, MachineStatus status) {
        final var machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new ExpectedException("기기를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        if (status == MachineStatus.MALFUNCTION) {
            machine.markAsMalfunction();
        } else if (status == MachineStatus.NORMAL) {
            machine.markAsNormal();

            // 활성 예약이 있는지 확인
            final var activeReservation = findActiveReservation(machine);
            if (activeReservation != null) {
                machine.markAsReserved();
            }
        }

        final var savedMachine = machineRepository.save(machine);

        return new MachineStatusUpdateResDto(savedMachine.getId(),
                savedMachine.getName(),
                savedMachine.getStatus(),
                savedMachine.getAvailability());
    }

    private Reservation findActiveReservation(Machine machine) {
        final var activeStatuses = List
                .of(ReservationStatus.RESERVED, ReservationStatus.CONFIRMED, ReservationStatus.RUNNING);
        final var activeReservations = reservationRepository.findByMachineAndStatusIn(machine, activeStatuses);

        return activeReservations.isEmpty() ? null : activeReservations.get(0);
    }
}
