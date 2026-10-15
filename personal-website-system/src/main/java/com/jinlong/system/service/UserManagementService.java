package com.jinlong.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jinlong.common.constant.WebsiteRole;
import com.jinlong.common.core.domain.entity.SysRole;
import com.jinlong.common.core.domain.entity.SysUser;
import com.jinlong.common.exception.ServiceException;
import com.jinlong.common.utils.SecurityUtils;
import com.jinlong.common.utils.AvatarPaths;
import com.jinlong.system.domain.SysUserRole;
import com.jinlong.system.domain.user.UserManagementRequests.*;
import com.jinlong.system.mapper.SysUserRoleMapper;
import com.jinlong.system.mapper.UserLedgerMapper;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {
    private final ISysUserService users;
    private final UserLedgerMapper ledger;
    private final SysUserRoleMapper userRoles;
    private final PasswordEncoder passwords;

    public UserManagementService(ISysUserService users, UserLedgerMapper ledger,
            SysUserRoleMapper userRoles, PasswordEncoder passwords) {
        this.users = users; this.ledger = ledger; this.userRoles = userRoles; this.passwords = passwords;
    }

    public Map<String, Object> list(String keyword, String role, String status, int pageNum, int pageSize) {
        SysUser actor = currentActor();
        requireManager(actor);
        if (pageNum < 1 || pageNum > 1_000_000 || pageSize < 1 || pageSize > 100) {
            throw badRequest("分页参数不正确，每页最多100条");
        }
        String search = text(keyword, 100, "搜索内容");
        if (role != null && !role.isEmpty()) { requireRole(role); }
        if (status != null && !status.isEmpty()) { requireStatus(status); }
        boolean restricted = WebsiteRole.of(actor) != WebsiteRole.SUPER_ADMIN;
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        // 权限筛选含角色子查询，COUNT 保留关联表，与列表查询使用相同的关联范围。
        page.setOptimizeJoinOfCountSql(false);
        IPage<SysUser> result = ledger.selectLedgerPage(page, search, role, status, restricted, actor.getUserId());
        List<Map<String, Object>> rows = result.getRecords().stream().map(target -> row(actor, target)).toList();
        List<Map<String, Object>> roles = Arrays.stream(WebsiteRole.values()).map(value -> Map.<String, Object>of(
                "key", value.key(), "label", value.label(), "assignable", !restricted || value == WebsiteRole.USER)).toList();
        return Map.of("rows", rows, "total", result.getTotal(), "pageNum", result.getCurrent(), "pageSize", result.getSize(),
                "roles", roles, "canCreate", hasPermission("system:user:add"));
    }

    @Transactional
    public Map<String, Object> create(NewUser request) {
        if (request == null) { throw badRequest("请填写用户信息"); }
        SysUser actor = lockActorAndTarget(SecurityUtils.getUserId());
        requireManager(actor);
        WebsiteRole role = requireRole(request.role());
        if (WebsiteRole.of(actor) != WebsiteRole.SUPER_ADMIN && role != WebsiteRole.USER) { deny(); }
        validatePassword(request.password(), request.confirmPassword());
        SysUser target = new SysUser();
        setProfile(target, request.username(), request.nickname(), request.email(), request.phone());
        target.setAvatar(AvatarPaths.validate(request.avatar()));
        target.setStatus(requireStatus(request.status()));
        target.setPassword(passwords.encode(request.password()));
        target.setCreateBy(actor.getUserName());
        try { ledger.insertLedgerUser(target); }
        catch (DuplicateKeyException exception) { throw duplicateAccount(); }
        replaceRole(target.getUserId(), role);
        return row(actor, users.selectUserById(target.getUserId()));
    }

    @Transactional
    public Map<String, Object> update(Long userId, EditUser request) {
        if (request == null) { throw badRequest("请填写用户信息"); }
        SysUser actor = lockActorAndTarget(userId);
        requireManager(actor);
        SysUser target = requireTarget(userId);
        requireEditable(actor, target);
        String status = requireStatus(request.status());
        if (!Objects.equals(status, target.getStatus()) && !canSetStatus(actor, target)) { deny(); }
        setProfile(target, request.username(), request.nickname(), request.email(), request.phone());
        if (request.avatar() != null) { target.setAvatar(AvatarPaths.validate(request.avatar())); }
        target.setStatus(status);
        saveProfile(actor, target);
        return row(actor, users.selectUserById(userId));
    }

    @Transactional
    public void changeStatus(Long userId, StatusChange request) {
        if (request == null) { throw badRequest("请选择账号状态"); }
        SysUser actor = lockActorAndTarget(userId);
        requireManager(actor);
        SysUser target = requireTarget(userId);
        if (!canSetStatus(actor, target)) { deny(); }
        target.setStatus(requireStatus(request.status()));
        saveProfile(actor, target);
    }

    @Transactional
    public void changeRole(Long userId, RoleChange request) {
        if (request == null) { throw badRequest("请选择角色"); }
        SysUser actor = lockActorAndTarget(userId);
        if (WebsiteRole.of(actor) != WebsiteRole.SUPER_ADMIN) { deny(); }
        SysUser target = requireTarget(userId);
        if (target.isAdmin() || Objects.equals(actor.getUserId(), userId)) {
            throw new ServiceException("仅超级管理员可设置其他用户的角色，不能更改自身或内置超级管理员角色", 403);
        }
        replaceRole(userId, requireRole(request.role()));
        // 更新审计字段；权限会在目标用户下次请求时重新从数据库读取。
        saveProfile(actor, target);
    }

    @Transactional
    public void resetPassword(Long userId, PasswordReset request) {
        if (request == null) { throw badRequest("请输入新密码"); }
        SysUser actor = lockActorAndTarget(userId);
        requireManager(actor);
        requireEditable(actor, requireTarget(userId));
        validatePassword(request.newPassword(), request.confirmPassword());
        ledger.resetPassword(userId, passwords.encode(request.newPassword()), actor.getUserName());
    }

    @Transactional
    public void updateOwnProfile(OwnProfile request) {
        if (request == null) { throw badRequest("请填写账号资料"); }
        SysUser actor = lockActorAndTarget(SecurityUtils.getUserId());
        String originalName = actor.getUserName();
        setProfile(actor, request.username(), request.nickname(), request.email(), actor.getPhonenumber());
        if (request.avatar() != null) { actor.setAvatar(AvatarPaths.validate(request.avatar())); }
        actor.setUpdateBy(originalName);
        try { ledger.updateLedgerUser(actor); }
        catch (DuplicateKeyException exception) { throw duplicateAccount(); }
    }

    @Transactional
    public void changeOwnPassword(OwnPassword request) {
        if (request == null) { throw badRequest("请输入当前密码和新密码"); }
        SysUser actor = lockActorAndTarget(SecurityUtils.getUserId());
        String current = request.currentPassword();
        if (current == null || current.isEmpty() || current.getBytes(StandardCharsets.UTF_8).length > 72
                || !passwords.matches(current, actor.getPassword())) { throw badRequest("当前密码不正确"); }
        validatePassword(request.newPassword(), request.confirmPassword());
        if (passwords.matches(request.newPassword(), actor.getPassword())) { throw badRequest("新密码不能与当前密码相同"); }
        ledger.resetPassword(actor.getUserId(), passwords.encode(request.newPassword()), actor.getUserName());
    }

    private SysUser currentActor() {
        SysUser actor = users.selectUserById(SecurityUtils.getUserId());
        if (actor == null || !"0".equals(actor.getStatus()) || !"0".equals(actor.getDelFlag())) {
            throw new ServiceException("登录状态已失效，请重新登录", 401);
        }
        return actor;
    }

    private SysUser lockActorAndTarget(Long userId) {
        if (userId == null || userId <= 0) { throw badRequest("用户编号不正确"); }
        Long actorId = SecurityUtils.getUserId();
        // 所有管理写操作都按编号锁定操作人与目标，串行校验降权和目标变更。
        ledger.lockUsers(actorId.equals(userId) ? List.of(actorId) : List.of(actorId, userId));
        return currentActor();
    }

    private SysUser requireTarget(Long userId) {
        SysUser target = users.selectUserById(userId);
        if (target == null || !"0".equals(target.getDelFlag())) { throw new ServiceException("用户不存在或已被移除", 404); }
        return target;
    }

    private void requireManager(SysUser actor) {
        if (WebsiteRole.of(actor) == WebsiteRole.USER) { deny(); }
    }

    private boolean canEdit(SysUser actor, SysUser target) {
        return WebsiteRole.of(actor) == WebsiteRole.SUPER_ADMIN
                || Objects.equals(actor.getUserId(), target.getUserId())
                || (WebsiteRole.of(actor) == WebsiteRole.ADMIN && WebsiteRole.isOrdinaryAccount(target));
    }

    private void requireEditable(SysUser actor, SysUser target) { if (!canEdit(actor, target)) { deny(); } }

    private boolean canSetStatus(SysUser actor, SysUser target) {
        return hasPermission("system:user:changeStatus") && !target.isAdmin()
                && !Objects.equals(actor.getUserId(), target.getUserId()) && canEdit(actor, target);
    }

    private boolean hasPermission(String permission) {
        var permissions = SecurityUtils.getLoginUser().getPermissions();
        return permissions != null && (permissions.contains("*:*:*") || permissions.contains(permission));
    }

    private Map<String, Object> row(SysUser actor, SysUser target) {
        Map<String, Object> result = new LinkedHashMap<>();
        WebsiteRole role = WebsiteRole.of(target);
        result.put("userId", target.getUserId()); result.put("username", target.getUserName());
        result.put("nickname", target.getNickName()); result.put("email", target.getEmail());
        result.put("avatar", target.getAvatar());
        result.put("phone", target.getPhonenumber()); result.put("status", target.getStatus());
        result.put("role", role.key()); result.put("roleName", role.label());
        result.put("createTime", target.getCreateTime()); result.put("updateTime", target.getUpdateTime());
        result.put("lastLoginAt", target.getLoginDate());
        result.put("canEdit", hasPermission("system:user:edit") && canEdit(actor, target));
        result.put("canResetPassword", hasPermission("system:user:resetPwd") && canEdit(actor, target));
        result.put("canSetStatus", canSetStatus(actor, target));
        result.put("canSetRole", hasPermission("system:user:assignRole") && WebsiteRole.of(actor) == WebsiteRole.SUPER_ADMIN && !target.isAdmin()
                && !Objects.equals(actor.getUserId(), target.getUserId()));
        result.put("protectedAccount", target.isAdmin());
        return result;
    }

    private void replaceRole(Long userId, WebsiteRole role) {
        // 首次分配管理员时自动补齐角色定义，不依赖手动执行初始化脚本。
        ledger.insertRoleIfMissing(role.key(), role.label(), 4 - role.level());
        SysRole stored = ledger.selectActiveRole(role.key());
        if (stored == null) { throw badRequest("所选角色已停用，请先恢复角色配置"); }
        userRoles.deleteUserRoleByUserId(userId);
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId); relation.setRoleId(stored.getRoleId());
        userRoles.batchUserRole(List.of(relation));
    }

    private void saveProfile(SysUser actor, SysUser target) {
        target.setUpdateBy(actor.getUserName());
        try { ledger.updateLedgerUser(target); }
        catch (DuplicateKeyException exception) { throw duplicateAccount(); }
    }

    private void setProfile(SysUser target, String username, String nickname, String email, String phone) {
        String name = text(username, 100, "登录账号");
        if (!name.matches("[\\p{L}\\p{N}_][\\p{L}\\p{N}_.@-]{1,99}")) {
            throw badRequest("账号需为2至100个字符，可使用文字、数字、下划线、点、@和连字符");
        }
        String address = text(email, 254, "邮箱");
        if (!address.isEmpty() && !address.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) { throw badRequest("邮箱格式不正确"); }
        target.setUserName(name); target.setNickName(text(nickname, 100, "昵称"));
        target.setEmail(address); target.setPhonenumber(text(phone, 30, "手机号码"));
    }

    private String text(String value, int limit, String label) {
        String result = value == null ? "" : value.trim();
        if (result.length() > limit) { throw badRequest(label + "不能超过" + limit + "个字符"); }
        return result;
    }

    private void validatePassword(String password, String confirmation) {
        if (password == null || password.codePointCount(0, password.length()) < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw badRequest("密码至少8个字符，最多72个UTF-8字节");
        }
        if (!password.equals(confirmation)) { throw badRequest("两次输入的密码不一致"); }
    }

    private WebsiteRole requireRole(String key) {
        WebsiteRole role = WebsiteRole.fromKey(key);
        if (role == null) { throw badRequest("请选择超级管理员、管理员或普通用户"); }
        return role;
    }

    private String requireStatus(String status) {
        if (!"0".equals(status) && !"1".equals(status)) { throw badRequest("请选择正常或停用状态"); }
        return status;
    }

    private void deny() { throw new ServiceException("没有权限管理此用户或分配该角色", 403); }
    private ServiceException badRequest(String message) { return new ServiceException(message, 400); }
    private ServiceException duplicateAccount() { return new ServiceException("该登录账号已被使用，请更换账号", 409); }
}
