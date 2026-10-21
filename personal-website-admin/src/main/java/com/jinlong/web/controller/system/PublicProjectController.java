package com.jinlong.web.controller.system;

import com.jinlong.common.core.domain.AjaxResult;
import com.jinlong.system.service.ProjectManagementService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicProjectController {
    private final ProjectManagementService projects;
    public PublicProjectController(ProjectManagementService projects) { this.projects = projects; }

    @GetMapping("/public/projects")
    public ResponseEntity<AjaxResult> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(AjaxResult.success(projects.published()));
    }
}
