package com.jinlong.personalwebsitesys.admin.domain.dto;

/** 仅允许更新这三个字段，不允许指定用户ID、权限或状态。 */
public record ProfileUpdateRequest(String username, String nickname, String email) {}
