package com.jinlong.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Read-only coverage against the imported vocabulary and private audio storage. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_IELTS_INTEGRATION", matches = "true")
class EnglishVocabularyIntegrationTest {
    @Autowired MockMvc mvc;

    @Test void requiresLoginForEveryEnglishEndpoint() throws Exception {
        mvc.perform(get("/english/chapters")).andExpect(status().isUnauthorized());
        mvc.perform(get("/english/chapters/1/words")).andExpect(status().isUnauthorized());
        mvc.perform(get("/english/chapters/1/playlist")).andExpect(status().isUnauthorized());
        mvc.perform(get("/english/words/1/audio")).andExpect(status().isUnauthorized());
    }

    @Test void readsPaginatedCardsAndOrderedAudioAsAnAuthenticatedUser() throws Exception {
        var chapters = mvc.perform(get("/english/chapters").with(user("ielts-reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(22))
                .andExpect(jsonPath("$.data[0].wordCount").value(227))
                .andReturn().getResponse().getContentAsString();
        long chapterId = JSON.parseObject(chapters).getJSONArray("data").getJSONObject(0).getLongValue("id");

        var cards = mvc.perform(get("/english/chapters/{id}/words", chapterId)
                        .param("pageNum", "2").param("pageSize", "2").with(user("ielts-reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(227))
                .andExpect(jsonPath("$.data.rows.length()").value(2))
                .andExpect(jsonPath("$.data.rows[0].hasAudio").value(true))
                .andExpect(jsonPath("$.data.rows[0].audioObjectKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long wordId = JSON.parseObject(cards).getJSONObject("data").getJSONArray("rows").getJSONObject(0).getLongValue("id");

        mvc.perform(get("/english/chapters/{id}/playlist", chapterId).with(user("ielts-reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(227))
                .andExpect(jsonPath("$.data[2].id").value((int) wordId));

        byte[] audio = mvc.perform(get("/english/words/{id}/audio", wordId).with(user("ielts-reader")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(audio.length >= 128, "An actual MP3 should be returned from MinIO");
    }
}
