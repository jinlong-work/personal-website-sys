package com.jinlong.personalwebsitesys.admin.service;

import com.jinlong.personalwebsitesys.admin.domain.SysUser;
import com.jinlong.personalwebsitesys.admin.domain.dto.ProfileUpdateRequest;

/** 用户业务契约，不向调用方开放未经业务校验的通用 CRUD。 */
public interface ISysUserService {
    /** 按不区分大小写的账号查询，账号不存在时返回 null。 */
    SysUser findByUsername(String username);

    /** 获取当前启用的管理员，无权限时抛出业务异常。 */
    SysUser requireActiveAdmin(long userId);

    /** 更新登录时间并返回最新资料，在同一事务内完成。 */
    SysUser recordAdminLogin(long userId, String expectedPasswordHash);

    /** 更新本人资料；空昵称和邮箱允许清空，账号重复时返回冲突。 */
    SysUser updateAdminProfile(long userId, ProfileUpdateRequest request);

    /** 仅在数据库密码仍与校验时一致的情况下更新，防止并发修改覆盖。 */
    void updateAdminPassword(long userId, String expectedPasswordHash, String newPasswordHash);
}
