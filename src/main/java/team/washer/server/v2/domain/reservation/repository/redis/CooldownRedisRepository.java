package team.washer.server.v2.domain.reservation.repository.redis;

import org.springframework.data.repository.CrudRepository;

import team.washer.server.v2.domain.reservation.entity.redis.CooldownEntity;

public interface CooldownRedisRepository extends CrudRepository<CooldownEntity, Long> {
}
