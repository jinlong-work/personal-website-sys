package com.jinlong.personalwebsitesys.admin.security.store;

import com.jinlong.personalwebsitesys.admin.config.TokenProperties;
import com.jinlong.personalwebsitesys.admin.security.domain.LoginToken;
import com.jinlong.personalwebsitesys.common.exception.ServiceException;
import com.jinlong.personalwebsitesys.framework.redis.RedisScriptExecutor;
import java.util.List;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 按用户保存登录Hash和过期时间索引，Lua保证查找、续期及撤销的原子性。
 * 两个键使用同一个用户hash tag；注销所有登录只删除该用户的两个键。
 */
@Component
public class RedisLoginTokenStore implements LoginTokenStore {
    // 字段值格式：密码指纹|滑动截止时间|绝对截止时间，不保存密码或完整JWT。
    private static final String HELPERS = """
            local function prune(now)
                local expired = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', now)
                for _, id in ipairs(expired) do
                    redis.call('HDEL', KEYS[1], id)
                    redis.call('ZREM', KEYS[2], id)
                end
            end
            local function expireKeys()
                local latest = redis.call('ZREVRANGE', KEYS[2], 0, 0, 'WITHSCORES')
                if #latest == 0 then
                    redis.call('DEL', KEYS[1], KEYS[2])
                else
                    redis.call('PEXPIREAT', KEYS[1], latest[2])
                    redis.call('PEXPIREAT', KEYS[2], latest[2])
                end
            end
            local function readActive(id, now)
                local value = redis.call('HGET', KEYS[1], id)
                if not value then return nil end
                local fingerprint, expiresAt, maxExpiresAt = string.match(value, '^([a-f0-9]+)|(%d+)|(%d+)$')
                if not fingerprint then error('Invalid login cache format') end
                if tonumber(expiresAt) <= now or tonumber(maxExpiresAt) <= now then
                    redis.call('HDEL', KEYS[1], id)
                    redis.call('ZREM', KEYS[2], id)
                    return nil
                end
                return value, fingerprint, tonumber(expiresAt), tonumber(maxExpiresAt)
            end
            """;

    private static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>(HELPERS + """
            local now = tonumber(ARGV[5])
            prune(now)
            if tonumber(ARGV[3]) <= now or tonumber(ARGV[4]) <= now then return 0 end
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2] .. '|' .. ARGV[3] .. '|' .. ARGV[4])
            redis.call('ZADD', KEYS[2], ARGV[3], ARGV[1])
            expireKeys()
            return 1
            """, Long.class);

    private static final DefaultRedisScript<String> FIND = new DefaultRedisScript<>(HELPERS + """
            local value = readActive(ARGV[1], tonumber(ARGV[2]))
            return value
            """, String.class);

    private static final DefaultRedisScript<String> RENEW = new DefaultRedisScript<>(HELPERS + """
            local now = tonumber(ARGV[2])
            prune(now)
            local value, fingerprint, expiresAt, maxExpiresAt = readActive(ARGV[1], now)
            if not value then return nil end
            if expiresAt - now <= tonumber(ARGV[4]) then
                expiresAt = math.min(now + tonumber(ARGV[3]), maxExpiresAt)
                value = fingerprint .. '|' .. string.format('%.0f', expiresAt) .. '|' .. string.format('%.0f', maxExpiresAt)
                redis.call('HSET', KEYS[1], ARGV[1], value)
                redis.call('ZADD', KEYS[2], expiresAt, ARGV[1])
            end
            expireKeys()
            return value
            """, String.class);

    private static final DefaultRedisScript<Long> REMOVE = new DefaultRedisScript<>(HELPERS + """
            local removed = redis.call('HDEL', KEYS[1], ARGV[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            expireKeys()
            return removed
            """, Long.class);

    private static final DefaultRedisScript<Long> REMOVE_USER = new DefaultRedisScript<>(
            "return redis.call('DEL', KEYS[1], KEYS[2])", Long.class);

    private final RedisScriptExecutor executor;
    private final String keyPrefix;

    public RedisLoginTokenStore(RedisScriptExecutor executor, TokenProperties properties) {
        this.executor = executor;
        keyPrefix = properties.getKeyPrefix();
        if (keyPrefix == null || !keyPrefix.matches("[A-Za-z0-9:_-]{1,128}")) {
            throw new IllegalStateException("token.key-prefix仅允许字母、数字、冒号、下划线和短横线，长度1至128");
        }
    }

    @Override
    public void save(LoginToken token) {
        Long result = executor.execute(SAVE, keys(token.getUserId()), token.getId(), token.getPasswordFingerprint(),
                Long.toString(token.getExpiresAt()), Long.toString(token.getMaxExpiresAt()),
                Long.toString(System.currentTimeMillis()));
        if (!Long.valueOf(1).equals(result)) {
            throw new ServiceException(503, "AUTH_CACHE_UNAVAILABLE", "登录状态保存失败，请重新登录");
        }
    }

    @Override
    public LoginToken findActive(long userId, String id, long now) {
        return decode(userId, id, executor.execute(FIND, keys(userId), id, Long.toString(now)));
    }

    @Override
    public LoginToken renewIfActive(LoginToken token, long now, long expireMillis, long thresholdMillis) {
        String value = executor.execute(RENEW, keys(token.getUserId()), token.getId(), Long.toString(now),
                Long.toString(expireMillis), Long.toString(thresholdMillis));
        return decode(token.getUserId(), token.getId(), value);
    }

    @Override
    public void remove(LoginToken token) {
        executor.execute(REMOVE, keys(token.getUserId()), token.getId());
    }

    @Override
    public void removeByUser(long userId) {
        executor.execute(REMOVE_USER, keys(userId));
    }

    private List<String> keys(long userId) {
        String userPrefix = keyPrefix + ":{" + userId + "}:";
        return List.of(userPrefix + "tokens", userPrefix + "expires");
    }

    private static LoginToken decode(long userId, String id, String value) {
        if (value == null) return null;
        try {
            String[] fields = value.split("\\|", -1);
            if (fields.length != 3 || !fields[0].matches("[a-f0-9]{64}")) throw new IllegalArgumentException();
            long expiresAt = Long.parseLong(fields[1]);
            long maxExpiresAt = Long.parseLong(fields[2]);
            if (expiresAt <= 0 || maxExpiresAt < expiresAt) throw new IllegalArgumentException();
            return new LoginToken(id, userId, fields[0], expiresAt, maxExpiresAt);
        } catch (IllegalArgumentException ex) {
            // 缓存损坏时拒绝鉴权，不输出缓存中的指纹或其他值。
            throw new ServiceException(503, "AUTH_CACHE_UNAVAILABLE", "登录缓存数据异常，请联系维护人员");
        }
    }
}
