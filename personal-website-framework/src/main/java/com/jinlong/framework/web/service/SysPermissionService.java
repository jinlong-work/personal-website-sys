package com.jinlong.framework.web.service;

import com.jinlong.common.constant.Constants;
import com.jinlong.common.constant.WebsiteRole;
import com.jinlong.common.core.domain.entity.SysRole;
import com.jinlong.common.core.domain.entity.SysUser;
import com.jinlong.system.service.ISysMenuService;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 沿用若依超级管理员、角色标识和菜单权限规则。 */
@Component
public class SysPermissionService {
    private final ISysMenuService menuService;

    public SysPermissionService(ISysMenuService menuService) { this.menuService = menuService; }

    public Set<String> getRolePermission(SysUser user) {
        if (WebsiteRole.of(user) == WebsiteRole.SUPER_ADMIN) { return Set.of(Constants.SUPER_ADMIN); }
        Set<String> roles = new HashSet<>();
        if (user.getRoles() != null) {
            user.getRoles().stream().filter(role -> "0".equals(role.getStatus()))
                    .map(SysRole::getRoleKey).filter(key -> key != null && !key.isBlank()).forEach(roles::add);
        }
        return roles;
    }

    public Set<String> getMenuPermission(SysUser user) {
        if (WebsiteRole.of(user) == WebsiteRole.SUPER_ADMIN) { return Set.of(Constants.ALL_PERMISSION); }
        // 具体操作权限统一读取数据库中的角色—权限关系，角色范围仍由业务服务校验。
        return menuService.selectMenuPermsByUserId(user.getUserId());
    }
}
