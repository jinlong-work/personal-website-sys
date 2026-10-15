package com.jinlong.framework.web.service;

import com.jinlong.common.core.domain.entity.SysUser;
import com.jinlong.common.core.domain.model.LoginUser;
import com.jinlong.system.service.ISysUserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    private final ISysUserService userService;
    private final SysPermissionService permissionService;

    public UserDetailsServiceImpl(ISysUserService userService, SysPermissionService permissionService) {
        this.userService = userService;
        this.permissionService = permissionService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userService.selectUserByUserName(username);
        if (user == null || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag())) {
            throw new UsernameNotFoundException("用户不存在或已停用");
        }
        return new LoginUser(user.getUserId(), user.getDeptId(), user, permissionService.getMenuPermission(user));
    }
}
