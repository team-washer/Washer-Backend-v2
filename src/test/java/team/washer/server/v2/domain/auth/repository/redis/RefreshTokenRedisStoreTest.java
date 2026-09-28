package team.washer.server.v2.domain.auth.repository.redis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRedisStoreTest {
    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void redisFailureIsPropagatedWithoutAnAuthenticationSuccess() {
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .willThrow(new RedisConnectionFailureException("Redis connection failed"));
        final var store = new RefreshTokenRedisStore(redisTemplate);

        assertThatThrownBy(() -> store.rotate(1L, "old-token", "new-token", 3600L))
                .isInstanceOf(RedisConnectionFailureException.class);
    }
}
