package team.washer.server.v2.domain.machine.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.dto.response.DeleteMachineResDto;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.DeleteMachineService;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;

@Service
@RequiredArgsConstructor
public class DeleteMachineServiceImpl implements DeleteMachineService {

    private final MachineRepository machineRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    @Override
    public DeleteMachineResDto execute(Long machineId) {
        final var machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new ExpectedException("기기를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        if (reservationRepository.findActiveReservationByMachineId(machineId).isPresent()) {
            throw new ExpectedException("활성 예약이 존재하는 기기는 삭제할 수 없습니다", HttpStatus.BAD_REQUEST);
        }

        final var id = machine.getId();
        final var name = machine.getName();
        machineRepository.delete(machine);

        return new DeleteMachineResDto(id, name);
    }
}
