package com.jinlong.framework.security.filter;

import com.jinlong.common.core.domain.model.LoginUser;
import com.jinlong.framework.security.handle.SecurityResponse;
import com.jinlong.framework.web.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {
    private final TokenService tokenService;
    private final JsonMapper mapper;

    public JwtAuthenticationTokenFilter(TokenService tokenService, JsonMapper mapper) {
        this.tokenService = tokenService; this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            LoginUser loginUser = tokenService.getLoginUser(request);
            if (loginUser != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                tokenService.verifyToken(loginUser);
                var authentication = UsernamePasswordAuthenticationToken.authenticated(loginUser, null, loginUser.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (DataAccessException exception) {
            SecurityResponse.write(response, mapper, 503, "认证服务暂不可用");
            return;
        }
        chain.doFilter(request, response);
    }
}
