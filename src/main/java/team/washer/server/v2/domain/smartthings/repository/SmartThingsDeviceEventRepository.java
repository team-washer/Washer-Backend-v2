package team.washer.server.v2.domain.smartthings.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import team.washer.server.v2.domain.smartthings.entity.SmartThingsDeviceEvent;

public interface SmartThingsDeviceEventRepository extends JpaRepository<SmartThingsDeviceEvent, Long> {

    boolean existsByEventId(String eventId);

    @Modifying
    @Query("delete from SmartThingsDeviceEvent e where e.createdAt < :threshold")
    int deleteCreatedBefore(@Param("threshold") LocalDateTime threshold);
}
