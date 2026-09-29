package com.infinevo.payroll.taxdeclaration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test verifying that V070 action codes and role grants are seeded properly (W-32.1, spec §7).
 *
 * <p>Validates:
 * <ul>
 *   <li>The four tax declaration action codes exist in {@code reference.action}</li>
 *   <li>{@code payroll-officer} holds {@code payroll.tax_declaration.read} and {@code manage}</li>
 *   <li>{@code employee} holds {@code payroll.tax_declaration.read_own} and {@code declare_own}</li>
 * </ul>
 */
@SpringBootTest(classes = PayrollTestApp.class)
class TaxDeclarationActionSeedIT extends AbstractIntegrationTest {

    /**
     * The four statutory tax declaration action codes introduced and managed by W-32.1 (V070).
     */
    private static final List<String> V070_SPEC_ACTIONS = List.of(
            "payroll.tax_declaration.read",
            "payroll.tax_declaration.manage",
            "payroll.tax_declaration.read_own",
            "payroll.tax_declaration.declare_own");

    /**
     * Complete set of tax declaration actions in the catalogue: the four V070 actions plus
     * the two pre-existing actions from V020 ('submit' and 'verify').
     */
    private static final List<String> ALL_CATALOGUE_ACTIONS = List.of(
            "payroll.tax_declaration.read",
            "payroll.tax_declaration.manage",
            "payroll.tax_declaration.read_own",
            "payroll.tax_declaration.declare_own",
            "payroll.tax_declaration.submit",
            "payroll.tax_declaration.verify");

