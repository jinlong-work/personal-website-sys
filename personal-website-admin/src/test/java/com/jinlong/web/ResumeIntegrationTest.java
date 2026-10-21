package com.jinlong.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.alibaba.fastjson2.JSON;
import com.jinlong.common.core.domain.model.LoginUser;
import com.jinlong.system.domain.resume.ResumeRequests.*;
import com.jinlong.system.service.ISysUserService;
import com.jinlong.system.service.ResumeManagementService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in real PostgreSQL checks; all content writes roll back after each test. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_RESUME_INTEGRATION", matches = "true")
@Transactional
class ResumeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ISysUserService users;
    @Autowired ResumeManagementService resumes;

    @Test void roundTripsJsonbWithVersionChecksAndPublicContent() throws Exception {
        var user = users.selectUserById(1L);
        assertNotNull(user, "Development database needs its existing administrator");
        var principal = new LoginUser(user.getUserId(), user.getDeptId(), user, Set.of("*:*:*"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var response = mvc.perform(get("/system/resume").with(authentication(auth))).andExpect(status().isOk()).andReturn().getResponse();
        var current = JSON.parseObject(response.getContentAsString()).getJSONObject("data");
        long version = current.getLongValue("version");
        var request = current.toJavaObject(SaveResume.class);
        var payload = JSON.parseObject(JSON.toJSONString(request));
        payload.getJSONObject("zh").getJSONObject("basic").put("intro", "集成测试资料：事务结束后回滚");
        mvc.perform(put("/system/resume").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON).content(payload.toJSONString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(version + 1));
        mvc.perform(get("/public/resume")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.zh.basic.intro").value("集成测试资料：事务结束后回滚"))
                .andExpect(jsonPath("$.data.updateBy").doesNotExist());
        mvc.perform(put("/system/resume").with(authentication(auth)).contentType(MediaType.APPLICATION_JSON).content(payload.toJSONString()))
                .andExpect(status().isConflict());
    }
    @Test void anonymousVisitorsCanReadButCannotManage() throws Exception {
        mvc.perform(get("/public/resume")).andExpect(status().isOk()).andExpect(jsonPath("$.data.zh.education").isArray());
        mvc.perform(get("/system/resume")).andExpect(status().isUnauthorized());
        mvc.perform(put("/system/resume").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
