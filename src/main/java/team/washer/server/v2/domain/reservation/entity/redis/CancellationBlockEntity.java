package team.washer.server.v2.domain.reservation.entity.redis;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 48시간 취소 횟수 초과 시 호실 단위 예약 차단 Redis 엔티티.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "reservation:block:room")
public class CancellationBlockEntity {

    @Id
    private String roomNumber;

    @TimeToLive
    private Long ttl;
}
