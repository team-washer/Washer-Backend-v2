package team.washer.server.v2.domain.machine.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.*;

@Repository
public interface MachineRepository extends JpaRepository<Machine, Long> {

    Optional<Machine> findByDeviceId(String deviceId);

    Optional<Machine> findByName(String name);

    boolean existsByDeviceId(String deviceId);

    boolean existsByName(String name);

    List<Machine> findByType(MachineType type);

    List<Machine> findByFloor(Integer floor);

    List<Machine> findByStatus(MachineStatus status);

    List<Machine> findByAvailability(MachineAvailability availability);

    List<Machine> findByTypeAndFloor(MachineType type, Integer floor);

    @Query("SELECT m FROM Machine m WHERE m.type = :type AND m.floor = :floor AND m.position = :position AND m.number = :number")
    Optional<Machine> findByLocation(@Param("type") MachineType type,
            @Param("floor") Integer floor,
            @Param("position") Position position,
            @Param("number") Integer number);

    List<Machine> findByStatusAndAvailability(MachineStatus status, MachineAvailability availability);

    default List<Machine> findAllAvailableMachines() {
        return findByStatusAndAvailability(MachineStatus.NORMAL, MachineAvailability.AVAILABLE);
    }
}
