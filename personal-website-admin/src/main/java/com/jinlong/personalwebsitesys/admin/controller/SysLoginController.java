package com.jinlong.personalwebsitesys.admin.controller;

import com.jinlong.personalwebsitesys.common.constant.LogConstants;
import com.jinlong.personalwebsitesys.common.core.domain.ApiResult;
import com.jinlong.personalwebsitesys.admin.security.service.AdminAuthService;
import com.jinlong.personalwebsitesys.admin.domain.SysUser;
import com.jinlong.personalwebsitesys.admin.security.service.TokenService;
import com.jinlong.personalwebsitesys.admin.domain.dto.LoginRequest;
import com.jinlong.personalwebsitesys.admin.domain.vo.LoginResultVo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 管理员登录、退出接口。账号资料接口见 SysProfileController。 */
@RestController
@RequestMapping("/api/auth")
public class SysLoginController {
    private final AdminAuthService authService;
    private final TokenService tokenService;

    public SysLoginController(AdminAuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public ApiResult<LoginResultVo> login(@RequestBody LoginRequest body) {
        SysUser user = authService.login(body);
        MDC.put(LogConstants.USER_ID, user.getId().toString());
        return ApiResult.success("登录成功", tokenService.createToken(user));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        var login = tokenService.resolve(request);
        if (login != null) MDC.put(LogConstants.USER_ID, Long.toString(login.getUserId()));
        tokenService.revoke(login);
    }

}
