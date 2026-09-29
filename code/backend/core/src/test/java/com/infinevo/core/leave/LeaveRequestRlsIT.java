package com.infinevo.core.leave;

import static com.infinevo.core.leave.LeaveTestSchema.TENANT_A;
import static com.infinevo.core.leave.LeaveTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-16.3, spec section 7 &amp; 8 — {@code LeaveRequestRlsIT}.
 *
 * <p>Verifies tenant isolation on {@code core.leave_request} and {@code core.leave_request_document}:
 * <ul>
 *   <li>Tenant A cannot read Tenant B's requests or attachments as {@code app_user}.
 *   <li>Row-level security policy {@code tenant_isolation} strictly enforces visibility at DB level.
 * </ul>
 */
@SpringBootTest(classes = LeaveTestApp.class)
class LeaveRequestRlsIT extends AbstractIntegrationTest {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveRequestDocumentRepository documentRepository;

    @Autowired
    private LeaveTypeService leaveTypeService;

    private UUID empAId;
    private UUID empBId;
    private UUID typeAId;
    private UUID typeBId;
    private UUID reqAId;
    private UUID reqBId;
    private UUID docAId;
    private UUID docBId;

    @BeforeAll
    static void applySchema() throws Exception {
        LeaveTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        LeaveTestSchema.clearAll();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        LeaveTestSchema.seedTenants();
        LeaveTestSchema.clearAll();

        empAId = LeaveTestSchema.insertEmployee(TENANT_A, "EMP-A-1", "Alice", "alice@a.test");
        empBId = LeaveTestSchema.insertEmployee(TENANT_B, "EMP-B-1", "Bob", "bob@b.test");

        TenantContext.set(TENANT_A);
        LeaveTypeResponse tA = leaveTypeService.createLeaveType(
                TENANT_A,
                new LeaveTypeRequest(
                        "Vacation A",
                        "VAC-A",
                        true,
                        LeaveUnit.DAYS,
                        true,
                        LocalDate.now().minusYears(1),
                        null));
        typeAId = tA.id();

        TenantContext.set(TENANT_B);
        LeaveTypeResponse tB = leaveTypeService.createLeaveType(
                TENANT_B,
                new LeaveTypeRequest(
                        "Vacation B",
                        "VAC-B",
                        true,
                        LeaveUnit.DAYS,
                        true,
                        LocalDate.now().minusYears(1),
                        null));
        typeBId = tB.id();

        // Insert documents into core.document directly as migration user
        docAId = UUID.randomUUID();
        docBId = UUID.randomUUID();
        insertDocument(TENANT_A, empAId, docAId, "certA.pdf");
        insertDocument(TENANT_B, empBId, docBId, "certB.pdf");

        // Insert leave requests as migration user
        reqAId = insertLeaveRequest(
                TENANT_A,
                empAId,
                typeAId,
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 4),
                new BigDecimal("3.00"));
        reqBId = insertLeaveRequest(
                TENANT_B,
                empBId,
                typeBId,
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12),
                new BigDecimal("3.00"));

        insertLeaveRequestDocument(TENANT_A, reqAId, docAId);
        insertLeaveRequestDocument(TENANT_B, reqBId, docBId);

        TenantContext.clear();
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("app_user bound to Tenant A sees only Tenant A's leave requests and documents")
    void tenantAOnlySeesOwnRequestsAndDocuments() throws SQLException {
        assertThat(LeaveTestSchema.visibleLeaveRequestCount(TENANT_A)).isEqualTo(1);
        assertThat(LeaveTestSchema.visibleLeaveRequestCount(TENANT_B)).isEqualTo(1);

        assertThat(LeaveTestSchema.visibleLeaveRequestDocumentCount(TENANT_A)).isEqualTo(1);
        assertThat(LeaveTestSchema.visibleLeaveRequestDocumentCount(TENANT_B)).isEqualTo(1);

        try (Connection conn = LeaveTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try {
                LeaveTestSchema.bindTenant(conn, TENANT_A);

                // Tenant A queries for Tenant B's request
                try (PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.leave_request WHERE id = ?")) {
                    ps.setObject(1, reqBId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertThat(rs.getInt(1)).isZero();
                    }
                }

                // Tenant A queries for Tenant B's document attachment
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM core.leave_request_document WHERE document_id = ?")) {
                    ps.setObject(1, docBId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertThat(rs.getInt(1)).isZero();
                    }
                }

                // Tenant A queries for own request
                try (PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.leave_request WHERE id = ?")) {
                    ps.setObject(1, reqAId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertThat(rs.getInt(1)).isEqualTo(1);
                    }
                }
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("leave request repository scopes queries by bound tenant")
    void repositoryEnforcesTenancy() {
        TenantContext.set(TENANT_A);
        Optional<LeaveRequest> aFindsA = leaveRequestRepository.findByIdAndTenantId(reqAId, TENANT_A);
        assertThat(aFindsA).isPresent();

        Optional<LeaveRequest> aFindsB = leaveRequestRepository.findByIdAndTenantId(reqBId, TENANT_A);
        assertThat(aFindsB).isEmpty();

        List<LeaveRequestDocument> aDocs = documentRepository.findByTenantIdAndLeaveRequestId(TENANT_A, reqAId);
        assertThat(aDocs).hasSize(1);
        assertThat(aDocs.get(0).getDocumentId()).isEqualTo(docAId);

        List<LeaveRequestDocument> bDocsFromA = documentRepository.findByTenantIdAndLeaveRequestId(TENANT_A, reqBId);
        assertThat(bDocsFromA).isEmpty();
    }

    private void insertDocument(UUID tenantId, UUID employeeId, UUID docId, String fileName) throws SQLException {
        try (Connection conn = LeaveTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.document (id, tenant_id, employee_id, kind, file_name, content_type, size_bytes, blob_container, blob_path, checksum_sha256) "
                                + "VALUES (?, ?, ?, 'LEAVE_ATTACHMENT', ?, 'application/pdf', 1024, 'docs', 'docs/' || ?, 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')")) {
            ps.setObject(1, docId);
            ps.setObject(2, tenantId);
            ps.setObject(3, employeeId);
            ps.setString(4, fileName);
            ps.setString(5, fileName);
            ps.executeUpdate();
        }
    }

    private UUID insertLeaveRequest(
            UUID tenantId, UUID employeeId, UUID leaveTypeId, LocalDate from, LocalDate to, BigDecimal days)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection conn = LeaveTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.leave_request (id, tenant_id, employee_id, leave_type_id, from_date, to_date, is_half_day, working_days, status, reason, created_by, updated_by) "
                                + "VALUES (?, ?, ?, ?, ?, ?, false, ?, 'PENDING', 'Trip', 'system', 'system')")) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, employeeId);
            ps.setObject(4, leaveTypeId);
            ps.setObject(5, from);
            ps.setObject(6, to);
            ps.setBigDecimal(7, days);
            ps.executeUpdate();
        }
        return id;
    }

    private void insertLeaveRequestDocument(UUID tenantId, UUID reqId, UUID docId) throws SQLException {
        try (Connection conn = LeaveTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.leave_request_document (id, tenant_id, leave_request_id, document_id, created_by, updated_by) "
                                + "VALUES (?, ?, ?, ?, 'system', 'system')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setObject(3, reqId);
            ps.setObject(4, docId);
            ps.executeUpdate();
        }
    }
}
