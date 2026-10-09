package com.jinlong.personalwebsitesys.admin.security.service;

import com.jinlong.personalwebsitesys.common.exception.ServiceException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 单实例按直接来源 IP 限流，不信任客户端提供的 X-Forwarded-For。 */
@Component
public class LoginRateLimiter {
    private static final long WINDOW_MS = 5 * 60 * 1000L;
    private final Map<String, Window> attempts = new HashMap<>();

    public synchronized void check(String ip) {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> now - e.getValue().start >= WINDOW_MS);
        Window window = attempts.get(ip);
        if (window == null) {
            if (attempts.size() >= 10000) throw limited();
            window = new Window(now);
            attempts.put(ip, window);
        }
        if (++window.count > 20) throw limited();
    }

    private static ServiceException limited() {
        return new ServiceException(429, "TOO_MANY_REQUESTS", "尝试次数过多，请稍后再试");
    }

    private static class Window {
        final long start;
        int count;
        Window(long start) { this.start = start; }
    }
}
