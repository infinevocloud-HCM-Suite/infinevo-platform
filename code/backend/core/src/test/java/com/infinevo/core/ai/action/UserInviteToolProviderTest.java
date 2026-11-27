package com.infinevo.core.ai.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.ai.help.HelpLibrary;
import com.infinevo.core.authz.Role;
import com.infinevo.core.authz.RoleRepository;
import com.infinevo.core.invitation.InvitationService;
import com.infinevo.core.invitation.UserInvitation;
import com.infinevo.core.invitation.UserInvitationRepository;
import com.infinevo.core.invitation.UserInvitationRequest;
import com.infinevo.shared.authz.PermissionService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

/** W-114.28 step 5, Tests: {@code core.propose_user_invite} and {@link UserInviteExecutor}. */
class UserInviteToolProviderTest {

    private final ObjectMapper json = new ObjectMapper();
    private RoleRepository roles;
    private UserInvitationRepository pending;
    private PermissionService permissions;
    private InvitationService invitations;
    private AdminStepsFixture f;
    private AiTool tool;
    private UserInviteExecutor exec;
    private Role hr;

    @BeforeEach
    void setUp() {
        roles = mock(RoleRepository.class);
        pending = mock(UserInvitationRepository.class);
        permissions = mock(PermissionService.class);
        invitations = mock(InvitationService.class);
        exec = new UserInviteExecutor(invitations, roles, permissions, new HelpLibrary());
        f = new AdminStepsFixture(List.of(exec));
        hr = role("hr", "HR");
        Role payroll = role("payroll-officer", "Payroll officer");
        Role platform = role("platform-admin", "Platform admin");
        when(roles.findByTenantIdOrderByCodeAsc(f.tenantId)).thenReturn(List.of(hr, payroll, platform));
        when(roles.findByTenantIdAndCode(eq(f.tenantId), anyString())).thenReturn(Optional.empty());
        when(roles.findByTenantIdAndCode(f.tenantId, "hr")).thenReturn(Optional.of(hr));
        when(roles.findByTenantIdAndCode(f.tenantId, "platform-admin")).thenReturn(Optional.of(platform));
        when(pending.findActivePendingByEmail(any(), any(), any())).thenReturn(Optional.empty());
        when(permissions.holds("core.role.assign")).thenReturn(true);
        tool = new UserInviteToolProvider(
                        roles, pending, permissions, f.actions, TransactionOperations.withoutTransaction())
                .tools()
                .get(0);
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    private Role role(String code, String name) {
        Role r = mock(Role.class);
        UUID id = UUID.randomUUID();
        when(r.getId()).thenReturn(id);
        when(r.getCode()).thenReturn(code);
        when(r.getName()).thenReturn(name);
        return r;
    }

    private AiToolResult run(String input) throws Exception {
        return tool.execute(f.ctx, json.readTree(input));
    }

    @Test
    @DisplayName("the tool and the action need core.user.manage, the Users screen's invite action")
    void declaration() {
        assertThat(tool.name()).isEqualTo("core.propose_user_invite");
        assertThat(tool.requiredAction()).isEqualTo("core.user.manage");
        assertThat(exec.requiredAction()).isEqualTo("core.user.manage");
        assertThat(exec.requiresEmployeeProfile()).isFalse();
    }

    @Test
    @DisplayName("roles named in words; the payload holds codes, no id; the card says what the role does")
    void propose() throws Exception {
        AiToolResult result = run("{\"email\":\" Asha@Acme.in \",\"roles\":[\"hr\",\"HR\"]}");

        assertThat(result.ok()).isTrue();
        assertThat(AdminStepsFixture.UUID_TEXT.matcher(result.data().toString()).find())
                .isFalse();
        assertThat(f.stored.get(0).getPayload().toString())
                .isEqualTo("{\"email\":\"asha@acme.in\",\"roles\":[\"hr\"]}");
        assertThat(result.data().toString())
                .contains("Invite asha@acme.in as HR")
                .contains("Act on the requests");
        verify(invitations, never()).createUserInvitation(any(), any());
    }

    @Test
    @DisplayName("an unknown role lists the company's roles, never platform-admin; it cannot be asked for")
    void unknownRole() throws Exception {
        AiToolResult result = run("{\"email\":\"a@acme.in\",\"roles\":[\"Boss\"]}");
        assertThat(result.error()).contains("Boss").contains("HR").contains("Payroll officer");
        assertThat(result.error()).doesNotContain("Platform admin");
        assertThat(run("{\"email\":\"a@acme.in\",\"roles\":[\"platform-admin\"]}")
                        .ok())
                .isFalse();
        assertThat(f.stored).isEmpty();
    }

    @Test
    @DisplayName("roles without core.role.assign, a bad email, and a waiting invitation are refused")
    void refusals() throws Exception {
        when(permissions.holds("core.role.assign")).thenReturn(false);
        assertThat(run("{\"email\":\"a@acme.in\",\"roles\":[\"HR\"]}").error())
                .isEqualTo(UserInviteExecutor.NO_ROLE_ASSIGN);
        assertThat(run("{\"email\":\"not an email\"}").ok()).isFalse();
        when(pending.findActivePendingByEmail(any(), eq("a@acme.in"), any()))
                .thenReturn(Optional.of(mock(UserInvitation.class)));
        assertThat(run("{\"email\":\"a@acme.in\"}").error()).isEqualTo(UserInviteToolProvider.PENDING);
        assertThat(f.stored).isEmpty();
    }

    @Test
    @DisplayName("Confirm calls createUserInvitation with the role ids found again and the caller as actor")
    void execute() throws Exception {
        var payload = json.readTree("{\"email\":\"asha@acme.in\",\"roles\":[\"hr\"]}");
        assertThat(exec.execute(f.ctx, payload).path("email").asText()).isEqualTo("asha@acme.in");
        ArgumentCaptor<UserInvitationRequest> sent = ArgumentCaptor.forClass(UserInvitationRequest.class);
        verify(invitations).createUserInvitation(sent.capture(), eq(f.userId));
        assertThat(sent.getValue().roleIds()).isEqualTo(Set.of(hr.getId()));

        when(permissions.holds("core.role.assign")).thenReturn(false);
        assertThatThrownBy(() -> exec.execute(f.ctx, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(UserInviteExecutor.NO_ROLE_ASSIGN);

        when(permissions.holds("core.role.assign")).thenReturn(true);
        var gone = json.readTree("{\"email\":\"asha@acme.in\",\"roles\":[\"old-role\"]}");
        assertThatThrownBy(() -> exec.execute(f.ctx, gone)).isInstanceOf(IllegalStateException.class);
        var platform = json.readTree("{\"email\":\"asha@acme.in\",\"roles\":[\"platform-admin\"]}");
        assertThatThrownBy(() -> exec.execute(f.ctx, platform)).isInstanceOf(IllegalStateException.class);
    }
}
