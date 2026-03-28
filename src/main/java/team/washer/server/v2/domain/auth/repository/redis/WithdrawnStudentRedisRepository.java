package team.washer.server.v2.domain.auth.repository.redis;

import org.springframework.data.repository.CrudRepository;

import team.washer.server.v2.domain.auth.entity.redis.WithdrawnStudentEntity;

public interface WithdrawnStudentRedisRepository extends CrudRepository<WithdrawnStudentEntity, String> {
}
