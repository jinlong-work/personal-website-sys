package com.jinlong.system.service;

import com.jinlong.common.core.domain.entity.SysUser;
import java.util.Date;

/** 本阶段只接入登录需要的用户查询和登录信息更新。 */
public interface ISysUserService {
    SysUser selectUserByUserName(String userName);
    SysUser selectUserById(Long userId);
    int updateLoginInfo(Long userId, String loginIp, Date loginDate);
}
