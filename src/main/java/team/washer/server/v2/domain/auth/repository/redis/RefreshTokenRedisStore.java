package team.washer.server.v2.domain.auth.repository.redis;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.auth.entity.redis.RefreshTokenEntity;

@Component
@RequiredArgsConstructor
public class RefreshTokenRedisStore {
    private static final String USER_KEY_PREFIX = "auth:refresh-token:user:";
    private static final String TOKEN_INDEX_KEY_PREFIX = USER_KEY_PREFIX + "token:";
    private static final String USER_INDEX_KEY = "auth:refresh-token:user";
    private static final String ENTITY_INDEX_KEY_SUFFIX = ":idx";

    private static final String REPLACE_SCRIPT = """
            local primaryKey = KEYS[1]
            local tokenIndexKey = KEYS[2]
            local entityIndexKey = KEYS[3]
            local userIndexKey = KEYS[4]
            local expectedToken = ARGV[1]
            local tokenIndexPrefix = ARGV[2]
            local userId = ARGV[3]
            local newToken = ARGV[4]
            local ttl = ARGV[5]
            local entityClass = ARGV[6]
            local currentToken = redis.call('HGET', primaryKey, 'token')

            if expectedToken ~= '' and currentToken ~= expectedToken then
                return 0
            end

            local function removeUserFromTokenIndex(indexKey)
                redis.call('SREM', indexKey, userId)
                if redis.call('SCARD', indexKey) == 0 then
                    redis.call('DEL', indexKey)
                end
            end

            local existingIndexes = redis.call('SMEMBERS', entityIndexKey)
            for _, existingIndex in ipairs(existingIndexes) do
                if existingIndex ~= tokenIndexKey then
                    removeUserFromTokenIndex(existingIndex)
                end
            end

            if currentToken and tokenIndexPrefix .. currentToken ~= tokenIndexKey then
                removeUserFromTokenIndex(tokenIndexPrefix .. currentToken)
            end

            redis.call('DEL', entityIndexKey)
            redis.call('HSET', primaryKey, 'userId', userId, 'token', newToken, 'ttl', ttl, '_class', entityClass)
            redis.call('EXPIRE', primaryKey, ttl)
            redis.call('SADD', userIndexKey, userId)
            redis.call('SADD', tokenIndexKey, userId)
            redis.call('SADD', entityIndexKey, tokenIndexKey)
            return 1
            """;

    private static final RedisScript<Long> REPLACE_REDIS_SCRIPT = new DefaultRedisScript<>(REPLACE_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;

    public void replace(final Long userId, final String newToken, final Long ttl) {
        if (!Long.valueOf(1L).equals(execute(userId, "", newToken, ttl))) {
            throw new IllegalStateException("Refresh Token 저장에 실패했습니다.");
        }
    }

    public boolean rotate(final Long userId, final String expectedToken, final String newToken, final Long ttl) {
        return Long.valueOf(1L).equals(execute(userId, expectedToken, newToken, ttl));
    }

    private Long execute(final Long userId, final String expectedToken, final String newToken, final Long ttl) {
        final var primaryKey = userKey(userId);
        final var tokenIndexKey = tokenIndexKey(newToken);
        final var entityIndexKey = primaryKey + ENTITY_INDEX_KEY_SUFFIX;

        return redisTemplate.execute(REPLACE_REDIS_SCRIPT,
                List.of(primaryKey, tokenIndexKey, entityIndexKey, USER_INDEX_KEY),
                expectedToken,
                TOKEN_INDEX_KEY_PREFIX,
                userId.toString(),
                newToken,
                ttl.toString(),
                RefreshTokenEntity.class.getName());
    }

    static String userKey(final Long userId) {
        return USER_KEY_PREFIX + userId;
    }

    static String tokenIndexKey(final String token) {
        return TOKEN_INDEX_KEY_PREFIX + token;
    }
}
