package com.jinlong.web.controller.system;

import com.jinlong.common.core.domain.AjaxResult;
import com.jinlong.system.domain.user.UserManagementRequests.OwnPassword;
import com.jinlong.system.domain.user.UserManagementRequests.OwnProfile;
import com.jinlong.system.service.UserManagementService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/account")
public class AccountProfileController {
    private final UserManagementService users;

    public AccountProfileController(UserManagementService users) { this.users = users; }

    @PutMapping("/profile")
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('account:profile:edit')")
    public AjaxResult profile(@RequestBody OwnProfile request) {
        users.updateOwnProfile(request);
        return AjaxResult.success("账号资料已保存");
    }

    @PutMapping("/password")
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('account:password:edit')")
    public AjaxResult password(@RequestBody OwnPassword request) {
        users.changeOwnPassword(request);
        return AjaxResult.success("密码修改成功，请重新登录");
    }
}
