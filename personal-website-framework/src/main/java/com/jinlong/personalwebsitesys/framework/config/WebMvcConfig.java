package com.jinlong.personalwebsitesys.framework.config;

import com.jinlong.personalwebsitesys.common.constant.LogConstants;
import com.jinlong.personalwebsitesys.common.constant.AuthConstants;
import com.jinlong.personalwebsitesys.framework.security.OriginPolicy;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 通用跨域配置，不依赖管理员或其他具体业务模块。 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final OriginPolicy originPolicy;

    public WebMvcConfig(OriginPolicy originPolicy) {
        this.originPolicy = originPolicy;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(originPolicy.allowedOrigins())
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization")
                .exposedHeaders(LogConstants.REQUEST_ID_HEADER, AuthConstants.TOKEN_EXPIRES_HEADER,
                        AuthConstants.TOKEN_MAX_EXPIRES_HEADER).allowCredentials(false);
    }
}
