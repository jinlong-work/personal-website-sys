package com.jinlong.web;

import com.jinlong.common.core.domain.entity.SysUser;
import com.jinlong.common.core.domain.model.LoginUser;
import com.jinlong.common.core.redis.RedisCache;
import com.jinlong.common.exception.ServiceException;
import com.jinlong.framework.config.RedisConfig;
import com.jinlong.framework.web.service.SysPasswordService;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.*;

/** 可选本机Redis集成验证：mvn -Dtest.redis.port=端口 test。不要传入业务Redis端口。 */
@EnabledIfSystemProperty(named = "test.redis.port", matches = "[0-9]+")
class RedisLoginCacheTests {
    private LettuceConnectionFactory factory;
    private RedisCache cache;
    private StringRedisTemplate strings;
    private String prefix;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getProperty("test.redis.port")));
        factory.afterPropertiesSet(); factory.start();
        cache = new RedisCache(); cache.redisTemplate = new RedisConfig().redisTemplate(factory);
        strings = new StringRedisTemplate(factory);
        prefix = "test-only:" + UUID.randomUUID();
    }

    @AfterEach
    void close() {
        var keys = strings.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) { strings.delete(keys); }
        factory.destroy();
    }

    @Test
    void serializedSessionExpiresAndCannotBeResurrected() {
        SysUser user = new SysUser(7L); user.setUserName("cache-test");
        LoginUser session = new LoginUser(7L, 100L, user, Set.of("website:read"));
        cache.setCacheObject(prefix + ":session", session, Duration.ofSeconds(30));
        LoginUser restored = cache.getCacheObject(prefix + ":session");
        assertThat(restored.getUserId()).isEqualTo(7L);
        assertThat(restored.getPermissions()).containsExactly("website:read");
        assertThat(strings.getExpire(prefix + ":session")).isBetween(1L, 30L);
        cache.deleteObject(prefix + ":session");
        assertThat(cache.setCacheObjectIfPresent(prefix + ":session", restored, Duration.ofSeconds(30))).isFalse();
        assertThat((Object) cache.getCacheObject(prefix + ":session")).isNull();
    }

    @Test
    void captchaConsumptionIsAtomicUnderConcurrentRequests() throws Exception {
        cache.setCacheObject(prefix + ":captcha", "8", Duration.ofSeconds(30));
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = IntStream.range(0, 20).mapToObj(index -> (java.util.concurrent.Callable<String>)
                    () -> cache.getAndDeleteCacheObject(prefix + ":captcha")).toList();
            long successful = 0;
            for (var result : executor.invokeAll(calls)) { if ("8".equals(result.get())) { successful++; } }
            assertThat(successful).isEqualTo(1);
        }
    }

    @Test
    void passwordRetryLuaCountsFailuresAndClearsAfterSuccess() {
        SysPasswordService service = new SysPasswordService(strings, prefix, 5, 10);
        for (int count = 0; count < 5; count++) { service.recordFailure(count % 2 == 0 ? "test-user" : "TEST-USER"); }
        assertThatThrownBy(() -> service.checkRetryLimit("TEST-USER")).isInstanceOf(ServiceException.class);
        assertThat(strings.getExpire(prefix + ":pwd_err_cnt:test-user")).isBetween(1L, 600L);
        service.clearLoginRecordCache("TEST-USER");
        assertThatCode(() -> service.checkRetryLimit("test-user")).doesNotThrowAnyException();
    }
}
