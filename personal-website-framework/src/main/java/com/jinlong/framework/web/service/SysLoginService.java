package com.jinlong.framework.web.service;

import com.jinlong.common.constant.CacheConstants;
import com.jinlong.common.core.domain.model.LoginUser;
import com.jinlong.common.core.redis.RedisCache;
import com.jinlong.common.exception.ServiceException;
import com.jinlong.system.service.ISysConfigService;
import com.jinlong.system.service.ISysUserService;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/** 若依登录流程：验证码、前置校验、Spring Security 认证、登录记录、JWT。 */
@Component
public class SysLoginService {
    private final TokenService tokenService;
    private final AuthenticationManager authenticationManager;
    private final RedisCache redisCache;
    private final ISysUserService userService;
    private final ISysConfigService configService;
    private final SysPasswordService passwordService;
    private final LoginLogService loginLogService;
    private final String prefix;

    public SysLoginService(TokenService tokenService, AuthenticationManager authenticationManager,
            RedisCache redisCache, ISysUserService userService, ISysConfigService configService,
            SysPasswordService passwordService, LoginLogService loginLogService,
            @Value("${token.key-prefix}") String prefix) {
        this.tokenService = tokenService; this.authenticationManager = authenticationManager;
        this.redisCache = redisCache; this.userService = userService; this.configService = configService;
        this.passwordService = passwordService; this.loginLogService = loginLogService; this.prefix = prefix;
    }

    public String login(String username, String password, String code, String uuid) {
        String ip = RequestUtils.getIpAddr();
        try {
            validateCaptcha(username, code, uuid);
            loginPreCheck(username, password);
            passwordService.checkRetryLimit(username);
            LoginUser loginUser;
            try {
                loginUser = (LoginUser) authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(username, password)).getPrincipal();
            } catch (InternalAuthenticationServiceException exception) {
                // 数据库不可用不能计为用户密码错误。
                if (exception.getCause() instanceof DataAccessException cause) { throw cause; }
                throw exception;
            } catch (AuthenticationException exception) {
                passwordService.recordFailure(username);
                throw new ServiceException("用户不存在或密码错误", 401);
            }
            userService.updateLoginInfo(loginUser.getUserId(), ip, new Date());
            String token = tokenService.createToken(loginUser);
            passwordService.clearLoginRecordCache(username);
            loginLogService.record(username, "0", "登录成功", ip);
            return token;
        } catch (ServiceException exception) {
            loginLogService.record(username, "1", exception.getMessage(), ip);
            throw exception;
        }
    }

    public void validateCaptcha(String username, String code, String uuid) {
        if (!configService.selectCaptchaEnabled()) { return; }
        if (uuid == null || !uuid.matches("[a-fA-F0-9]{32}")) {
            throw new ServiceException("验证码已失效", 400);
        }
        String captcha = redisCache.getAndDeleteCacheObject(prefix + ":" + CacheConstants.CAPTCHA_CODE_KEY + uuid);
        if (captcha == null) { throw new ServiceException("验证码已失效", 400); }
        if (code == null || !captcha.equalsIgnoreCase(code)) { throw new ServiceException("验证码错误", 400); }
    }

    public void loginPreCheck(String username, String password) {
        if (username == null || password == null || username.isBlank() || password.isEmpty()) {
            throw new ServiceException("用户名和密码不能为空", 400);
        }
        if (!username.equals(username.trim()) || username.length() < 2 || username.length() > 100
                || password.length() < 5 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ServiceException("用户不存在或密码错误", 401);
        }
        String blacklist = configService.selectConfigByKey("sys.login.blackIPList");
        if (blacklist != null && !blacklist.isBlank()) {
            for (String pattern : blacklist.split("[,;\\s]+")) {
                if (org.springframework.util.PatternMatchUtils.simpleMatch(pattern, RequestUtils.getIpAddr())) {
                    throw new ServiceException("当前IP禁止登录", 403);
                }
            }
        }
    }
}
