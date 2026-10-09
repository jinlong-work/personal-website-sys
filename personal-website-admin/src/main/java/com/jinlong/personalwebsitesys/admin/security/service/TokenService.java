package com.jinlong.personalwebsitesys.admin.security.service;

import com.jinlong.personalwebsitesys.admin.config.TokenProperties;
import com.jinlong.personalwebsitesys.admin.domain.SysUser;
import com.jinlong.personalwebsitesys.admin.domain.vo.LoginResultVo;
import com.jinlong.personalwebsitesys.admin.domain.vo.UserProfileVo;
import com.jinlong.personalwebsitesys.admin.security.domain.LoginToken;
import com.jinlong.personalwebsitesys.admin.security.store.LoginTokenStore;
import com.jinlong.personalwebsitesys.admin.security.util.PasswordFingerprint;
import com.jinlong.personalwebsitesys.common.exception.ServiceException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** 参考若依：签名JWT只携带登录标识，真实登录时效由服务端仓库控制。 */
@Service
public class TokenService {
    private final TokenProperties properties;
    private final LoginTokenStore store;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public TokenService(TokenProperties properties, LoginTokenStore store) {
        this.properties = properties;
        this.store = store;
        if (properties.getExpireTime() < 1 || properties.getRefreshThreshold() < 0
                || properties.getRefreshThreshold() >= properties.getExpireTime()
                || properties.getMaxLifetime() < properties.getExpireTime()
                || properties.getMaxLifetime() > 43200
                || !"Authorization".equals(properties.getHeader())
                || properties.getIssuer() == null || properties.getIssuer().isBlank()) {
            throw new IllegalStateException("token配置无效：使用Authorization，0<=续期阈值<有效期<=最长时效<=43200分钟");
        }
        try {
            byte[] bytes = Decoders.BASE64.decode(properties.getSecret());
            if (bytes.length < 64) throw new IllegalArgumentException();
            signingKey = Keys.hmacShaKeyFor(bytes);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("请在dev配置或JWT_SECRET中设置至少64字节随机密钥的Base64值");
        }
        parser = Jwts.parser().verifyWith(signingKey).requireIssuer(properties.getIssuer()).build();
    }

    public LoginResultVo createToken(SysUser user) {
        // JWT时间精度为秒，统一对齐，缓存和签名令牌使用同一绝对截止时间。
        long now = System.currentTimeMillis() / 1000 * 1000;
        long maxExpiresAt = now + minutes(properties.getMaxLifetime());
        String id = UUID.randomUUID().toString();
        LoginToken login = new LoginToken(id, user.getId(), PasswordFingerprint.of(user.getPasswordHash()),
                now + minutes(properties.getExpireTime()), maxExpiresAt);
        String jwt = Jwts.builder().id(id).subject(user.getId().toString()).issuer(properties.getIssuer())
                .issuedAt(new Date(now)).expiration(new Date(maxExpiresAt))
                .signWith(signingKey, Jwts.SIG.HS512).compact();
        store.save(login);
        return new LoginResultVo(jwt, login.getExpiresAt(), maxExpiresAt, UserProfileVo.from(user));
    }

    public LoginToken requireLogin(HttpServletRequest request) {
        LoginToken login = resolve(request);
        if (login == null) throw unauthenticated();
        return login;
    }

    public LoginToken resolve(HttpServletRequest request) {
        String authorization = request.getHeader(properties.getHeader());
        if (authorization == null || authorization.length() > 4096 || !authorization.startsWith("Bearer ")) return null;
        try {
            var signed = parser.parseSignedClaims(authorization.substring(7).trim());
            if (!"HS512".equals(signed.getHeader().getAlgorithm())) return null;
            Claims claims = signed.getPayload();
            if (claims.getId() == null || claims.getExpiration() == null) return null;
            long userId = Long.parseLong(claims.getSubject());
            if (userId <= 0) return null;
            LoginToken login = store.findActive(userId, claims.getId(), System.currentTimeMillis());
            if (login == null || !Long.toString(login.getUserId()).equals(claims.getSubject())
                    || login.getMaxExpiresAt() != claims.getExpiration().getTime()) return null;
            return login;
        } catch (JwtException | IllegalArgumentException ex) {
            return null; // 不记录原始Token或解析异常消息。
        }
    }

    public LoginToken verifyAndRefresh(LoginToken login, SysUser user) {
        if (login.getUserId() != user.getId()
                || !PasswordFingerprint.of(user.getPasswordHash()).equals(login.getPasswordFingerprint())) {
            store.remove(login);
            throw unauthenticated();
        }
        LoginToken renewed = store.renewIfActive(login, System.currentTimeMillis(),
                minutes(properties.getExpireTime()), minutes(properties.getRefreshThreshold()));
        if (renewed == null) throw unauthenticated();
        return renewed;
    }

    public void revoke(LoginToken login) { if (login != null) store.remove(login); }
    public void revokeUser(long userId) { store.removeByUser(userId); }
    private static long minutes(int value) { return TimeUnit.MINUTES.toMillis(value); }
    private static ServiceException unauthenticated() {
        return new ServiceException(401, "UNAUTHENTICATED", "登录已过期或失效，请重新登录");
    }
}
