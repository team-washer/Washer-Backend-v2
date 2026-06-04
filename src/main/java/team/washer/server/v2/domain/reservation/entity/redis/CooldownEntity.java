package team.washer.server.v2.domain.reservation.entity.redis;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예약 취소 후 5분 재예약 쿨다운 Redis 엔티티. 세탁기/건조기를 분리하기 위해 키는
 * {@code {userId}:{machineType}} 형식을 사용합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "reservation:cooldown:user")
public class CooldownEntity {

    /** {@code {userId}:{machineType}} 형식의 복합 키 */
    @Id
    private String id;

    @TimeToLive
    private Long ttl;
}
