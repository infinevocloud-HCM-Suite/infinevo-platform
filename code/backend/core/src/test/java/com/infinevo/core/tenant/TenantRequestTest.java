package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.invitation.InvitationService;
import com.infinevo.core.setup.SetupChecklistService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * D-42: the optional {@code admin_email} on {@link TenantRequest}, and the refusals
 * {@link TenantServiceImpl} makes before it writes anything.
 */
class TenantRequestTest {

    private static TenantRequest withEmail(String email) {
        return new TenantRequest("Acme", "IN", "Asia/Kolkata", (short) 4, Set.of(), email);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("no admin email means no administrator to invite")
    void absentEmail_isNull(String email) {
        assertThat(withEmail(email).validatedAdminEmail()).isNull();
    }

    @Test
    @DisplayName("a valid admin email is trimmed and lower-cased")
    void validEmail_isNormalised() {
        assertThat(withEmail("  Admin@Acme.Example  ").validatedAdminEmail()).isEqualTo("admin@acme.example");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "admin@", "@acme.example", "admin@acme", "ad min@acme.example", "a@b@c.de"})
    @DisplayName("a malformed admin email is refused")
    void malformedEmail_isRefused(String email) {
        assertThatThrownBy(() -> withEmail(email).validatedAdminEmail())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("admin_email");
    }

    @Test
    @DisplayName("an admin email longer than core.user_invitation.email allows is refused")
    void overlongEmail_isRefused() {
        String email = "a".repeat(250) + "@acme.example";
        assertThatThrownBy(() -> withEmail(email).validatedAdminEmail()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("JSON field admin_email binds to the request; leaving it out leaves it null")
    void json_bindsAdminEmail() throws Exception {
        ObjectMapper json = new ObjectMapper();
        TenantRequest with = json.readValue(
                "{\"name\":\"Acme\",\"country_code\":\"IN\",\"admin_email\":\"boss@acme.example\"}",
                TenantRequest.class);
        assertThat(with.adminEmail()).isEqualTo("boss@acme.example");
        assertThat(with.countryCode()).isEqualTo("IN");

        TenantRequest without = json.readValue("{\"name\":\"Acme\"}", TenantRequest.class);
        assertThat(without.adminEmail()).isNull();
    }

    @Test
    @DisplayName("provisioning refuses a malformed admin email before touching the database")
    void service_refusesMalformedEmail_beforeWriting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InvitationService invitations = mock(InvitationService.class);
        TenantServiceImpl service = new TenantServiceImpl(jdbc, (SetupChecklistService) null, invitations);

        assertThatThrownBy(() -> service.provisionTenant(withEmail("nope"), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc, invitations);
    }

    @Test
    @DisplayName("provisioning refuses an admin email with no inviter before touching the database")
    void service_refusesEmailWithoutActor_beforeWriting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InvitationService invitations = mock(InvitationService.class);
        TenantServiceImpl service = new TenantServiceImpl(jdbc, (SetupChecklistService) null, invitations);

        assertThatThrownBy(() -> service.provisionTenant(withEmail("boss@acme.example")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inviter");
        verifyNoInteractions(jdbc, invitations);
    }

    @Test
    @DisplayName("provisioning refuses an admin email when invitation emails are not configured")
    void service_refusesEmailWhenNoEmailCanBeSent_beforeWriting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InvitationService invitations = mock(InvitationService.class);
        when(invitations.canSendInvitationEmail()).thenReturn(false);
        TenantServiceImpl service = new TenantServiceImpl(jdbc, (SetupChecklistService) null, invitations);

        assertThatThrownBy(() -> service.provisionTenant(withEmail("boss@acme.example"), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVITATION_LINK_BASE_URL");
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("provisioning refuses an admin email when no invitation service is available")
    void service_refusesEmailWithoutInvitationService_beforeWriting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantServiceImpl service = new TenantServiceImpl(jdbc, (SetupChecklistService) null, (InvitationService) null);

        assertThatThrownBy(() -> service.provisionTenant(withEmail("boss@acme.example"), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }
}
