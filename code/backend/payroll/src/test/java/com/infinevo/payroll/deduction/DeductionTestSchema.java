package com.infinevo.payroll.deduction;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentResponse;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fixtures for the W-35.2 integration tests: {@link PayrollTestSchema}'s tables, which include
 * {@code V100} and {@code V101}; rows written as {@code migration_user}.
 */
final class DeductionTestSchema {

    static final UUID TENANT_A = PayrollTestSchema.TENANT_A;
    static final UUID TENANT_B = PayrollTestSchema.TENANT_B;

    private DeductionTestSchema() {}

    static void apply() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    static void clean() throws SQLException {
        PayrollTestSchema.cleanTables();
        PayrollTestApp.TEST_DOCUMENTS.clear();
    }

    static UUID insertEmployee(UUID tenantId, String number, String status) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee
                            (tenant_id, employee_number, first_name, last_name, date_of_joining, status,
                             termination_date, created_by, updated_by)
                        VALUES (?, ?, 'First', 'Last', DATE '2025-01-01', ?, ?, 'test', 'test')
                        RETURNING id
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, number);
            ps.setString(3, status);
            ps.setObject(4, "TERMINATED".equals(status) ? LocalDate.of(2026, 1, 31) : null);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    /** A document row for the foreign key, and the same document in the test app's document service. */
    static UUID insertDocument(UUID tenantId, UUID employeeId, DocumentKind kind) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.document
                            (id, tenant_id, employee_id, kind, file_name, content_type, size_bytes,
                             blob_container, blob_path, checksum_sha256)
                        VALUES (?, ?, ?, ?, 'proof.pdf', 'application/pdf', 1024, 'documents', ?, ?)
                        """)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, employeeId);
            ps.setString(4, kind.name());
            ps.setString(5, tenantId + "/" + id);
            ps.setString(6, "0".repeat(64));
            ps.executeUpdate();
        }
        PayrollTestApp.TEST_DOCUMENTS.put(
                id,
                new DocumentResponse(
                        id, employeeId, kind, null, "proof.pdf", "application/pdf", 1024L, "0", Instant.now(), "test"));
        return id;
    }

    static int count(String sql, Object... params) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    static int deductionRows(UUID tenantId) throws SQLException {
        return count("SELECT count(*) FROM payroll.employee_deduction WHERE tenant_id = ?", tenantId);
    }

    static int ledgerRows(UUID tenantId) throws SQLException {
        return count("SELECT count(*) FROM core.pay_input WHERE tenant_id = ? AND kind = 'AD_HOC_DEDUCTION'", tenantId);
    }
}
