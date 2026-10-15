package com.jinlong.framework.web.service;

import com.jinlong.common.constant.CacheConstants;
import com.jinlong.common.exception.ServiceException;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.List;

/** Redis 原子累加失败次数，避免并发请求覆盖计数。 */
@Component
public class SysPasswordService {
    private static final DefaultRedisScript<Long> FAILURE_SCRIPT = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]); end; return n", Long.class);
    private final StringRedisTemplate redis;
    private final String prefix;
    private final int maxRetryCount;
    private final int lockTime;

    public SysPasswordService(StringRedisTemplate redis, @Value("${token.key-prefix}") String prefix,
            @Value("${user.password.maxRetryCount:5}") int maxRetryCount,
            @Value("${user.password.lockTime:10}") int lockTime) {
        if (maxRetryCount < 1 || lockTime < 1) { throw new IllegalArgumentException("密码重试配置必须大于0"); }
        this.redis = redis; this.prefix = prefix; this.maxRetryCount = maxRetryCount; this.lockTime = lockTime;
    }

    public void checkRetryLimit(String username) {
        String count = redis.opsForValue().get(getCacheKey(username));
        if (count != null && Long.parseLong(count) >= maxRetryCount) {
            throw new ServiceException("密码错误次数过多，请 " + lockTime + " 分钟后重试", 429);
        }
    }

    public void recordFailure(String username) {
        redis.execute(FAILURE_SCRIPT, List.of(getCacheKey(username)), Long.toString(Duration.ofMinutes(lockTime).toMillis()));
    }

    public void clearLoginRecordCache(String username) { redis.delete(getCacheKey(username)); }

    private String getCacheKey(String username) {
        return prefix + ":" + CacheConstants.PWD_ERR_CNT_KEY + username.toLowerCase(Locale.ROOT);
    }
}
