package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
 * An employee invitation accepted the way the public endpoint accepts it: with no tenant bound to the
 * thread. The acceptance binds the tenant itself and must keep it bound through the commit, because
 * linking the employee to its account is an audited update and the audit row needs the tenant at flush
 * and again for its own insert after commit. Found on Azure dev, 2026-10-07: the commit threw "No tenant
 * bound to this thread", the endpoint answered 500, and the Keycloak user created just before survived.
 */
@SpringBootTest(classes = InvitationTestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class EmployeeInvitationAcceptanceIT extends AbstractIntegrationTest {

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeInvitationRepository employeeInvitationRepository;

    @MockBean
    private KeycloakProvisioningService keycloakProvisioningService;

    private UUID tenant;
    private UUID adminUserId;
    private UUID employeeId;

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("EmpAccept " + UUID.randomUUID());
        adminUserId = UUID.randomUUID();
        TenantContext.set(tenant);
        employeeId = employeeService
                .create(new EmployeeRequest(
                        "EMP-" + UUID.randomUUID().toString().substring(0, 8),
                        "Jane",
                        null,
                        "Doe",
                        "FEMALE",
                        LocalDate.of(2024, 1, 1),
                        null,
                        EmploymentStatus.ACTIVE,
                        "jane.accept." + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                        "9876543210",
                        true,
                        null,
                        null,
                        null))
                .id();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("accepting with no tenant bound commits the link, the status and the employee's audit row")
    void acceptsWithNoTenantBoundOnTheThread() throws SQLException {
        String token = InvitationTokenUtils.generateToken();
        UUID invitationId = employeeInvitationRepository
                .save(new EmployeeInvitation(
                        tenant,
                        employeeId,
                        "jane.accept@example.com",
                        InvitationTokenUtils.hashToken(token),
                        Instant.now().plus(7, ChronoUnit.DAYS),
                        adminUserId,
                        "admin"))
                .getId();
        UUID keycloakUserId = UUID.randomUUID();
        when(keycloakProvisioningService.getOrCreateKeycloakUser(anyString(), any(), any()))
                .thenReturn(new KeycloakProvisioningService.ProvisioningResult(keycloakUserId, true));

        TenantContext.clear();
        invitationService.acceptInvitation(token);

        assertThat(TenantContext.isBound())
                .as("the acceptance leaves no tenant on the thread")
                .isFalse();
        verify(keycloakProvisioningService, never()).deleteKeycloakUser(any());

        TenantContext.set(tenant);
        assertThat(employeeInvitationRepository
                        .findById(invitationId)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(employeeRepository
                        .findByIdAndTenantIdAndDeletedFalse(employeeId, tenant)
                        .orElseThrow()
                        .getUserAccountId())
                .isNotNull();
        assertThat(auditRowsFor(employeeId))
                .as("the employee update was audited under its tenant")
                .isPositive();
    }

    private long auditRowsFor(UUID entityId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM core.audit_log WHERE tenant_id = ? AND entity_id = ?")) {
            ps.setObject(1, tenant);
            ps.setString(2, entityId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
