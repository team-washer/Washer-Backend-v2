package team.washer.server.v2.domain.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.custom.UserRepositoryCustom;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {

    Optional<User> findByStudentId(String studentId);

    boolean existsByStudentId(String studentId);

    List<User> findByRoomNumber(String roomNumber);

    List<User> findByFloor(Integer floor);

    List<User> findByGrade(Integer grade);

    List<User> findByNameContaining(String name);

    @Query("SELECT u FROM User u WHERE u.floor = :floor AND u.grade = :grade")
    List<User> findByFloorAndGrade(@Param("floor") Integer floor, @Param("grade") Integer grade);

    @Query("SELECT u FROM User u WHERE u.penaltyCount > :threshold ORDER BY u.penaltyCount DESC")
    List<User> findUsersWithPenaltyAbove(@Param("threshold") Integer threshold);
}
