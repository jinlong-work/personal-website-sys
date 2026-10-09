package com.jinlong.personalwebsitesys.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 令牌时效以分钟配置，密钥只从本机dev配置或环境变量提供。 */
@Component
@ConfigurationProperties(prefix = "token")
public class TokenProperties {
    private String header = "Authorization";
    private String secret;
    private String issuer = "personal-website-sys";
    private int expireTime = 30;
    private int refreshThreshold = 20;
    private int maxLifetime = 480;
    private String keyPrefix = "personal-website:auth";

    public String getHeader() { return header; }
    public void setHeader(String value) { header = value; }
    public String getSecret() { return secret; }
    public void setSecret(String value) { secret = value; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String value) { issuer = value; }
    public int getExpireTime() { return expireTime; }
    public void setExpireTime(int value) { expireTime = value; }
    public int getRefreshThreshold() { return refreshThreshold; }
    public void setRefreshThreshold(int value) { refreshThreshold = value; }
    public int getMaxLifetime() { return maxLifetime; }
    public void setMaxLifetime(int value) { maxLifetime = value; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String value) { keyPrefix = value; }
}
