package com.jinlong.personalwebsitesys.admin.domain.dto;

/** 不使用 record，避免自动生成的 toString 将密码写入日志。 */
public class LoginRequest {
    private String username;
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
