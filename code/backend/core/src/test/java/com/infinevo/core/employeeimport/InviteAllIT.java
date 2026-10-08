package com.infinevo.core.employeeimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.invitation.EmployeeInvitationRequest;
import com.infinevo.core.invitation.InvitationService;
import com.infinevo.core.invitation.KeycloakProvisioningService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-73.7 §7: "Invite all without access" invites only the active employees with a work email, no account
 * and no live pending invitation — with the {@code employee} role only — and a second run invites nobody.
 */
@SpringBootTest(classes = EmployeeImportTestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class InviteAllIT extends AbstractIntegrationTest {

    @Autowired
    private EmployeeImportService importService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private InvitationService invitationService;

    @MockBean
    private KeycloakProvisioningService keycloakProvisioningService;

    @MockBean
    private DocumentService documentService;

    private UUID tenant;
    private UUID actor;

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("InviteAll " + UUID.randomUUID());
        actor = UUID.randomUUID();
        TenantContext.set(tenant);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("only employees without an account and with a work email are invited, once")
    void invitesOnlyThoseWithoutAccessAndIsIdempotent() throws SQLException {
        UUID withoutAccess = employee("A", "a@invite-all.test", EmploymentStatus.ACTIVE);
        employee("B", null, EmploymentStatus.ACTIVE);
        UUID linked = employee("C", "c@invite-all.test", EmploymentStatus.ACTIVE);
        employeeService.linkLogin(
                linked, AuthzTestSchema.insertUserAccount(tenant, "c-" + UUID.randomUUID() + "@x.test"));
        UUID alreadyInvited = employee("D", "d@invite-all.test", EmploymentStatus.ACTIVE);
        invitationService.createEmployeeInvitation(new EmployeeInvitationRequest(alreadyInvited), actor);

        assertThat(importService.countWithoutAccess()).isEqualTo(1);

        List<ImportRowResult> first =
                importService.inviteAllWithoutAccess(actor, EmployeeImportService.ProgressListener.NONE);

        assertThat(first).hasSize(1);
        assertThat(first.get(0).status()).isEqualTo(ImportRowResult.Status.OK);
        assertThat(first.get(0).employeeId()).isEqualTo(withoutAccess);
        assertThat(invitationService.employeeAccess(withoutAccess).roles())
                .extracting(r -> r.code())
                .containsExactly("employee");

        assertThat(importService.countWithoutAccess()).isZero();
        assertThat(importService.inviteAllWithoutAccess(actor, EmployeeImportService.ProgressListener.NONE))
                .isEmpty();
    }

    @Test
    @DisplayName("a terminated employee is not invited")
    void terminatedNotInvited() {
        employee("T", "t@invite-all.test", EmploymentStatus.TERMINATED);

        assertThat(importService.countWithoutAccess()).isZero();
        assertThat(importService.inviteAllWithoutAccess(actor, EmployeeImportService.ProgressListener.NONE))
                .isEmpty();
    }

    private UUID employee(String prefix, String email, EmploymentStatus status) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return employeeService
                .create(new EmployeeRequest(
                        prefix + "-" + suffix,
                        "First",
                        null,
                        "Last",
                        null,
                        LocalDate.of(2024, 1, 1),
                        status == EmploymentStatus.TERMINATED ? LocalDate.of(2025, 1, 1) : null,
                        status,
                        email == null ? null : suffix + "." + email,
                        null,
                        true,
                        null,
                        null,
                        null))
                .id();
    }
}
