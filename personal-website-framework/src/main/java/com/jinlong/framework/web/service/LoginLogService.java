package com.jinlong.framework.web.service;

import com.jinlong.system.domain.SysLogininfor;
import com.jinlong.system.mapper.SysLogininforMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoginLogService {
    private static final Logger log = LoggerFactory.getLogger(LoginLogService.class);
    private final SysLogininforMapper logininforMapper;

    public LoginLogService(SysLogininforMapper logininforMapper) { this.logininforMapper = logininforMapper; }

    public void record(String username, String status, String message, String ip) {
        SysLogininfor info = new SysLogininfor();
        info.setUserName(username == null ? "" : username.substring(0, Math.min(username.length(), 100)));
        info.setStatus(status); info.setMsg(message); info.setIpaddr(ip);
        info.setLoginLocation(""); info.setBrowser(""); info.setOs("");
        try { logininforMapper.insertLogininfor(info); }
        catch (RuntimeException exception) { log.warn("登录审计记录写入失败", exception); }
    }
}
