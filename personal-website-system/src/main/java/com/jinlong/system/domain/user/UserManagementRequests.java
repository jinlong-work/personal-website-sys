package com.jinlong.system.domain.user;

/** 使用独立请求对象，避免直接绑定 SysUser 的密码、角色组及审计字段。 */
public final class UserManagementRequests {
    private UserManagementRequests() {}

    public record NewUser(String username, String nickname, String email, String phone,
            String status, String role, String password, String confirmPassword, String avatar) {
        @Override public String toString() { return "NewUser[credentials omitted]"; }
    }
    public record EditUser(String username, String nickname, String email, String phone, String status, String avatar) {}
    public record RoleChange(String role) {}
    public record StatusChange(String status) {}
    public record PasswordReset(String newPassword, String confirmPassword) {
        @Override public String toString() { return "PasswordReset[credentials omitted]"; }
    }
    public record OwnProfile(String username, String nickname, String email, String avatar) {}
    public record OwnPassword(String currentPassword, String newPassword, String confirmPassword) {
        @Override public String toString() { return "OwnPassword[credentials omitted]"; }
    }
}
