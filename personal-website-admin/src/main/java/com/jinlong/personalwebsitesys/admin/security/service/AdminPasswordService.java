package com.jinlong.personalwebsitesys.admin.security.service;

import com.jinlong.personalwebsitesys.admin.domain.SysUser;
import com.jinlong.personalwebsitesys.admin.domain.dto.PasswordUpdateRequest;
import com.jinlong.personalwebsitesys.admin.service.ISysUserService;
import com.jinlong.personalwebsitesys.common.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 校验旧密码及新密码规则，交由用户业务服务执行原子更新。 */
@Service
public class AdminPasswordService {
    private final ISysUserService userService;
    private final PasswordEncoder encoder;

    public AdminPasswordService(ISysUserService userService, PasswordEncoder encoder) {
        this.userService = userService;
        this.encoder = encoder;
    }

    public void changePassword(long userId, PasswordUpdateRequest request) {
        String current = request.getCurrentPassword();
        String next = request.getNewPassword();
        if (current == null || current.isEmpty() || current.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ServiceException(400, "VALIDATION_ERROR", "当前密码不能为空且不能超过72个UTF-8字节");
        }
        if (next == null || next.codePointCount(0, next.length()) < 8
                || next.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ServiceException(400, "VALIDATION_ERROR", "新密码至少8个字符且不能超过72个UTF-8字节");
        }
        if (!next.equals(request.getConfirmPassword())) {
            throw new ServiceException(400, "PASSWORD_CONFIRM_MISMATCH", "两次输入的新密码不一致");
        }
        SysUser user = userService.requireActiveAdmin(userId);
        String originalHash = user.getPasswordHash();
        String hash = originalHash.startsWith("{bcrypt}") ? originalHash.substring(8) : originalHash;
        if (!encoder.matches(current, hash)) {
            throw new ServiceException(400, "CURRENT_PASSWORD_INCORRECT", "当前密码不正确");
        }
        if (encoder.matches(next, hash)) {
            throw new ServiceException(400, "PASSWORD_UNCHANGED", "新密码不能与当前密码相同");
        }
        userService.updateAdminPassword(userId, originalHash, encoder.encode(next));
    }
}
