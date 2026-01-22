package team.washer.server.v2.domain.reservation.entity.redis;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "reservation:penalty:user")
public class PenaltyEntity {

    @Id
    private Long userId;

    private LocalDateTime expiryTime;

    @TimeToLive
    private Long ttl;
}
