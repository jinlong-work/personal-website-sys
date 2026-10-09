package com.jinlong.personalwebsitesys.framework.security;

import com.jinlong.personalwebsitesys.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** CORS 与写请求来源检查共用可信来源配置。 */
@Component
public class OriginPolicy {
    private final Set<String> origins;

    public OriginPolicy(@Value("${app.auth.allowed-origins}") String origins) {
        this.origins = Arrays.stream(origins.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public String[] allowedOrigins() { return origins.toArray(String[]::new); }

    public void checkWriteRequest(HttpServletRequest request) {
        if (Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) return;
        String origin = request.getHeader("Origin");
        // Apifox 等非浏览器客户端可不带 Origin 和 Fetch Metadata。
        if ((origin != null && !origins.contains(origin))
                || (origin == null && request.getHeader("Sec-Fetch-Site") != null)) {
            throw new ServiceException(403, "FORBIDDEN", "请求来源不受信任");
        }
    }
}
