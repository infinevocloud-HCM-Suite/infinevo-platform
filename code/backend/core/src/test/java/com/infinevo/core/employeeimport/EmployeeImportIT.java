package com.infinevo.core.employeeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.invitation.EmployeeInvitation;
import com.infinevo.core.invitation.EmployeeInvitationRepository;
import com.infinevo.core.invitation.KeycloakProvisioningService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-73.7 §7: a dry run writes nothing; an import with a bad row creates the others, each with its
 * invitation and roles; duplicates in the tenant and in the file are refused; the result file has one line
 * per row. Against a real PostgreSQL as {@code app_user}, so row-level security is in play.
 */
@SpringBootTest(classes = EmployeeImportTestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class EmployeeImportIT extends AbstractIntegrationTest {

    private static final String HEADER = String.join(",", EmployeeImportParser.COLUMNS) + "\n";

    @Autowired
    private EmployeeImportService importService;

    @Autowired
    private EmployeeInvitationRepository invitationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private KeycloakProvisioningService keycloakProvisioningService;

    @MockBean
    private DocumentService documentService;

    private UUID tenant;
    private UUID actor;
    private UUID hrRole;
    private String suffix;

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("Import " + UUID.randomUUID());
        actor = UUID.randomUUID();
        hrRole = AuthzTestSchema.roleId(tenant, "hr");
        suffix = UUID.randomUUID().toString().substring(0, 6);
        insertDepartment(tenant, "ENG", "Engineering");
        TenantContext.set(tenant);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("dry run reports every row and writes nothing")
    void dryRunWritesNothing() throws SQLException {
        List<ImportRowResult> results = importService.dryRun(EmployeeImportParser.parse(HEADER
                + row("A1", "a1", "2026-04-01", "ENG", "Y", "hr")
                + row("A2", "a2", "2026-04-01", "NOPE", "N", "")
                + row("A3", "a3", "31/02/2026", "", "N", "")));

        assertThat(results)
                .extracting(ImportRowResult::status)
                .containsExactly(ImportRowResult.Status.OK, ImportRowResult.Status.ERROR, ImportRowResult.Status.ERROR);
        assertThat(results.get(0).message()).isEqualTo("Will be created and invited with hr");
        assertThat(results.get(1).message()).contains("No active department with code or name NOPE");
        assertThat(results.get(2).message()).contains("date_of_joining 31/02/2026 is not a date");
        assertThat(employeeCount(tenant)).isZero();
    }

    @Test
    @DisplayName("import with one bad row creates the others, invites the Y rows with their roles")
    void oneBadRowDoesNotStopTheOthers() throws SQLException {
        List<ImportRowResult> results = importService.importRows(
                EmployeeImportParser.parse(HEADER
                        + row("B1", "b1", "2026-04-01", "engineering", "Y", "hr;employee")
                        + row("B2", "b2", "not-a-date", "", "N", "")
                        + row("B3", "b3", "01-05-2026", "", "N", "")),
                actor,
                EmployeeImportService.ProgressListener.NONE);

        assertThat(results)
                .extracting(ImportRowResult::status)
                .containsExactly(ImportRowResult.Status.OK, ImportRowResult.Status.ERROR, ImportRowResult.Status.OK);
        assertThat(results.get(0).invitationId()).isNotNull();
        assertThat(results.get(2).invitationId()).isNull();
        assertThat(results.get(2).employeeId()).isNotNull();
        assertThat(employeeCount(tenant)).isEqualTo(2);

        EmployeeInvitation invitation = new TransactionTemplate(transactionManager)
                .execute(status -> invitationRepository
                        .findByIdAndTenantId(results.get(0).invitationId(), tenant)
                        .orElseThrow());
        assertThat(invitation.getEmployeeId()).isEqualTo(results.get(0).employeeId());
        assertThat(invitation.getRoleIds()).containsExactly(hrRole);
    }

    @Test
    @DisplayName("an employee number already in the tenant, and an email repeated in the file, are refused")
    void duplicatesRefused() throws SQLException {
        importService.importRows(
                EmployeeImportParser.parse(HEADER + row("C1", "c1", "2026-04-01", "", "N", "")),
                actor,
                EmployeeImportService.ProgressListener.NONE);

        List<ImportRowResult> results = importService.dryRun(EmployeeImportParser.parse(HEADER
                + row("C1", "c-new", "2026-04-01", "", "N", "")
                + row("C2", "c1", "2026-04-01", "", "N", "")
                + row("C3", "c3", "2026-04-01", "", "N", "")
                + row("C4", "c3", "2026-04-01", "", "N", "")
                + row("C3", "c5", "2026-04-01", "", "N", "")));

        assertThat(results)
                .extracting(ImportRowResult::status)
                .containsExactly(
                        ImportRowResult.Status.ERROR,
                        ImportRowResult.Status.ERROR,
                        ImportRowResult.Status.OK,
                        ImportRowResult.Status.ERROR,
                        ImportRowResult.Status.ERROR);
        assertThat(results.get(0).message()).contains("is already in use in this tenant");
        assertThat(results.get(1).message()).contains("already belongs to an employee in this tenant");
        assertThat(results.get(3).message()).contains("repeats row 3");
        assertThat(results.get(4).message()).contains("repeats row 3");
    }

    @Test
    @DisplayName("an unknown role and platform-admin are row errors, and nothing is written for them")
    void badRolesRefused() throws SQLException {
        List<ImportRowResult> results = importService.importRows(
                EmployeeImportParser.parse(HEADER
                        + row("D1", "d1", "2026-04-01", "", "Y", "wizard")
                        + row("D2", "d2", "2026-04-01", "", "Y", "platform-admin")
                        + row("D3", "d3", "2026-04-01", "", "N", "hr")),
                actor,
                EmployeeImportService.ProgressListener.NONE);

        assertThat(results).allMatch(r -> r.status() == ImportRowResult.Status.ERROR);
        assertThat(results.get(0).message()).contains("Role wizard does not exist");
        assertThat(results.get(1).message()).contains("platform-admin cannot be granted");
        assertThat(results.get(2).message()).contains("set give_access to Y");
        assertThat(employeeCount(tenant)).isZero();
    }

    @Test
    @DisplayName("the result file has a header and one line per row, stored as an EXPORT")
    void resultFileOneLinePerRow() throws Exception {
        AtomicReference<String> stored = new AtomicReference<>();
        UUID documentId = UUID.randomUUID();
        when(documentService.store(eq(DocumentKind.EXPORT), isNull(), any(String.class), any(InputStream.class)))
                .thenAnswer(invocation -> {
                    stored.set(new String(
                            invocation.getArgument(3, InputStream.class).readAllBytes(), StandardCharsets.UTF_8));
                    return documentId;
                });
        List<ImportRowResult> results = importService.importRows(
                EmployeeImportParser.parse(
                        HEADER + row("E1", "e1", "2026-04-01", "", "N", "") + row("E2", "e2", "bad", "", "N", "")),
                actor,
                EmployeeImportService.ProgressListener.NONE);

        UUID returned = ((EmployeeImportServiceImpl) importService).storeResult("job-1", "IMPORT", results);

        assertThat(returned).isEqualTo(documentId);
        String[] lines = stored.get().split("\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo("row,employee_number,status,message,employee_id,invitation_id");
        assertThat(lines[1]).startsWith("1,E1" + suffix + ",OK,Created,");
        assertThat(lines[2]).startsWith("2,E2" + suffix + ",ERROR,");
    }

    /** One CSV line; numbers and emails carry this test's suffix so runs never collide. */
    private String row(String number, String emailUser, String joined, String department, String access, String roles) {
        return String.join(
                        ",",
                        number + suffix,
                        "First",
                        "Last",
                        emailUser + "." + suffix + "@import.test",
                        "9876543210",
                        joined,
                        department,
                        "",
                        "",
                        access,
                        roles)
                + "\n";
    }

    private static void insertDepartment(UUID tenantId, String code, String name) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.department (tenant_id, code, name) VALUES (?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setString(2, code);
            ps.setString(3, name);
            ps.executeUpdate();
        }
    }

    private static int employeeCount(UUID tenantId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.employee WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
