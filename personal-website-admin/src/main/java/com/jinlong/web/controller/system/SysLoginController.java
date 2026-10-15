package com.jinlong.web.controller.system;

import com.jinlong.common.constant.Constants;
import com.jinlong.common.core.domain.AjaxResult;
import com.jinlong.common.core.domain.model.LoginBody;
import com.jinlong.framework.web.service.SysLoginService;
import com.jinlong.framework.web.service.SysPermissionService;
import com.jinlong.common.core.domain.entity.SysUser;
import com.jinlong.common.core.domain.model.LoginUser;
import com.jinlong.common.utils.SecurityUtils;
import com.jinlong.system.service.ISysConfigService;
import com.jinlong.system.service.ISysMenuService;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SysLoginController {

    @Autowired
    private SysLoginService loginService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private ISysConfigService configService;

    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody loginBody) {
        AjaxResult ajax = AjaxResult.success();
        // 生成令牌
        String token = loginService.login(loginBody.getUsername(), loginBody.getPassword(), loginBody.getCode(),
                loginBody.getUuid());
        ajax.put(Constants.TOKEN, token);
        return ajax;
    }

    @GetMapping("/getInfo")
    public AjaxResult getInfo() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getUser();
        AjaxResult ajax = AjaxResult.success();
        ajax.put("user", user);
        ajax.put("roles", permissionService.getRolePermission(user));
        ajax.put("permissions", loginUser.getPermissions());
        String chrtype = configService.selectConfigByKey("sys.account.chrtype");
        ajax.put("pwdChrtype", chrtype == null || chrtype.isBlank() ? "0" : chrtype);
        ajax.put("isDefaultModifyPwd", "1".equals(configService.selectConfigByKey("sys.account.initPasswordModify")) && user.getPwdUpdateDate() == null);
        ajax.put("isPasswordExpired", passwordIsExpiration(user.getPwdUpdateDate()));
        return ajax;
    }

    @GetMapping("/getRouters")
    public AjaxResult getRouters() {
        return AjaxResult.success(menuService.buildMenus(menuService.selectMenuTreeByUserId(SecurityUtils.getUserId())));
    }

    private boolean passwordIsExpiration(Date pwdUpdateDate) {
        String value = configService.selectConfigByKey("sys.account.passwordValidateDays");
        if (value == null || !value.matches("[0-9]+")) { return false; }
        int days = Integer.parseInt(value);
        return days > 0 && (pwdUpdateDate == null || System.currentTimeMillis() - pwdUpdateDate.getTime() > TimeUnit.DAYS.toMillis(days));
    }
}
