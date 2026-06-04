package team.washer.server.v2.domain.machine.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.*;
import team.washer.server.v2.domain.machine.repository.custom.MachineRepositoryCustom;

@Repository
public interface MachineRepository extends JpaRepository<Machine, Long>, MachineRepositoryCustom {

    /**
     * 비관적 쓰기 락을 걸어 기기를 조회합니다. 동일 기기에 대한 동시 예약 생성을 직렬화하기 위해 사용합니다.
     *
     * @param id
     *            기기 ID
     * @return 락이 걸린 기기
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Machine m WHERE m.id = :id")
    Optional<Machine> findByIdForUpdate(@Param("id") Long id);

    Optional<Machine> findByDeviceId(String deviceId);

    Optional<Machine> findByName(String name);

    boolean existsByDeviceId(String deviceId);

    boolean existsByName(String name);

    List<Machine> findByType(MachineType type);

    List<Machine> findByFloor(Integer floor);

    List<Machine> findByStatus(MachineStatus status);

    long countByStatus(MachineStatus status);

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
