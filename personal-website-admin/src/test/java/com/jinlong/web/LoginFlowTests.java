package com.jinlong.web;

import com.jinlong.common.core.domain.entity.SysMenu;
import com.jinlong.common.core.domain.entity.SysUser;
import com.jinlong.common.core.domain.model.LoginUser;
import com.jinlong.common.core.redis.RedisCache;
import com.jinlong.framework.web.service.TokenService;
import com.jinlong.system.domain.SysConfig;
import com.jinlong.system.mapper.SysConfigMapper;
import com.jinlong.system.mapper.SysLogininforMapper;
import com.jinlong.system.mapper.SysMenuMapper;
import com.jinlong.system.mapper.SysUserMapper;
import com.jinlong.web.service.MinioAvatarStore;
import org.springframework.mock.web.MockMultipartFile;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 外部数据库/Redis使用替身，测试真实MVC、安全过滤器、BCrypt、JWT与Redis值序列化。 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/login_test",
        "spring.datasource.username=test", "spring.datasource.password=test",
        "token.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "token.key-prefix=login-test", "app.auth.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
class LoginFlowTests {
    private static final String UUID = "12345678123456781234567812345678";
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired TokenService tokens;
    @MockitoBean SysUserMapper users;
    @MockitoBean SysMenuMapper menus;
    @MockitoBean SysConfigMapper configs;
    @MockitoBean SysLogininforMapper loginLogs;
    @MockitoBean RedisCache cache;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean MinioAvatarStore avatarStore;
    private final JdkSerializationRedisSerializer serializer = new JdkSerializationRedisSerializer();
    private final Map<String, byte[]> cached = new HashMap<>();
    private final Map<String, Integer> failures = new HashMap<>();
    private SysUser admin;

