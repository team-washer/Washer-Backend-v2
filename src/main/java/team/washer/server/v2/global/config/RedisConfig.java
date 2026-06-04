package team.washer.server.v2.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisKeyValueAdapter.EnableKeyspaceEvents;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

/**
 * Redis 리포지토리 설정.
 *
 * <p>
 * {@code @Indexed} 보조 인덱스를 사용하는 엔티티가 TTL로 만료될 때, keyspace 만료 이벤트를 수신하여 보조 인덱스를
 * 함께 정리하도록 한다. 이 설정이 없으면 만료된 엔티티의 보조 인덱스 Set이 정리되지 않아 메모리 누수가 발생할 수 있다.
 */
@Configuration
@EnableRedisRepositories(basePackages = "team.washer.server.v2", enableKeyspaceEvents = EnableKeyspaceEvents.ON_STARTUP)
public class RedisConfig {
}
