package team.washer.server.v2.domain.reservation.entity.redis;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 타임아웃 첫 번째 경고 기록 Redis 엔티티 (TTL 7일).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "reservation:warning:user")
public class TimeoutWarningEntity {

    @Id
    private Long userId;

    @TimeToLive
    private Long ttl;
}
