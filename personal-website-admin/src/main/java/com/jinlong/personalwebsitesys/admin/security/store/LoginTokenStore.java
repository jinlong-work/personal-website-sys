package com.jinlong.personalwebsitesys.admin.security.store;

import com.jinlong.personalwebsitesys.admin.security.domain.LoginToken;

/** 登录状态仓库；续期不得重新创建已撤销或已过期的登录状态。 */
public interface LoginTokenStore {
    void save(LoginToken token);
    LoginToken findActive(long userId, String id, long now);
    LoginToken renewIfActive(LoginToken token, long now, long expireMillis, long thresholdMillis);
    void remove(LoginToken token);
    void removeByUser(long userId);
}
