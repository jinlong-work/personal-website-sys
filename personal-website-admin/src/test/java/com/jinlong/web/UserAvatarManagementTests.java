package com.jinlong.web;

import com.jinlong.common.core.domain.entity.SysRole;
import com.jinlong.common.core.domain.entity.SysUser;
import com.jinlong.common.core.domain.model.LoginUser;
import com.jinlong.common.exception.ServiceException;
import com.jinlong.system.domain.user.UserManagementRequests.EditUser;
import com.jinlong.system.domain.user.UserManagementRequests.OwnProfile;
import com.jinlong.system.mapper.SysUserRoleMapper;
import com.jinlong.system.mapper.UserLedgerMapper;
import com.jinlong.system.service.ISysUserService;
import com.jinlong.system.service.UserManagementService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserAvatarManagementTests {
    private static final String AVATAR = "/files/avatars/" + "a".repeat(32) + ".png";
    private final ISysUserService users = mock(ISysUserService.class);
    private final UserLedgerMapper ledger = mock(UserLedgerMapper.class);
    private final UserManagementService service = new UserManagementService(users, ledger, mock(SysUserRoleMapper.class), mock(PasswordEncoder.class));
    private SysUser actor;
    private SysUser target;

    @BeforeEach
    void setUp() {
        actor = account(1L, "admin"); target = account(7L, "member"); target.setAvatar(AVATAR);
        when(users.selectUserById(1L)).thenReturn(actor);
        when(users.selectUserById(7L)).thenReturn(target);
        authenticate(actor);
    }

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void editorPersistsAndReturnsAvatarAndAllowsRemoval() {
        String next = "/files/avatars/" + "b".repeat(32) + ".png";
        assertThat(service.update(7L, new EditUser("member", "用户", "", "", "0", next)).get("avatar")).isEqualTo(next);
        verify(ledger).updateLedgerUser(argThat(user -> nextAvatar(user, 'b')));
        service.update(7L, new EditUser("member", "用户", "", "", "0", ""));
        assertThat(target.getAvatar()).isEmpty();
    }

    @Test
    void olderProfileRequestsPreserveExistingAvatar() {
        service.update(7L, new EditUser("member", "用户", "", "", "0", null));
        assertThat(target.getAvatar()).isEqualTo(AVATAR);
    }

    @Test
    void externalUrlsAndObjectTraversalAreRejectedBeforePersistence() {
        for (String path : List.of("https://example.com/a.png", "/files/avatars/../secret", "javascript:alert(1)")) {
            assertThatThrownBy(() -> service.update(7L, new EditUser("member", "用户", "", "", "0", path)))
                    .isInstanceOfSatisfying(ServiceException.class, error -> assertThat(error.getCode()).isEqualTo(400));
        }
        verify(ledger, never()).updateLedgerUser(any());
    }

    @Test
    void ordinaryUserCanSaveOwnAvatarWithoutChangingRoleOrStatus() {
        authenticate(target);
        service.updateOwnProfile(new OwnProfile("member", "用户", "", ""));
        assertThat(target.getAvatar()).isEmpty();
        assertThat(target.getStatus()).isEqualTo("0");
        verify(ledger).updateLedgerUser(target);
    }

    @Test
    void managerCannotChangeAnotherManagersAvatar() {
        SysRole role = new SysRole(); role.setRoleKey("manager"); role.setStatus("0"); role.setDelFlag("0");
        actor.setUserId(2L); actor.setRoles(List.of(role)); target.setRoles(List.of(role));
        when(users.selectUserById(2L)).thenReturn(actor);
        authenticate(actor);
        assertThatThrownBy(() -> service.update(7L, new EditUser("member", "用户", "", "", "0", "")))
                .isInstanceOfSatisfying(ServiceException.class, error -> assertThat(error.getCode()).isEqualTo(403));
        verify(ledger, never()).updateLedgerUser(any());
    }

    private boolean nextAvatar(SysUser user, char character) {
        return ("/files/avatars/" + String.valueOf(character).repeat(32) + ".png").equals(user.getAvatar());
    }

    private SysUser account(Long id, String username) {
        SysUser user = new SysUser(id); user.setUserName(username); user.setNickName("用户");
        user.setStatus("0"); user.setDelFlag("0"); user.setRoles(List.of()); return user;
    }

    private void authenticate(SysUser user) {
        LoginUser login = new LoginUser(user.getUserId(), null, user, Set.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(login, null, login.getAuthorities()));
    }
}
