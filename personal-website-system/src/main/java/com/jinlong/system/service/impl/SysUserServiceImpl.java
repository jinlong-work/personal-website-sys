package com.jinlong.system.service.impl;

import com.jinlong.common.core.domain.entity.SysUser;
import com.jinlong.system.mapper.SysUserMapper;
import com.jinlong.system.service.ISysUserService;
import java.util.Date;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl implements ISysUserService {
    private final SysUserMapper userMapper;

    public SysUserServiceImpl(SysUserMapper userMapper) { this.userMapper = userMapper; }

    public SysUser selectUserByUserName(String userName) { return userMapper.selectUserByUserName(userName); }
    public SysUser selectUserById(Long userId) { return userMapper.selectUserById(userId); }
    public int updateLoginInfo(Long userId, String loginIp, Date loginDate) {
        return userMapper.updateLoginInfo(userId, loginIp, loginDate);
    }
}
