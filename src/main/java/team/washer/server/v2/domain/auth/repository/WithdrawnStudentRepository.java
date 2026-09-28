package team.washer.server.v2.domain.auth.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import team.washer.server.v2.domain.auth.entity.WithdrawnStudent;

@Repository
public interface WithdrawnStudentRepository extends JpaRepository<WithdrawnStudent, Long> {

    Optional<WithdrawnStudent> findByStudentId(String studentId);

    boolean existsByStudentIdAndExpiresAtAfter(String studentId, LocalDateTime now);
}
