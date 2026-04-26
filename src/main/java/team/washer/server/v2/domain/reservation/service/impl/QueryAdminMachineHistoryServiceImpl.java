package team.washer.server.v2.domain.reservation.service.impl;

import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.reservation.dto.response.AdminMachineHistoryGroupResDto;
import team.washer.server.v2.domain.reservation.dto.response.AdminMachineHistoryResDto;
import team.washer.server.v2.domain.reservation.dto.response.AdminMachineReservationItemResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.QueryAdminMachineHistoryService;

@Service
@RequiredArgsConstructor
public class QueryAdminMachineHistoryServiceImpl implements QueryAdminMachineHistoryService {

    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    @Override
    public AdminMachineHistoryResDto execute(String machineName) {
        final var reservations = reservationRepository.findAllByMachineNameFilter(machineName);

        final var machines = reservations.stream()
                .collect(Collectors.groupingBy(r -> r.getMachine().getName(),
                        Collectors.mapping(this::toItemDto, Collectors.toList())))
                .entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> new AdminMachineHistoryGroupResDto(entry.getKey(), entry.getValue())).toList();

        return new AdminMachineHistoryResDto(machines);
    }

    private AdminMachineReservationItemResDto toItemDto(Reservation reservation) {
        return new AdminMachineReservationItemResDto(reservation.getUser().getRoomNumber(),
                reservation.getReservedAt(),
                reservation.getActualCompletionTime(),
                reservation.getCancelledAt(),
                reservation.getStatus());
    }
}
