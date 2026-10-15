package com.jinlong.common.constant;

import com.jinlong.common.core.domain.entity.SysUser;

/** 保留若依 admin/common 标识，manager 为网站管理员。 */
public enum WebsiteRole {
    SUPER_ADMIN("admin", "超级管理员", 3),
    ADMIN("manager", "管理员", 2),
    USER("common", "普通用户", 1);

    private final String key;
    private final String label;
    private final int level;

    WebsiteRole(String key, String label, int level) {
        this.key = key; this.label = label; this.level = level;
    }

    public String key() { return key; }
    public String label() { return label; }
    public int level() { return level; }

    public static WebsiteRole fromKey(String key) {
        for (WebsiteRole role : values()) {
            if (role.key.equals(key)) { return role; }
        }
        return null;
    }

    public static WebsiteRole of(SysUser user) {
        if (user.isAdmin() || hasRole(user, SUPER_ADMIN.key)) { return SUPER_ADMIN; }
        return hasRole(user, ADMIN.key) ? ADMIN : USER;
    }

    private static boolean hasRole(SysUser user, String key) {
        return user.getRoles() != null && user.getRoles().stream().anyMatch(role ->
                "0".equals(role.getStatus()) && !"2".equals(role.getDelFlag()) && key.equals(role.getRoleKey()));
    }

    /** 未归入这三种角色的旧权限账号，仅允许超级管理员维护。 */
    public static boolean isOrdinaryAccount(SysUser user) {
        return !user.isAdmin() && of(user) == USER && (user.getRoles() == null || user.getRoles().stream()
                .allMatch(role -> USER.key.equals(role.getRoleKey())));
    }
}