    @BeforeEach
    void setUp() {
        cached.clear(); failures.clear();
        admin = new SysUser(1L);
        admin.setUserName("admin"); admin.setNickName("管理员"); admin.setDeptId(100L);
        admin.setPassword(encoder.encode("secret123")); admin.setStatus("0"); admin.setDelFlag("0"); admin.setRoles(List.of());
        when(users.selectUserByUserName(anyString())).thenAnswer(inv -> "admin".equalsIgnoreCase(inv.getArgument(0)) ? admin : null);
        when(users.selectUserById(1L)).thenReturn(admin);
        when(configs.selectConfig(any())).thenAnswer(inv -> {
            SysConfig input = inv.getArgument(0);
            SysConfig config = new SysConfig(); config.setConfigKey(input.getConfigKey());
            config.setConfigValue("sys.account.captchaEnabled".equals(input.getConfigKey()) ? "true" : "0");
            if ("sys.login.blackIPList".equals(input.getConfigKey())) { config.setConfigValue(""); }
            return config;
        });
        when(cache.getCacheObject(anyString())).thenAnswer(inv -> deserialize(inv.getArgument(0)));
        when(cache.getAndDeleteCacheObject(anyString())).thenAnswer(inv -> serializer.deserialize(cached.remove(inv.getArgument(0))));
        doAnswer(inv -> { cached.put(inv.getArgument(0), serializer.serialize(inv.getArgument(1))); return null; })
                .when(cache).setCacheObject(anyString(), any(), any(Duration.class));
        doAnswer(inv -> { cached.put(inv.getArgument(0), serializer.serialize(inv.getArgument(1))); return null; })
                .when(cache).setCacheObject(anyString(), any(), anyInt(), any(TimeUnit.class));
        doAnswer(inv -> { cached.put(inv.getArgument(0), serializer.serialize(inv.getArgument(1))); return null; })
                .when(cache).setCacheObject(anyString(), any());
        when(cache.setCacheObjectIfPresent(anyString(), any(), any(Duration.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            if (!cached.containsKey(key)) { return false; }
            cached.put(key, serializer.serialize(inv.getArgument(1))); return true;
        });
        when(cache.deleteObject(anyString())).thenAnswer(inv -> cached.remove(inv.getArgument(0)) != null);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(inv -> failures.containsKey(inv.getArgument(0)) ? failures.get(inv.getArgument(0)).toString() : null);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(inv -> {
            List<String> keys = inv.getArgument(1);
            return (long) failures.merge(keys.getFirst(), 1, Integer::sum);
        });
        when(redis.delete(anyString())).thenAnswer(inv -> failures.remove(inv.getArgument(0)) != null);
        when(menus.selectMenuTreeAll()).thenReturn(List.of());
    }

    private Object deserialize(String key) { return serializer.deserialize(cached.get(key)); }
    private void captcha() { cached.put("login-test:captcha_codes:" + UUID, serializer.serialize("6")); }
    private String body(String username, String password, String code) {
        return json.writeValueAsString(Map.of("username", username, "password", password, "code", code, "uuid", UUID));
    }
    private String login() throws Exception {
        captcha();
        String result = mvc.perform(post("/login").contentType("application/json").content(body("admin", "secret123", "6")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("token").asText();
    }

    @Test
    void loginInfoRoutersAndLogoutRevokeToken() throws Exception {
        String token = login();
        mvc.perform(get("/getInfo").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userName").value("admin"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.roles[0]").value("admin"))
                .andExpect(jsonPath("$.permissions[0]").value("*:*:*"));
        mvc.perform(get("/getRouters").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        mvc.perform(post("/logout").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/getInfo").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        verify(users).updateLoginInfo(eq(1L), anyString(), any());
    }

    @Test
    void captchaIsRenderedAndConsumedOnceEvenWithWrongCode() throws Exception {
        mvc.perform(get("/captchaImage")).andExpect(status().isOk()).andExpect(jsonPath("$.captchaEnabled").value(true))
                .andExpect(jsonPath("$.uuid").isString()).andExpect(jsonPath("$.img").isString());
        captcha();
        mvc.perform(post("/login").contentType("application/json").content(body("admin", "secret123", "wrong")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.msg").value("验证码错误"));
        mvc.perform(post("/login").contentType("application/json").content(body("admin", "secret123", "6")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.msg").value("验证码已失效"));
    }

    @Test
    void missingCaptchaAndInvalidJsonHaveClearErrors() throws Exception {
        captcha();
        mvc.perform(post("/login").contentType("application/json").content("{\"username\":\"admin\",\"password\":\"secret123\",\"uuid\":\"" + UUID + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.msg").value("验证码错误"));
        mvc.perform(post("/login").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void invalidOrMissingTokensCannotAccessProtectedEndpoints() throws Exception {
        mvc.perform(get("/getInfo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/getRouters").header("Authorization", "Bearer invalid.jwt"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        String token = login();
        String tampered = (token.charAt(0) == 'a' ? "b" : "a") + token.substring(1);
        mvc.perform(get("/getInfo").header("Authorization", "Bearer " + tampered)).andExpect(status().isUnauthorized());
    }

    @Test
    void disablingUserOrChangingPasswordRevokesExistingSessions() throws Exception {
        String token = login();
        admin.setStatus("1");
        mvc.perform(get("/getInfo").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        admin.setStatus("0"); token = login();
        admin.setPassword(encoder.encode("changed123"));
        mvc.perform(get("/getInfo").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndDisabledAccountUseSameResponse() throws Exception {
        captcha();
        mvc.perform(post("/login").contentType("application/json").content(body("admin", "wrong123", "6")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.msg").value("用户不存在或密码错误"));
        admin.setStatus("1"); captcha();
        mvc.perform(post("/login").contentType("application/json").content(body("admin", "secret123", "6")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.msg").value("用户不存在或密码错误"));
    }

    @Test
    void fiveFailuresLockAccountAcrossUsernameCasing() throws Exception {
        for (int count = 0; count < 5; count++) {
            captcha();
            mvc.perform(post("/login").contentType("application/json").content(body(count % 2 == 0 ? "admin" : "ADMIN", "wrong123", "6")))
                    .andExpect(status().isUnauthorized());
        }
        captcha();
        mvc.perform(post("/login").contentType("application/json").content(body("ADMIN", "secret123", "6")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void logoutSessionCannotBeRecreatedByRefresh() throws Exception {
        String token = login();
        LoginUser session = cached.values().stream().map(serializer::deserialize).filter(LoginUser.class::isInstance)
                .map(LoginUser.class::cast).findFirst().orElseThrow();
        mvc.perform(post("/logout").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        tokens.refreshToken(session);
        assertThat(cached.keySet()).noneMatch(key -> key.contains("login_tokens:"));
    }

    @Test
    void normalUserReceivesAssignedMenuPermissionAndRoute() throws Exception {
        admin.setUserId(7L);
        when(users.selectUserById(7L)).thenReturn(admin);
        when(menus.selectMenuPermsByUserId(7L)).thenReturn(List.of("website:article:list,website:article:read"));
        SysMenu menu = new SysMenu();
        menu.setMenuId(20L); menu.setParentId(0L); menu.setMenuName("文章"); menu.setMenuType("C");
        menu.setPath("articles"); menu.setComponent("articles/index"); menu.setIsFrame("1"); menu.setIsCache("0"); menu.setVisible("0");
        when(menus.selectMenuTreeByUserId(7L)).thenReturn(List.of(menu));
        String token = login();
        mvc.perform(get("/getInfo").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(2));
        mvc.perform(get("/getRouters").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].children[0].component").value("articles/index"));
    }

    @Test
    void redisFailureReturns503AndCorsAllowsConfiguredOrigin() throws Exception {
        mvc.perform(options("/login").header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        String token = login();
        when(cache.getCacheObject(contains("login_tokens:"))).thenThrow(new RedisConnectionFailureException("test"));
        mvc.perform(get("/getInfo").header("Authorization", "Bearer " + token)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
    }

    @Test
    void databaseFailureDoesNotCountAsBadPassword() throws Exception {
        when(users.selectUserByUserName("admin")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("test database offline"));
        captcha();
        mvc.perform(post("/login").contentType("application/json").content(body("admin", "secret123", "6")))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(503));
        assertThat(failures).isEmpty();
    }

    @Test
    void avatarUploadRequiresLoginAndValidImage() throws Exception {
        var file = new MockMultipartFile("file", "avatar.png", "image/png", AvatarStorageTests.picture(16, 16, "png"));
        mvc.perform(multipart("/files/avatars").file(file)).andExpect(status().isUnauthorized());
        verifyNoInteractions(avatarStore);
        String token = login();
        mvc.perform(multipart("/files/avatars").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatar").value(org.hamcrest.Matchers.matchesPattern("/files/avatars/[a-f0-9]{32}\\.png")));
        verify(avatarStore).put(matches("[a-f0-9]{32}\\.png"), any(byte[].class));
    }

    @Test
    void avatarReadIsPublicButCannotReadArbitraryObjects() throws Exception {
        String name = "a".repeat(32) + ".png";
        byte[] picture = AvatarStorageTests.picture(8, 8, "png");
        when(avatarStore.get(name)).thenReturn(picture);
        mvc.perform(get("/files/avatars/" + name)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(content().bytes(picture))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/files/avatars/secret.txt")).andExpect(status().isNotFound());
        verify(avatarStore, never()).get("secret.txt");
    }

    @Test
    void accountWithoutUploadPermissionCannotUpload() throws Exception {
        admin.setUserId(7L); when(users.selectUserById(7L)).thenReturn(admin);
        when(menus.selectMenuPermsByUserId(7L)).thenReturn(List.of("website:article:read"));
        String token = login();
        mvc.perform(multipart("/files/avatars").file(new MockMultipartFile("file", "avatar.png", "image/png", AvatarStorageTests.picture(8, 8, "png")))
                .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        verifyNoInteractions(avatarStore);
    }
}
