package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-42.2, spec section 7 — {@code TimesheetDefinitionMigrationIT}.
 *
 * <p>{@code V145} makes the {@code TIMESHEET} approval step per item: for a tenant created from now on, through the
 * seed function, and for an existing tenant whose row is still the untouched seed. A tenant who edited the row keeps
 * the edit. The script is run again here on rows put back to the old shape, as Flyway runs it over tenants that
 * already exist.
 */
@SpringBootTest(classes = ApprovalTestApp.class)
class TimesheetDefinitionMigrationIT extends AbstractIntegrationTest {

    private static final String OLD_SEED =
            "[{\"kind\": \"PROJECT_MANAGER\", \"assignee\": null, \"escalate_after_days\": 3, \"per_item\": false}]";

    @BeforeAll
    static void setupSchema() throws Exception {
        ApprovalTestSchema.apply();
    }

    @BeforeEach
    @AfterEach
    void clean() throws Exception {
        ApprovalTestSchema.clearDefinitions();
    }

    @AfterAll
    static void clearApprovalRows() throws Exception {
        // The tenants this test made stay: in a database shared with the authorization tests, a tenant has roles
        // seeded for it (a foreign key from core.role), so deleting one here is refused. What a later test could
        // trip on is their approval rows, and those are cleared.
        ApprovalTestSchema.clearAll();
    }

    @Test
    @DisplayName("A tenant created after V145 gets all seven flows, and its TIMESHEET step is per item")
    void newTenantIsSeededPerItem() throws Exception {
        UUID tenant = insertTenant();

        assertThat(scalar("SELECT count(*) FROM core.approval_definition WHERE tenant_id = ?", tenant))
                .as("the seven default flows")
                .isEqualTo("7");
        assertThat(perItem(tenant, "TIMESHEET")).isEqualTo("true");
        assertThat(perItem(tenant, "PROOF_OF_INVESTMENT"))
                .as("proof of investment's first step stays per item, and keeps its role")
                .isEqualTo("true");
        assertThat(scalar(
                        "SELECT steps->0->>'assignee' FROM core.approval_definition"
                                + " WHERE tenant_id = ? AND flow_type = 'PROOF_OF_INVESTMENT'",
                        tenant))
                .isEqualTo("hr");
        assertThat(scalar(
                        "SELECT count(*) FROM core.approval_definition"
                                + " WHERE tenant_id = ? AND flow_type <> 'TIMESHEET' AND flow_type <> 'PROOF_OF_INVESTMENT'"
                                + " AND steps::text LIKE '%\"per_item\": true%'",
                        tenant))
                .as("no other flow changed")
                .isEqualTo("0");
    }

    @Test
    @DisplayName("Run over an existing tenant, V145 flips an untouched seed row and leaves an edited one alone")
    void existingTenantsAreUpdatedOnlyWhereUntouched() throws Exception {
        UUID untouched = insertTenant();
        UUID editedSteps = insertTenant();
        UUID editedByTenant = insertTenant();

        // Put all three back as V089 left them, then let the tenants edit two of them.
        execute("UPDATE core.approval_definition SET steps = '" + OLD_SEED + "'::jsonb, updated_by = 'system'"
                + " WHERE flow_type = 'TIMESHEET'");
        execute("UPDATE core.approval_definition SET steps = jsonb_set(steps, '{0,escalate_after_days}', '7'::jsonb)"
                + " WHERE flow_type = 'TIMESHEET' AND tenant_id = '" + editedSteps + "'");
        execute("UPDATE core.approval_definition SET created_by = 'admin@tenant.test'"
                + " WHERE flow_type = 'TIMESHEET' AND tenant_id = '" + editedByTenant + "'");

        ApprovalTestSchema.applyTimesheetPerProject();

        assertThat(perItem(untouched, "TIMESHEET")).isEqualTo("true");
        assertThat(scalar(
                        "SELECT updated_by FROM core.approval_definition WHERE tenant_id = ? AND flow_type = 'TIMESHEET'",
                        untouched))
                .isEqualTo("V145");
        assertThat(perItem(editedSteps, "TIMESHEET"))
                .as("a definition whose steps were edited keeps them")
                .isEqualTo("false");
        assertThat(scalar(
                        "SELECT steps->0->>'escalate_after_days' FROM core.approval_definition"
                                + " WHERE tenant_id = ? AND flow_type = 'TIMESHEET'",
                        editedSteps))
                .isEqualTo("7");
        assertThat(perItem(editedByTenant, "TIMESHEET"))
                .as("a definition the tenant created is theirs")
                .isEqualTo("false");
    }

    @Test
    @DisplayName("Running V145 twice changes nothing the second time")
    void runningItAgainIsHarmless() throws Exception {
        UUID tenant = insertTenant();
        ApprovalTestSchema.applyTimesheetPerProject();
        ApprovalTestSchema.applyTimesheetPerProject();

        assertThat(perItem(tenant, "TIMESHEET")).isEqualTo("true");
        assertThat(scalar("SELECT count(*) FROM core.approval_definition WHERE tenant_id = ?", tenant))
                .isEqualTo("7");
    }

    private static UUID insertTenant() throws SQLException {
        UUID tenant = UUID.randomUUID();
        try (Connection conn = ApprovalTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
            ps.setObject(1, tenant);
            ps.setString(2, "V145 " + tenant);
            ps.executeUpdate();
        }
        return tenant;
    }

    private static String perItem(UUID tenant, String flowType) throws SQLException {
        try (Connection conn = ApprovalTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT steps->0->>'per_item' FROM core.approval_definition"
                                + " WHERE tenant_id = ? AND flow_type = ?")) {
            ps.setObject(1, tenant);
            ps.setString(2, flowType);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("%s row of tenant %s", flowType, tenant)
                        .isTrue();
                return rs.getString(1);
            }
        }
    }

    private static String scalar(String sql, UUID tenant) throws SQLException {
        try (Connection conn = ApprovalTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection conn = ApprovalTestSchema.migrationConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
