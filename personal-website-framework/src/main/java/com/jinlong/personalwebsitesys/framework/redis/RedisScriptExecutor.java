package com.jinlong.personalwebsitesys.framework.redis;

import com.jinlong.personalwebsitesys.common.exception.ServiceException;
import com.jinlong.personalwebsitesys.common.utils.SafeLogUtils;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** 统一使用字符串序列化执行Lua，不输出键、参数、连接密码或原始异常消息。 */
@Component
public class RedisScriptExecutor {
    private static final Logger log = LoggerFactory.getLogger(RedisScriptExecutor.class);
    private final StringRedisTemplate redisTemplate;

    public RedisScriptExecutor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public <T> T execute(DefaultRedisScript<T> script, List<String> keys, String... arguments) {
        try {
            return redisTemplate.execute(script, keys, (Object[]) arguments);
        } catch (DataAccessException ex) {
            log.error("Redis operation failed{}", SafeLogUtils.stackTrace(ex));
            throw new ServiceException(503, "AUTH_CACHE_UNAVAILABLE", "登录缓存服务暂时不可用，请稍后重试");
        }
    }
}