    @BeforeAll
    static void applySchema() throws Exception {
        TaxDeclarationTestSchema.apply();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        TaxDeclarationTestSchema.seedTenants();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("The four W-32.1 action codes exist in reference.action as required by spec §7")
    void fourSpecActionCodesExistInReferenceTable() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT code FROM reference.action WHERE code LIKE 'payroll.tax_declaration.%' ORDER BY code");
                ResultSet rs = ps.executeQuery()) {
            Set<String> found = new HashSet<>();
            while (rs.next()) {
                found.add(rs.getString(1));
            }
            assertThat(found)
                    .as("V070 must seed the 4 statutory tax declaration actions")
                    .containsAll(V070_SPEC_ACTIONS);
        }
    }

    @Test
    @DisplayName("All six catalogue actions (4 from V070 + 2 from V020) are accounted for")
    void allSixCatalogueActionsAccountedFor() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT code FROM reference.action WHERE code LIKE 'payroll.tax_declaration.%' ORDER BY code");
                ResultSet rs = ps.executeQuery()) {
            Set<String> found = new HashSet<>();
            while (rs.next()) {
                found.add(rs.getString(1));
            }
            assertThat(found)
                    .as("reference.action contains exactly 4 V070 spec actions + 2 pre-existing V020 actions")
                    .containsExactlyInAnyOrderElementsOf(ALL_CATALOGUE_ACTIONS);
        }
    }

    @Test
    @DisplayName("payroll-officer holds read and manage actions in the tenant (W-32.1 spec §7)")
    void payrollOfficerHoldsReadAndManage() throws SQLException {
        Set<String> officerActions = getRoleActions(TaxDeclarationTestSchema.TENANT_A, "payroll-officer");
        assertThat(officerActions)
                .as("payroll-officer must hold read and manage actions")
                .contains("payroll.tax_declaration.read", "payroll.tax_declaration.manage");
    }

    @Test
    @DisplayName("employee holds read_own and declare_own actions in the tenant (W-32.1 spec §7)")
    void employeeHoldsOwnActions() throws SQLException {
        Set<String> employeeActions = getRoleActions(TaxDeclarationTestSchema.TENANT_A, "employee");
        assertThat(employeeActions)
                .as("employee must hold read_own and declare_own actions")
                .contains("payroll.tax_declaration.read_own", "payroll.tax_declaration.declare_own");
    }

    @Test
    @DisplayName("apply() can run repeatedly on a shared database")
    void applyIsIdempotent() {
        assertThatCode(() -> {
                    TaxDeclarationTestSchema.apply();
                    TaxDeclarationTestSchema.apply();
                })
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName(
            "Inserting a new tenant automatically seeds tax declaration roles via database trigger without manual intervention")
    void newTenantTrigger_automaticallySeedsTaxDeclarationActionsWithoutManualCall() throws SQLException {
        UUID newTenantId = UUID.randomUUID();
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection()) {
            try {
                // Insert a brand new tenant - do NOT call seed_system_roles or seed_tax_declaration_roles manually
                try (PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
                    ps.setObject(1, newTenantId);
                    ps.setString(2, "Trigger Test Tenant " + newTenantId);
                    ps.executeUpdate();
                }

                // Verify trigger tenant_seed_tax_declaration_roles automatically granted actions to payroll-officer
                Set<String> officerActions = getRoleActions(newTenantId, "payroll-officer");
                assertThat(officerActions)
                        .as("Trigger must grant tax declaration read and manage to payroll-officer")
                        .contains("payroll.tax_declaration.read", "payroll.tax_declaration.manage");

                // Verify trigger automatically granted actions to employee
                Set<String> employeeActions = getRoleActions(newTenantId, "employee");
                assertThat(employeeActions)
                        .as("Trigger must grant tax declaration read_own and declare_own to employee")
                        .contains("payroll.tax_declaration.read_own", "payroll.tax_declaration.declare_own");
            } finally {
                cleanupTenant(conn, newTenantId);
            }
        }
    }

    @Test
    @DisplayName("A pre-existing tenant (before V070) receives tax declaration actions when V070 seed function runs")
    void preExistingTenant_receivesTaxDeclarationActionsWhenSeeded() throws SQLException {
        UUID existingTenantId = UUID.randomUUID();
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection()) {
            try {
                // Create tenant and system roles
                try (PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
                    ps.setObject(1, existingTenantId);
                    ps.setString(2, "Pre-V070 Tenant " + existingTenantId);
                    ps.executeUpdate();
                }

                // Simulate pre-V070 state by clearing any tax declaration role actions
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM core.role_action WHERE tenant_id = ? AND action_code LIKE 'payroll.tax_declaration.%'")) {
                    del.setObject(1, existingTenantId);
                    del.executeUpdate();
                }

                // Verify no tax declaration actions exist yet
                Set<String> beforeActions = getRoleActions(existingTenantId, "payroll-officer");
                assertThat(beforeActions)
                        .doesNotContain("payroll.tax_declaration.read", "payroll.tax_declaration.manage");

                // Run the V070 migration seed function (same as V070 backfill: SELECT
                // core.seed_tax_declaration_roles(t.tenant_id))
                try (PreparedStatement stmt = conn.prepareStatement("SELECT core.seed_tax_declaration_roles(?)")) {
                    stmt.setObject(1, existingTenantId);
                    stmt.execute();
                }

                // Verify actions are seeded for the pre-existing tenant
                Set<String> afterOfficerActions = getRoleActions(existingTenantId, "payroll-officer");
                assertThat(afterOfficerActions)
                        .contains("payroll.tax_declaration.read", "payroll.tax_declaration.manage");

                Set<String> afterEmployeeActions = getRoleActions(existingTenantId, "employee");
                assertThat(afterEmployeeActions)
                        .contains("payroll.tax_declaration.read_own", "payroll.tax_declaration.declare_own");
            } finally {
                cleanupTenant(conn, existingTenantId);
            }
        }
    }

    private void cleanupTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM core.role_action WHERE tenant_id = ?")) {
            del.setObject(1, tenantId);
            del.executeUpdate();
        }
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM core.role WHERE tenant_id = ?")) {
            del.setObject(1, tenantId);
            del.executeUpdate();
        }
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM core.tenant WHERE tenant_id = ?")) {
            del.setObject(1, tenantId);
            del.executeUpdate();
        }
    }

    private Set<String> getRoleActions(UUID tenantId, String roleCode) throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        SELECT ra.action_code
                          FROM core.role r
                          JOIN core.role_action ra ON ra.role_id = r.id AND ra.tenant_id = r.tenant_id
                         WHERE r.tenant_id = ? AND r.code = ?
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, roleCode);
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> actions = new HashSet<>();
                while (rs.next()) {
                    actions.add(rs.getString(1));
                }
                return actions;
            }
        }
    }
}
