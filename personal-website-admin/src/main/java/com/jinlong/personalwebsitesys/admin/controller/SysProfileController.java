package com.jinlong.personalwebsitesys.admin.controller;

import com.jinlong.personalwebsitesys.common.constant.AuthConstants;
import com.jinlong.personalwebsitesys.common.core.domain.ApiResult;
import com.jinlong.personalwebsitesys.admin.domain.SysUser;
import com.jinlong.personalwebsitesys.admin.domain.dto.ProfileUpdateRequest;
import com.jinlong.personalwebsitesys.admin.domain.dto.PasswordUpdateRequest;
import com.jinlong.personalwebsitesys.admin.security.service.AdminPasswordService;
import com.jinlong.personalwebsitesys.admin.security.service.TokenService;
import com.jinlong.personalwebsitesys.admin.domain.vo.UserProfileVo;
import com.jinlong.personalwebsitesys.admin.service.ISysUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前管理员资料接口，操作对象由鉴权拦截器确定。 */
@RestController
@RequestMapping("/api/auth/me")
public class SysProfileController {
    private final ISysUserService userService;
    private final AdminPasswordService passwordService;
    private final TokenService tokenService;

    public SysProfileController(ISysUserService userService, AdminPasswordService passwordService, TokenService tokenService) {
        this.userService = userService;
        this.passwordService = passwordService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ApiResult<UserProfileVo> profile(@RequestAttribute(AuthConstants.REQUEST_USER) UserProfileVo current) {
        return ApiResult.success("操作成功", current);
    }

    @PutMapping
    public ApiResult<UserProfileVo> updateProfile(@RequestBody ProfileUpdateRequest body,
            @RequestAttribute(AuthConstants.REQUEST_USER) UserProfileVo current) {
        SysUser updated = userService.updateAdminProfile(Long.parseLong(current.id()), body);
        return ApiResult.success("账号信息已更新", UserProfileVo.from(updated));
    }

    @PutMapping("/password")
    public ApiResult<Void> changePassword(@RequestBody PasswordUpdateRequest body,
            @RequestAttribute(AuthConstants.REQUEST_USER) UserProfileVo current) {
        long userId = Long.parseLong(current.id());
        passwordService.changePassword(userId, body);
        tokenService.revokeUser(userId);
        return ApiResult.success("密码已修改，请使用新密码重新登录", null);
    }
}
