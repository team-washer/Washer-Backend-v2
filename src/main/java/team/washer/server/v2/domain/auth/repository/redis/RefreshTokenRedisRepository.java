package team.washer.server.v2.domain.auth.repository.redis;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import team.washer.server.v2.domain.auth.entity.redis.RefreshTokenEntity;

@Repository
public interface RefreshTokenRedisRepository extends CrudRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByToken(String token);
}
