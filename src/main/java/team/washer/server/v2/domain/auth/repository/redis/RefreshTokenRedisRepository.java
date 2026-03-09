package team.washer.server.v2.domain.auth.repository.redis;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import team.washer.server.v2.domain.auth.entity.redis.RefreshTokenEntity;

public interface RefreshTokenRedisRepository extends CrudRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByToken(String token);
}
