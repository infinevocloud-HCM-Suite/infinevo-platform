package com.infinevo.payroll.statutory.settings;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * D-39: V153 splits {@code include_edli_admin_in_ctc} and {@code include_edli_admin_in_structure} into separate
 * EDLI and admin-charge switches, backfilled from the merged values.
 *
 * <p>Runs against real Postgres in one transaction: the table is put back into its V062 shape, seeded with merged
 * values, V153 is executed, the split columns are read, and everything is rolled back.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class EpfEdliAdminSplitMigrationIT extends AbstractIntegrationTest {

    @BeforeAll
    static void initSchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName("V153 backfills each split switch from its merged column and drops the merged columns")
    void backfillsSplitColumnsFromMergedColumns() throws Exception {
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            conn.setAutoCommit(false);
            try {
                restoreV062Shape(conn);
                // Tenant A: merged CTC on, structure off. Tenant B: the reverse.
                seedMerged(conn, TENANT_A, true, false);
                seedMerged(conn, TENANT_B, false, true);

                PayrollTestSchema.executeResource(conn, "db/migration/payroll/V153__epf_edli_admin_split.sql");

                assertSwitches(conn, TENANT_A, true, true, false, false);
                assertSwitches(conn, TENANT_B, false, false, true, true);
                assertThat(PayrollTestSchema.columnExists(conn, "payroll", "epf_setting", "include_edli_admin_in_ctc"))
                        .isFalse();
                assertThat(PayrollTestSchema.columnExists(
                                conn, "payroll", "epf_setting", "include_edli_admin_in_structure"))
                        .isFalse();
            } finally {
                conn.rollback();
            }
        }
    }

    private static void restoreV062Shape(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM payroll.epf_setting");
            st.execute(
                    """
                    ALTER TABLE payroll.epf_setting
                        DROP COLUMN include_edli_in_ctc,
                        DROP COLUMN include_admin_in_ctc,
                        DROP COLUMN include_edli_in_structure,
                        DROP COLUMN include_admin_in_structure,
                        ADD COLUMN include_edli_admin_in_ctc BOOLEAN NOT NULL DEFAULT false,
                        ADD COLUMN include_edli_admin_in_structure BOOLEAN NOT NULL DEFAULT false
                    """);
        }
    }

    private static void seedMerged(Connection conn, UUID tenantId, boolean inCtc, boolean inStructure)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO payroll.epf_setting (id, tenant_id, include_edli_admin_in_ctc, include_edli_admin_in_structure) "
                        + "VALUES (gen_random_uuid(), ?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setBoolean(2, inCtc);
            ps.setBoolean(3, inStructure);
            ps.executeUpdate();
        }
    }

    private static void assertSwitches(
            Connection conn,
            UUID tenantId,
            boolean edliInCtc,
            boolean adminInCtc,
            boolean edliInStructure,
            boolean adminInStructure)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT include_edli_in_ctc, include_admin_in_ctc, include_edli_in_structure, include_admin_in_structure "
                        + "FROM payroll.epf_setting WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean(1)).isEqualTo(edliInCtc);
                assertThat(rs.getBoolean(2)).isEqualTo(adminInCtc);
                assertThat(rs.getBoolean(3)).isEqualTo(edliInStructure);
                assertThat(rs.getBoolean(4)).isEqualTo(adminInStructure);
            }
        }
    }
}
