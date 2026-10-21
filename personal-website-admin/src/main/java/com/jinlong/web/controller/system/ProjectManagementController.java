package com.jinlong.web.controller.system;

import com.jinlong.common.core.domain.AjaxResult;
import com.jinlong.system.domain.project.ProjectRequests.*;
import com.jinlong.system.service.ProjectManagementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/projects")
public class ProjectManagementController {
    private final ProjectManagementService projects;
    public ProjectManagementController(ProjectManagementService projects) { this.projects = projects; }

    @GetMapping
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('website:project:list')")
    public AjaxResult list(@RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize) {
        return AjaxResult.success(projects.list(keyword, status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('website:project:list')")
    public AjaxResult get(@PathVariable Long id) { return AjaxResult.success(projects.get(id)); }

    @PostMapping
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('website:project:add')")
    public AjaxResult create(@RequestBody SaveProject request) { return AjaxResult.success(projects.create(request)); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('website:project:edit')")
    public AjaxResult update(@PathVariable Long id, @RequestBody SaveProject request) { return AjaxResult.success(projects.update(id, request)); }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('website:project:publish')")
    public AjaxResult status(@PathVariable Long id, @RequestBody ChangeStatus request) { return AjaxResult.success(projects.changeStatus(id, request)); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('website:project:remove')")
    public AjaxResult remove(@PathVariable Long id, @RequestParam Long version) {
        projects.remove(id, version);
        return AjaxResult.success("项目已删除");
    }
}
