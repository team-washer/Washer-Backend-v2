package team.washer.server.v2.domain.admin.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.washer.server.v2.domain.admin.entity.WashingBan;

public interface WashingBanRepository extends JpaRepository<WashingBan, Long> {
    boolean existsByRoomNumber(String roomNumber);
    Optional<WashingBan> findByRoomNumber(String roomNumber);
}
