package com.jinlong.personalwebsitesys.admin.domain.vo;

/** 不自动生成toString，避免完整令牌出现在日志中。时间为Unix毫秒。 */
public final class LoginResultVo {
    private final String token;
    private final long expiresAt;
    private final long maxExpiresAt;
    private final UserProfileVo user;

    public LoginResultVo(String token, long expiresAt, long maxExpiresAt, UserProfileVo user) {
        this.token = token;
        this.expiresAt = expiresAt;
        this.maxExpiresAt = maxExpiresAt;
        this.user = user;
    }

    public String getToken() { return token; }
    public String getTokenType() { return "Bearer"; }
    public long getExpiresAt() { return expiresAt; }
    public long getMaxExpiresAt() { return maxExpiresAt; }
    public UserProfileVo getUser() { return user; }
}
