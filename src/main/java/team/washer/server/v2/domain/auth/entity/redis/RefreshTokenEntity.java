package team.washer.server.v2.domain.auth.entity.redis;

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
@RedisHash(value = "auth:refresh-token:user")
public class RefreshTokenEntity {

    @Id
    private Long userId;

    private String token;

    @TimeToLive
    private Long ttl;
}
