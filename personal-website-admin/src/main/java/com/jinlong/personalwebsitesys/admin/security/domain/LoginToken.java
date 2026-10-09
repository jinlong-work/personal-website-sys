package com.jinlong.personalwebsitesys.admin.security.domain;

/** 服务端登录状态，不保存密码或完整JWT，也不作为接口响应返回。 */
public final class LoginToken {
    private final String id;
    private final long userId;
    private final String passwordFingerprint;
    private final long expiresAt;
    private final long maxExpiresAt;

    public LoginToken(String id, long userId, String fingerprint, long expiresAt, long maxExpiresAt) {
        this.id = id;
        this.userId = userId;
        this.passwordFingerprint = fingerprint;
        this.expiresAt = expiresAt;
        this.maxExpiresAt = maxExpiresAt;
    }

    public String getId() { return id; }
    public long getUserId() { return userId; }
    public String getPasswordFingerprint() { return passwordFingerprint; }
    public long getExpiresAt() { return expiresAt; }
    public long getMaxExpiresAt() { return maxExpiresAt; }
    public LoginToken withExpiresAt(long value) {
        return new LoginToken(id, userId, passwordFingerprint, value, maxExpiresAt);
    }
}
