package com.infinevo.payroll.proof;

import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_A;
import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-34.1 — row-level security on the three proof tables (spec section 7). */
class ProofRlsIT extends ProofIntegrationTestBase {

    private static final List<String> TABLES = List.of(
            "payroll.employee_proof_of_investment",
            "payroll.employee_proof_item",
            "payroll.employee_proof_item_document");

    private UUID employeeB;

    private void seedBothTenants() throws Exception {
        openWindows();
        declare();
        UUID rent = item(proofService.readOwn(fy), ProofSourceKind.HOUSE_RENT).id();
        proofService.attachOwn(fy, rent, "a.pdf", pdf("a"));

        TenantContext.set(TENANT_B);
        employeeB = TaxDeclarationTestSchema.seedEmployee(TENANT_B, "EMP-B", "b@globex.com", "Bob", "Globex");
        actAs(employeeB);
        openWindows();
        declare(employeeB);
        UUID rentB = item(proofService.readOwn(fy), ProofSourceKind.HOUSE_RENT).id();
        proofService.attachOwn(fy, rentB, "b.pdf", pdf("b"));
        TenantContext.set(TENANT_A);
        actAs(employeeId);
    }

    @Test
    @DisplayName("An unbound app_user connection sees no proof rows at all")
    void unboundSeesNothing() throws Exception {
        seedBothTenants();
        try (Connection conn = TaxDeclarationTestSchema.appConnection();
                var st = conn.createStatement()) {
            for (String table : TABLES) {
                try (ResultSet rs = st.executeQuery("SELECT count(*) FROM " + table)) {
                    rs.next();
                    assertThat(rs.getInt(1)).as(table).isZero();
                }
            }
        }
    }

    @Test
    @DisplayName("Bound to tenant A, app_user sees only tenant A's proof, items and file links")
    void boundSeesOnlyItsTenant() throws Exception {
        seedBothTenants();
        try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
            bind(conn, TENANT_A);
            for (String table : TABLES) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT DISTINCT tenant_id FROM " + table);
                        ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as(table + " has rows").isTrue();
                    assertThat(rs.getObject(1, UUID.class)).as(table).isEqualTo(TENANT_A);
                    assertThat(rs.next()).as(table + " shows no other tenant").isFalse();
                }
            }
        }
    }

    @Test
    @DisplayName("Bound to tenant A, app_user cannot insert a proof, item or link for tenant B")
    void crossTenantInsertRejected() throws Exception {
        seedBothTenants();
        UUID proofB = scalarUuid("SELECT id FROM payroll.employee_proof_of_investment WHERE tenant_id = ?", TENANT_B);
        UUID itemB = scalarUuid("SELECT id FROM payroll.employee_proof_item WHERE tenant_id = ?", TENANT_B);
        UUID docB = scalarUuid("SELECT id FROM core.document WHERE tenant_id = ?", TENANT_B);
        UUID declarationB = scalarUuid(
                "SELECT declaration_id FROM payroll.employee_proof_of_investment WHERE tenant_id = ?", TENANT_B);

        try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
            conn.setAutoCommit(true);
            bind(conn, TENANT_A);

            assertRlsRefuses(
                    conn,
                    "INSERT INTO payroll.employee_proof_of_investment (id, tenant_id, employee_id, declaration_id,"
                            + " financial_year) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    TENANT_B,
                    employeeB,
                    declarationB,
                    "2099-2100");
            assertRlsRefuses(
                    conn,
                    "INSERT INTO payroll.employee_proof_item (id, tenant_id, proof_id, source_kind, source_line_id,"
                            + " description, declared_amount) VALUES (?, ?, ?, 'SECTION_6A', ?, 'x', 1)",
                    UUID.randomUUID(),
                    TENANT_B,
                    proofB,
                    UUID.randomUUID());
            assertRlsRefuses(
                    conn,
                    "INSERT INTO payroll.employee_proof_item_document (id, tenant_id, item_id, document_id)"
                            + " VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(),
                    TENANT_B,
                    itemB,
                    docB);
        }
    }

    @Test
    @DisplayName("Through the services, tenant A cannot reach tenant B's proof or its files")
    void servicesStayInTheirTenant() throws Exception {
        seedBothTenants();
        UUID itemB = scalarUuid("SELECT id FROM payroll.employee_proof_item WHERE tenant_id = ?", TENANT_B);
        UUID docB = scalarUuid(
                "SELECT document_id FROM payroll.employee_proof_item_document WHERE tenant_id = ?", TENANT_B);

        assertThatThrownBy(() -> proofService.read(employeeB, fy)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> proofService.openDocumentOwn(fy, itemB, docB))
                .isInstanceOf(ProofNotFoundException.class);
        assertThatThrownBy(() -> proofService.updateItemOwn(
                        fy, itemB, new ProofItemUpdateRequest(java.math.BigDecimal.ONE, null)))
                .isInstanceOf(ProofNotFoundException.class);
    }

    private static void bind(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    private static void assertRlsRefuses(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            assertThatThrownBy(ps::executeUpdate)
                    .isInstanceOfSatisfying(
                            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
        }
    }

    private static UUID scalarUuid(String sql, UUID tenant) throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as(sql).isTrue();
                return rs.getObject(1, UUID.class);
            }
        }
    }
}
