package com.jinlong.web.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alibaba.fastjson2.JSON;
import com.jinlong.common.core.domain.entity.SysUser;
import com.jinlong.common.core.domain.model.LoginUser;
import com.jinlong.common.exception.ServiceException;
import com.jinlong.system.domain.resume.ResumeRequests.*;
import com.jinlong.system.domain.resume.WebsiteResume;
import com.jinlong.system.mapper.UserLedgerMapper;
import com.jinlong.system.mapper.WebsiteResumeMapper;
import com.jinlong.system.service.ISysUserService;
import com.jinlong.system.service.ResumeManagementService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ResumeManagementServiceTest {
    private final WebsiteResumeMapper mapper = mock(WebsiteResumeMapper.class);
    private final ISysUserService users = mock(ISysUserService.class);
    private final UserLedgerMapper ledger = mock(UserLedgerMapper.class);
    private final ResumeManagementService service = new ResumeManagementService(mapper, users, ledger);
    private final Basic basic = new Basic("曹进龙", "JINLONG CAO", "开发工程师", "", "", "", "简介", "关于我", "", "", "hello@example.com", "", "");

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private Content content(List<Education> schools, List<Experience> jobs, List<Skill> skills) { return new Content(basic, schools, jobs, skills); }
    private SaveResume valid() { return new SaveResume(0L, content(List.of(), List.of(), List.of()), null); }
    private void login(boolean active, boolean ordinary, Set<String> permissions) {
        SysUser user = new SysUser(); user.setUserId(ordinary ? 99L : 1L); user.setUserName("test");
        user.setStatus(active ? "0" : "1"); user.setDelFlag("0"); user.setRoles(List.of());
        LoginUser principal = new LoginUser(user.getUserId(), null, user, permissions);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(users.selectUserById(user.getUserId())).thenReturn(user);
    }
    private WebsiteResume stored() {
        WebsiteResume row = new WebsiteResume(); row.setResumeId(1L); row.setVersion(0L);
        row.setContent(JSON.toJSONString(ResumeManagementService.validate(valid()))); return row;
    }

    @Test void acceptsOptionalEnglishAndIntentionallyEmptySections() {
        var result = ResumeManagementService.validate(valid());
        assertEquals(2, result.size());
        Content zh = (Content) result.get("zh");
        assertEquals(List.of(), zh.education()); assertEquals(List.of(), zh.skills());
        assertEquals("", ((Content)result.get("en")).basic().name());
    }
    @Test void rejectsMissingRequiredFieldsAndOversizedCollections() {
        assertThrows(ServiceException.class, () -> ResumeManagementService.validate(new SaveResume(0L, null, null)));
        var blank = new Basic("", "", "", "", "", "", "", "", "", "", "", "", "");
        assertThrows(ServiceException.class, () -> ResumeManagementService.validate(new SaveResume(0L, new Content(blank, List.of(), List.of(), List.of()), null)));
        var entries = Collections.nCopies(13, new Education("学校", "", "", "", "", ""));
        assertThrows(ServiceException.class, () -> ResumeManagementService.validate(new SaveResume(0L, content(entries, List.of(), List.of()), null)));
    }
    @Test void validatesDatesWithoutInventingAnUnknownMonth() {
        var school = new Education("湖北大学", "GIS", "本科", "", "2023", "");
        assertDoesNotThrow(() -> ResumeManagementService.validate(new SaveResume(0L, content(List.of(school), List.of(), List.of()), null)));
        var reversed = new Experience("公司", "岗位", "2025-06", "2024-12", "");
        var invalid = new Experience("公司", "岗位", "2024-13", "", "");
        assertThrows(ServiceException.class, () -> ResumeManagementService.validate(new SaveResume(0L, content(List.of(), List.of(reversed), List.of()), null)));
        assertThrows(ServiceException.class, () -> ResumeManagementService.validate(new SaveResume(0L, content(List.of(), List.of(invalid), List.of()), null)));
    }
    @Test void preservesOrderingAndValidatesSkillGroups() {
        var first = new Skill("地图", List.of("Cesium", "Three.js", "Cesium"), "");
        var second = new Skill("后端", List.of("Java"), "");
        Content zh = (Content) ResumeManagementService.validate(new SaveResume(0L, content(List.of(), List.of(), List.of(first, second)), null)).get("zh");
        assertEquals(List.of("Cesium", "Three.js"), zh.skills().get(0).items());
        assertEquals("后端", zh.skills().get(1).name());
        assertThrows(ServiceException.class, () -> ResumeManagementService.validate(new SaveResume(0L, content(List.of(), List.of(), List.of(new Skill("空分组", List.of(), ""))), null)));
    }
    @Test void blocksOrdinaryOrDisabledAccountsEvenWithCachedPermissions() {
        login(true, true, Set.of("*:*:*"));
        assertEquals(403, assertThrows(ServiceException.class, service::get).getCode());
        login(false, false, Set.of("*:*:*"));
        assertEquals(401, assertThrows(ServiceException.class, () -> service.save(valid())).getCode());
        verifyNoInteractions(mapper);
    }
    @Test void preventsStaleSavesAndSeparatesReadAndEditPermissions() {
        login(true, false, Set.of("website:resume:list"));
        when(mapper.selectById(1L)).thenReturn(stored());
        assertEquals(false, service.get().get("canEdit"));
        assertEquals(403, assertThrows(ServiceException.class, () -> service.save(valid())).getCode());
        login(true, false, Set.of("*:*:*"));
        when(mapper.update(isNull(), any())).thenReturn(0);
        assertEquals(409, assertThrows(ServiceException.class, () -> service.save(valid())).getCode());
    }
    @Test void publicResponseContainsOnlyPublishedContent() {
        when(mapper.selectById(1L)).thenReturn(stored());
        assertEquals(Set.of("zh", "en"), service.published().keySet());
        verifyNoInteractions(users, ledger);
    }
}
