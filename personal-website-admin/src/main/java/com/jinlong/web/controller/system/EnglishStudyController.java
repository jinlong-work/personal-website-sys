package com.jinlong.web.controller.system;

import com.jinlong.common.core.domain.AjaxResult;
import com.jinlong.common.utils.SecurityUtils;
import com.jinlong.system.domain.english.IeltsStudyProgress.Rating;
import com.jinlong.system.service.EnglishStudyService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/english/study")
public class EnglishStudyController {
    private final EnglishStudyService study;

    public EnglishStudyController(EnglishStudyService study) {
        this.study = study;
    }

    @GetMapping("/progress")
    public ResponseEntity<AjaxResult> progress() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(AjaxResult.success(study.listProgress(SecurityUtils.getUserId())));
    }

    @PutMapping("/words/{wordId}")
    public ResponseEntity<AjaxResult> record(@PathVariable long wordId, @RequestBody StudyAction action) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(AjaxResult.success(
                study.record(SecurityUtils.getUserId(), wordId, action.rating(), action.version())));
    }

    public record StudyAction(Rating rating, Integer version) {}
}
