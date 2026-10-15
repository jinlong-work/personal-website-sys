package com.jinlong.web.controller.system;

import com.jinlong.common.core.domain.AjaxResult;
import com.jinlong.system.domain.user.UserManagementRequests.*;
import com.jinlong.system.service.UserManagementService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/system/users")
public class UserManagementController {
    private final UserManagementService users;

    public UserManagementController(UserManagementService users) { this.users = users; }

    @GetMapping
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('system:user:list')")
    public AjaxResult list(@RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String role, @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize) {
        return AjaxResult.success(users.list(keyword, role, status, pageNum, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('system:user:add')")
    public AjaxResult create(@RequestBody NewUser request) { return AjaxResult.success(users.create(request)); }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('system:user:edit')")
    public AjaxResult update(@PathVariable Long userId, @RequestBody EditUser request) {
        return AjaxResult.success(users.update(userId, request));
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('system:user:assignRole')")
    public AjaxResult role(@PathVariable Long userId, @RequestBody RoleChange request) {
        users.changeRole(userId, request);
        return AjaxResult.success("用户角色已更新");
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('system:user:changeStatus')")
    public AjaxResult status(@PathVariable Long userId, @RequestBody StatusChange request) {
        users.changeStatus(userId, request);
        return AjaxResult.success("账号状态已更新");
    }

    @PutMapping("/{userId}/password")
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('system:user:resetPwd')")
    public AjaxResult password(@PathVariable Long userId, @RequestBody PasswordReset request) {
        users.resetPassword(userId, request);
        return AjaxResult.success("密码已重置，该用户需重新登录");
    }
}
