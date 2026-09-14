package team.washer.server.v2.domain.user.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.custom.UserRepositoryCustom;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {

    /**
     * 사용자를 비관적 쓰기 락으로 조회합니다. 사용자 삭제와 예약 생성을 직렬화하기 위해 사용하며, 락 순서는 사용자 → 기기 → 예약입니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    Optional<User> findByStudentId(String studentId);

    boolean existsByStudentId(String studentId);

    List<User> findByRoomNumber(String roomNumber);

    @Query(value = """
            SELECT u.id
            FROM users u
            WHERE u.room_number = (
                SELECT target.room_number
                FROM users target
                WHERE target.id = :userId
            )
            ORDER BY u.id
            FOR UPDATE
            """, nativeQuery = true)
    List<Long> findRoomUserIdsByUserIdForUpdate(@Param("userId") Long userId);

    List<User> findByFloor(Integer floor);

    List<User> findByGrade(Integer grade);

    List<User> findByNameContaining(String name);

    long countByRole(UserRole role);

    @Query("SELECT u FROM User u WHERE u.floor = :floor AND u.grade = :grade")
    List<User> findByFloorAndGrade(@Param("floor") Integer floor, @Param("grade") Integer grade);

    @Query("SELECT u FROM User u WHERE u.penaltyCount > :threshold ORDER BY u.penaltyCount DESC")
    List<User> findUsersWithPenaltyAbove(@Param("threshold") Integer threshold);

    @Query("SELECT COUNT(u) FROM User u WHERE u.lastCancellationAt IS NOT NULL AND u.lastCancellationAt >= :threshold")
    long countSuspendedStudents(@Param("threshold") LocalDateTime threshold);
}
