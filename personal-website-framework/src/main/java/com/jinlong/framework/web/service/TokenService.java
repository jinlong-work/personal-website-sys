package com.jinlong.framework.web.service;

import com.jinlong.common.constant.CacheConstants;
import com.jinlong.common.constant.Constants;
import com.jinlong.common.core.domain.entity.SysUser;
import com.jinlong.common.core.domain.model.LoginUser;
import com.jinlong.common.core.redis.RedisCache;
import com.jinlong.system.service.ISysUserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** JWT 保存登录标识，Redis 保存会话；退出后即刻失效。 */
@Component
public class TokenService {
    private final RedisCache redisCache;
    private final ISysUserService userService;
    private final SysPermissionService permissionService;
    private final String header;
    private final String issuer;
    private final String prefix;
    private final SecretKey key;
    private final long expireMillis;
    private final long refreshMillis;
    private final long maxLifetimeMillis;

    public TokenService(RedisCache redisCache, ISysUserService userService, SysPermissionService permissionService,
            @Value("${token.header:Authorization}") String header, @Value("${token.secret}") String secret,
            @Value("${token.issuer}") String issuer, @Value("${token.key-prefix}") String prefix,
            @Value("${token.expire-time:30}") int expireTime,
            @Value("${token.refresh-threshold:20}") int refreshThreshold,
            @Value("${token.max-lifetime:480}") int maxLifetime) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 64) {
            throw new IllegalArgumentException("JWT_SECRET 必须至少为64个UTF-8字节");
        }
        if (expireTime < 1 || refreshThreshold < 0 || refreshThreshold >= expireTime || maxLifetime < expireTime) {
            throw new IllegalArgumentException("Token 有效期配置不正确");
        }
        this.redisCache = redisCache; this.userService = userService; this.permissionService = permissionService;
        this.header = header; this.issuer = issuer; this.prefix = prefix;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = Duration.ofMinutes(expireTime).toMillis();
        this.refreshMillis = Duration.ofMinutes(refreshThreshold).toMillis();
        this.maxLifetimeMillis = Duration.ofMinutes(maxLifetime).toMillis();
    }

    public String createToken(LoginUser loginUser) {
        long now = System.currentTimeMillis();
        loginUser.setToken(UUID.randomUUID().toString());
        loginUser.setIssuedAt(now); loginUser.setLoginTime(now); loginUser.setExpireTime(now + expireMillis);
        loginUser.setIpaddr(RequestUtils.getIpAddr());
        String jwt = Jwts.builder().issuer(issuer).issuedAt(new Date(now)).expiration(new Date(now + maxLifetimeMillis))
                .claim(Constants.LOGIN_USER_KEY, loginUser.getToken()).claim(Constants.JWT_USERNAME, loginUser.getUsername())
                .signWith(key, Jwts.SIG.HS512).compact();
        redisCache.setCacheObject(getTokenKey(loginUser.getToken()), loginUser, Duration.ofMillis(expireMillis));
        return jwt;
    }

    public LoginUser getLoginUser(HttpServletRequest request) {
        String bearer = request.getHeader(header);
        if (bearer == null || !bearer.startsWith(Constants.TOKEN_PREFIX)) { return null; }
        Claims claims;
        String uuid;
        try {
            claims = Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                    .parseSignedClaims(bearer.substring(Constants.TOKEN_PREFIX.length())).getPayload();
            uuid = claims.get(Constants.LOGIN_USER_KEY, String.class);
        } catch (JwtException | IllegalArgumentException exception) { return null; }
        if (uuid == null || !uuid.matches("[a-f0-9-]{36}")) { return null; }
        LoginUser loginUser = redisCache.getCacheObject(getTokenKey(uuid));
        if (loginUser == null || loginUser.getUser() == null || loginUser.getIssuedAt() == null || loginUser.getExpireTime() == null
                || loginUser.getExpireTime() <= System.currentTimeMillis()) { return null; }
        // 账号禁用、删除、改密与权限变更对已有会话即时生效。
        SysUser currentUser = userService.selectUserById(loginUser.getUserId());
        if (currentUser == null || !"0".equals(currentUser.getStatus()) || !"0".equals(currentUser.getDelFlag())
                || !Objects.equals(currentUser.getPassword(), loginUser.getUser().getPassword())) {
            delLoginUser(uuid);
            return null;
        }
        loginUser.setUser(currentUser);
        loginUser.setPermissions(permissionService.getMenuPermission(currentUser));
        return loginUser;
    }

    public void verifyToken(LoginUser loginUser) {
        if (loginUser.getExpireTime() - System.currentTimeMillis() <= refreshMillis) { refreshToken(loginUser); }
    }

    public void refreshToken(LoginUser loginUser) {
        long now = System.currentTimeMillis();
        long expires = Math.min(now + expireMillis, loginUser.getIssuedAt() + maxLifetimeMillis);
        if (expires <= now) { delLoginUser(loginUser.getToken()); return; }
        loginUser.setLoginTime(now); loginUser.setExpireTime(expires);
        // SET XX 不会重新创建被并发退出请求删除的会话。
        redisCache.setCacheObjectIfPresent(getTokenKey(loginUser.getToken()), loginUser, Duration.ofMillis(expires - now));
    }

    public void delLoginUser(String token) {
        if (token != null) { redisCache.deleteObject(getTokenKey(token)); }
    }

    private String getTokenKey(String uuid) { return prefix + ":" + CacheConstants.LOGIN_TOKEN_KEY + uuid; }
}
