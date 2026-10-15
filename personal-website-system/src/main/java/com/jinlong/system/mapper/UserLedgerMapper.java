package com.jinlong.system.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlong.common.core.domain.entity.SysRole;
import com.jinlong.common.core.domain.entity.SysUser;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UserLedgerMapper {
    IPage<SysUser> selectLedgerPage(@Param("page") IPage<SysUser> page,
            @Param("keyword") String keyword, @Param("role") String role,
            @Param("status") String status, @Param("restricted") boolean restricted,
            @Param("actorId") Long actorId);
    List<Long> lockUsers(@Param("ids") List<Long> ids);
    int insertLedgerUser(SysUser user);
    int updateLedgerUser(SysUser user);
    int resetPassword(@Param("userId") Long userId, @Param("password") String password, @Param("updateBy") String updateBy);
    int insertRoleIfMissing(@Param("key") String key, @Param("name") String name, @Param("sort") int sort);
    SysRole selectActiveRole(@Param("key") String key);
}
