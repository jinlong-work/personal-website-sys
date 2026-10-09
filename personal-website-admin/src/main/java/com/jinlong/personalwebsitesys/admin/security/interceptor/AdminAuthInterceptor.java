package com.jinlong.personalwebsitesys.admin.security.interceptor;

import com.jinlong.personalwebsitesys.common.constant.AuthConstants;
import com.jinlong.personalwebsitesys.common.constant.LogConstants;
import com.jinlong.personalwebsitesys.common.exception.ServiceException;
import com.jinlong.personalwebsitesys.framework.security.OriginPolicy;
import com.jinlong.personalwebsitesys.admin.security.service.LoginRateLimiter;
import com.jinlong.personalwebsitesys.admin.domain.vo.UserProfileVo;
import com.jinlong.personalwebsitesys.admin.domain.SysUser;
import com.jinlong.personalwebsitesys.admin.security.service.TokenService;
import com.jinlong.personalwebsitesys.admin.service.ISysUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {
    private final ISysUserService userService;
    private final OriginPolicy originPolicy;
    private final LoginRateLimiter rateLimiter;
    private final TokenService tokenService;

    public AdminAuthInterceptor(ISysUserService userService, OriginPolicy originPolicy,
            LoginRateLimiter rateLimiter, TokenService tokenService) {
        this.userService = userService;
        this.originPolicy = originPolicy;
        this.rateLimiter = rateLimiter;
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        response.setHeader("Cache-Control", "no-store");
        markAuditOperation(request);
        if ("OPTIONS".equals(request.getMethod())) return true;
        originPolicy.checkWriteRequest(request);
        String path = request.getServletPath();
        if (path.equals("/api/auth/login") && "POST".equals(request.getMethod())) {
            rateLimiter.check(request.getRemoteAddr());
            return true;
        }
        if (path.equals("/api/auth/logout") && "POST".equals(request.getMethod())) return true;
        var login = tokenService.requireLogin(request);
        long id = login.getUserId();
        MDC.put(LogConstants.USER_ID, Long.toString(id));
        try {
            SysUser user = userService.requireActiveAdmin(id);
            var active = tokenService.verifyAndRefresh(login, user);
            request.setAttribute(AuthConstants.REQUEST_USER, UserProfileVo.from(user));
            request.setAttribute(AuthConstants.REQUEST_LOGIN_TOKEN, active);
            response.setHeader(AuthConstants.TOKEN_EXPIRES_HEADER, Long.toString(active.getExpiresAt()));
            response.setHeader(AuthConstants.TOKEN_MAX_EXPIRES_HEADER, Long.toString(active.getMaxExpiresAt()));
            if (path.equals("/api/auth/me/password") && "PUT".equals(request.getMethod())) {
                rateLimiter.check("password:" + id);
            }
        } catch (ServiceException ex) {
            if (ex.status() == 401 || ex.status() == 403) tokenService.revoke(login);
            throw ex;
        }
        return true;
    }

    /** 管理员业务决定操作名称，框架日志层仅负责统一输出。 */
    private static void markAuditOperation(HttpServletRequest request) {
        String operation = switch (request.getMethod() + " " + request.getServletPath()) {
            case "POST /api/auth/login" -> "ADMIN_LOGIN";
            case "POST /api/auth/logout" -> "ADMIN_LOGOUT";
            case "PUT /api/auth/me" -> "ADMIN_PROFILE_UPDATE";
            case "PUT /api/auth/me/password" -> "ADMIN_PASSWORD_UPDATE";
            default -> null;
        };
        if (operation != null) request.setAttribute(LogConstants.OPERATION_ATTRIBUTE, operation);
    }
}
