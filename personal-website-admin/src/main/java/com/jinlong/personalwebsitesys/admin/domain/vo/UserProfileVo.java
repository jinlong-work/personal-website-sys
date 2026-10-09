package com.jinlong.personalwebsitesys.admin.domain.vo;

import com.jinlong.personalwebsitesys.admin.domain.SysUser;
import java.time.OffsetDateTime;

/** 对外用户资料，显式排除密码哈希，BIGINT 主键以字符串返回。 */
public record UserProfileVo(String id, String username, String nickname, String email,
                            String role, String status, OffsetDateTime lastLoginAt,
                            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static UserProfileVo from(SysUser user) {
        return new UserProfileVo(user.getId().toString(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.getRole(), user.getStatus(), user.getLastLoginAt(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
