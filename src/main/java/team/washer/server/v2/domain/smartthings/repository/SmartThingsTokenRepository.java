package team.washer.server.v2.domain.smartthings.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;

@Repository
public interface SmartThingsTokenRepository extends JpaRepository<SmartThingsToken, Long> {

    /**
     * Get the singleton token with pessimistic write lock for thread-safe updates
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM SmartThingsToken t WHERE t.id = :id")
    Optional<SmartThingsToken> findByIdWithLock(Long id);

    /**
     * Get the singleton token
     */
    default Optional<SmartThingsToken> findSingletonToken() {
        return findById(SmartThingsToken.SINGLETON_ID);
    }

    /**
     * Get the singleton token with lock for updates
     */
    default Optional<SmartThingsToken> findSingletonTokenWithLock() {
        return findByIdWithLock(SmartThingsToken.SINGLETON_ID);
    }
}
