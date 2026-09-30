package com.infinevo.payroll.reimbursement;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.component.Reimbursement;
import com.infinevo.payroll.component.ReimbursementRepository;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * Integration tests verifying Row-Level Security on payroll.employee_reimbursement_request (W-35.1, spec section 7).
 * Verifies as app_user that Tenant A cannot read, query, or insert Tenant B's claims.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class ReimbursementClaimRlsIT extends AbstractIntegrationTest {

    @Autowired
    private ReimbursementClaimService claimService;

    @Autowired
    private ReimbursementRepository reimbursementRepository;

    @Autowired
    private ReimbursementClaimRepository claimRepository;

    private UUID employeeBId;
    private UUID reimbursementBId;
    private UUID claimBId;

    @BeforeAll
    static void initSchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();

        // Seed Tenant B Employee & Reimbursement Component
        TenantContext.set(TENANT_B);
        employeeBId = UUID.randomUUID();
        seedEmployee(TENANT_B, employeeBId, "EMP-B", "Brian", "TenantB");

        Reimbursement reimbB = new Reimbursement(TENANT_B, "test");
        reimbB.setCode("MED-B");
        reimbB.setName("Medical B");
        reimbB.setReimbursementType("MEDICAL");
        reimbB.setActive(true);
        reimbB = reimbursementRepository.save(reimbB);
        reimbursementBId = reimbB.getId();

        EmployeeResponse empBResponse = PayrollTestSchema.createTestEmployee(
                employeeBId, TENANT_B, "EMP-B", "Brian", "TenantB", "brian@tenantb.com");
        PayrollTestApp.CURRENT_EMPLOYEE.set(empBResponse);

        ReimbursementClaimRequest reqB = new ReimbursementClaimRequest(
                reimbursementBId, new BigDecimal("1200.00"), LocalDate.now(), "Medical bill", null);
        ReimbursementClaimResponse createdB = claimService.submit(reqB);
        claimBId = createdB.id();

        TenantContext.clear();
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
    }

    @Test
    @DisplayName("Tenant A cannot read Tenant B claim by id or in paged list via service")
    void tenantACannotReadTenantBClaimViaService() {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> claimService.get(claimBId)).isInstanceOf(ReimbursementClaimNotFoundException.class);

        Page<ReimbursementClaimResponse> page = claimService.list(null, null, null, null, PageRequest.of(0, 50));
        assertThat(page.getContent()).noneMatch(c -> c.id().equals(claimBId));
    }

    @Test
    @DisplayName("As app_user, RLS blocks Tenant A from reading, updating, or inserting Tenant B's claim")
    void rlsBlocksCrossTenantSqlAccess() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_A);

            // 1. Tenant A cannot select Tenant B's claim
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM payroll.employee_reimbursement_request WHERE id = ?")) {
                ps.setObject(1, claimBId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1))
                            .as("Tenant A must see 0 rows for Tenant B's claim under RLS")
                            .isZero();
                }
            }

            // 2. Tenant A cannot update Tenant B's claim
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE payroll.employee_reimbursement_request SET description = 'hacked' WHERE id = ?")) {
                ps.setObject(1, claimBId);
                int updated = ps.executeUpdate();
                assertThat(updated).isZero();
            }

            // 3. Raw-SQL INSERT with tenant B's id under tenant A's context is refused
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payroll.employee_reimbursement_request "
                    + "(id, tenant_id, employee_id, reimbursement_id, requested_amount, bill_date, status, created_by, updated_by) "
                    + "VALUES (?, ?, ?, ?, 500, CURRENT_DATE, 'SUBMITTED', 'system', 'system')")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_B);
                ps.setObject(3, employeeBId);
                ps.setObject(4, reimbursementBId);

                assertThatThrownBy(ps::executeUpdate)
                        .as("Tenant A inserting with tenant_id B must be blocked by RLS WITH CHECK")
                        .isInstanceOf(SQLException.class);
            }

            // 4. Control check: bound to Tenant B
            PayrollTestSchema.bindTenant(conn, TENANT_B);
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM payroll.employee_reimbursement_request WHERE id = ?")) {
                ps.setObject(1, claimBId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    private static void seedEmployee(UUID tenantId, UUID employeeId, String code, String first, String last)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.employee (id, tenant_id, employee_number, first_name, last_name, work_email, date_of_joining, status) "
                                + "VALUES (?, ?, ?, ?, ?, ?, '2026-01-01', 'ACTIVE') ON CONFLICT DO NOTHING")) {
            ps.setObject(1, employeeId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, first);
            ps.setString(5, last);
            ps.setString(6, code.toLowerCase() + "@example.com");
            ps.executeUpdate();
        }
    }
}
