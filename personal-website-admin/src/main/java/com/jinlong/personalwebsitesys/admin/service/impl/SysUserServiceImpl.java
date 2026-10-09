package com.jinlong.personalwebsitesys.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jinlong.personalwebsitesys.common.constant.AuthConstants;
import com.jinlong.personalwebsitesys.common.exception.ServiceException;
import com.jinlong.personalwebsitesys.admin.domain.SysUser;
import com.jinlong.personalwebsitesys.admin.domain.dto.ProfileUpdateRequest;
import com.jinlong.personalwebsitesys.admin.mapper.SysUserMapper;
import com.jinlong.personalwebsitesys.admin.service.ISysUserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SysUserServiceImpl implements ISysUserService {
    private final SysUserMapper userMapper;

    public SysUserServiceImpl(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public SysUser findByUsername(String username) {
        // 值通过参数绑定传入；保持与数据库大小写不敏感唯一索引一致。
        return userMapper.selectOne(Wrappers.<SysUser>query().apply("LOWER(username) = LOWER({0})", username));
    }

    @Override
    public SysUser requireActiveAdmin(long userId) {
        SysUser user = userMapper.selectOne(activeAdminQuery(userId));
        if (user == null) throw new ServiceException(403, "FORBIDDEN", "无权执行此操作");
        return user;
    }

    @Override
    @Transactional
    public SysUser recordAdminLogin(long userId, String expectedPasswordHash) {
        int changed = userMapper.update(null, activeAdminUpdate(userId)
                .eq(SysUser::getPasswordHash, expectedPasswordHash)
                .setSql("last_login_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP"));
        if (changed == 0) throw new ServiceException(401, "INVALID_CREDENTIALS", "账号或密码错误");
        return userMapper.selectOne(activeAdminQuery(userId));
    }

    @Override
    @Transactional
    public SysUser updateAdminProfile(long userId, ProfileUpdateRequest request) {
        String account = request.username() == null ? "" : request.username().trim();
        String name = optionalText(request.nickname());
        String address = optionalText(request.email());
        if (account.isEmpty() || account.length() > 100) {
            throw new ServiceException(400, "VALIDATION_ERROR", "登录账号不能为空且不能超过100个字符");
        }
        if (name != null && name.length() > 100) {
            throw new ServiceException(400, "VALIDATION_ERROR", "昵称不能超过100个字符");
        }
        if (address != null && (address.length() > 254 || !address.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            throw new ServiceException(400, "VALIDATION_ERROR", "请输入有效邮箱，且不能超过254个字符");
        }
        try {
            // Wrapper 显式 set(null)，确保昵称和邮箱可以清空。
            int changed = userMapper.update(null, activeAdminUpdate(userId)
                    .set(SysUser::getUsername, account)
                    .set(SysUser::getNickname, name)
                    .set(SysUser::getEmail, address)
                    .setSql("updated_at = CURRENT_TIMESTAMP"));
            if (changed == 0) throw new ServiceException(403, "FORBIDDEN", "无权执行此操作");
            return userMapper.selectOne(activeAdminQuery(userId));
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(409, "USERNAME_EXISTS", "该登录账号已被使用，请换一个账号");
        }
    }

    private static LambdaQueryWrapper<SysUser> activeAdminQuery(long id) {
        return Wrappers.<SysUser>lambdaQuery().eq(SysUser::getId, id)
                .eq(SysUser::getRole, AuthConstants.ROLE_ADMIN)
                .eq(SysUser::getStatus, AuthConstants.STATUS_ACTIVE);
    }

    @Override
    @Transactional
    public void updateAdminPassword(long userId, String expectedPasswordHash, String newPasswordHash) {
        int changed = userMapper.update(null, activeAdminUpdate(userId)
                .eq(SysUser::getPasswordHash, expectedPasswordHash)
                .set(SysUser::getPasswordHash, newPasswordHash)
                .setSql("updated_at = CURRENT_TIMESTAMP"));
        if (changed == 0) {
            requireActiveAdmin(userId);
            throw new ServiceException(409, "PASSWORD_CHANGED_RETRY", "密码已被修改，请重新登录后重试");
        }
    }

    private static LambdaUpdateWrapper<SysUser> activeAdminUpdate(long id) {
        return Wrappers.<SysUser>lambdaUpdate().eq(SysUser::getId, id)
                .eq(SysUser::getRole, AuthConstants.ROLE_ADMIN)
                .eq(SysUser::getStatus, AuthConstants.STATUS_ACTIVE);
    }

    private static String optionalText(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
