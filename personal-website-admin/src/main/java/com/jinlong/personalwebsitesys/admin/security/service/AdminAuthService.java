package com.jinlong.personalwebsitesys.admin.security.service;

import com.jinlong.personalwebsitesys.common.exception.ServiceException;
import com.jinlong.personalwebsitesys.admin.domain.SysUser;
import com.jinlong.personalwebsitesys.admin.domain.dto.LoginRequest;
import com.jinlong.personalwebsitesys.admin.service.ISysUserService;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 认证编排：校验输入与密码，通过系统业务服务维护登录时间。 */
@Service
public class AdminAuthService {
    private final ISysUserService userService;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public AdminAuthService(ISysUserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("unused-password-for-timing");
    }

    public SysUser login(LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();
        if (username == null || username.trim().isEmpty() || username.trim().length() > 100
                || password == null || password.isEmpty()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ServiceException(400, "VALIDATION_ERROR", "账号不能为空且不超过100字符，密码不能为空且不超过72个UTF-8字节");
        }
        SysUser user = userService.findByUsername(username.trim());
        String hash = user == null ? dummyHash : user.getPasswordHash();
        if (hash != null && hash.startsWith("{bcrypt}")) hash = hash.substring(8);
        boolean valid = passwordEncoder.matches(password, hash);
        if (user == null || !valid) {
            throw new ServiceException(401, "INVALID_CREDENTIALS", "账号或密码错误");
        }
        return userService.recordAdminLogin(user.getId(), user.getPasswordHash());
    }
}
