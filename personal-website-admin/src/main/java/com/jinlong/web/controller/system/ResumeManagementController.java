package com.jinlong.web.controller.system;

import com.jinlong.common.core.domain.AjaxResult;
import com.jinlong.system.domain.resume.ResumeRequests.SaveResume;
import com.jinlong.system.service.ResumeManagementService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class ResumeManagementController {
    private final ResumeManagementService resumes;
    public ResumeManagementController(ResumeManagementService resumes) { this.resumes = resumes; }

    @GetMapping("/public/resume")
    public ResponseEntity<AjaxResult> published() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(AjaxResult.success(resumes.published()));
    }
    @GetMapping("/system/resume")
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('website:resume:list')")
    public AjaxResult get() { return AjaxResult.success(resumes.get()); }

    @PutMapping("/system/resume")
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('website:resume:edit')")
    public AjaxResult save(@RequestBody SaveResume request) { return AjaxResult.success(resumes.save(request)); }
}
