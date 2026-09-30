package com.infinevo.payroll.reimbursement;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.approval.ApprovalDecideRequest;
import com.infinevo.core.approval.ApprovalDecision;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.approval.ApprovalStepRepository;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.payinput.PayInput;
import com.infinevo.core.payinput.PayInputRepository;
import com.infinevo.core.payinput.PayInputService;
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
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
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
 * Integration test verifying late claim approval behavior when the current period is locked (W-35.1, spec section 7).
 * Verifies that approval redirects the pay input to the next open period and claim.posted_period records the redirection.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class ReimbursementClaimLockedPeriodIT extends AbstractIntegrationTest {

    @Autowired
    private ReimbursementClaimService claimService;

    @Autowired
    private ReimbursementRepository reimbursementRepository;

    @Autowired
    private ReimbursementClaimRepository claimRepository;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalInstanceRepository instanceRepository;

    @Autowired
    private ApprovalStepRepository stepRepository;

    @Autowired
    private PayInputService payInputService;

    @Autowired
    private PayInputRepository payInputRepository;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private UUID employeeId;
    private UUID adminEmployeeId;
    private UUID reimbursementId;

    private EmployeeResponse employee;
    private EmployeeResponse adminEmployee;

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

        TenantContext.set(TENANT_A);

        employeeId = UUID.randomUUID();
        adminEmployeeId = UUID.randomUUID();

        seedEmployee(TENANT_A, employeeId, "EMP-LOCK", "Charlie", "Brown");
        seedEmployee(TENANT_A, adminEmployeeId, "ADM-LOCK", "Admin", "User");

        employee = PayrollTestSchema.createTestEmployee(
                employeeId, TENANT_A, "EMP-LOCK", "Charlie", "Brown", "charlie@example.com");

        adminEmployee = PayrollTestSchema.createTestEmployee(
                adminEmployeeId, TENANT_A, "ADM-LOCK", "Admin", "User", "admin@example.com");

        Reimbursement reimb = new Reimbursement(TENANT_A, "test");
        reimb.setCode("INTERNET");
        reimb.setName("Broadband Allowance");
        reimb.setReimbursementType("ALLOWANCE");
        reimb.setActive(true);
        reimb = reimbursementRepository.save(reimb);
        reimbursementId = reimb.getId();

        PayrollTestApp.APPROVER_ID.set(adminEmployeeId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        PayrollTestApp.APPROVER_ID.remove();
    }

    @Test
    @DisplayName("When current period is locked, approval posts to next open period and posted_period says so")
    void currentPeriodLockedRedirectsToNextPeriod() {
        // 1. Employee Charlie submits claim
        PayrollTestApp.CURRENT_EMPLOYEE.set(employee);
        ReimbursementClaimRequest req = new ReimbursementClaimRequest(
                reimbursementId, new BigDecimal("1200.00"), LocalDate.now(), "Home broadband", null);
        ReimbursementClaimResponse submitted = claimService.submit(req);

        // 2. Lock the current period via PayInputService
        YearMonth currentPeriod = YearMonth.now();
        payInputService.lock(currentPeriod);

        // 3. Admin approves the claim
        PayrollTestApp.CURRENT_EMPLOYEE.set(adminEmployee);
        ApprovalInstance instance =
                instanceRepository.findById(submitted.approvalInstanceId()).orElseThrow();
        List<ApprovalStep> steps = transactionTemplate.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_A, instance.getId()));
        ApprovalStep step = steps.get(0);

        ApprovalDecideRequest decideReq =
                new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Approved late claim", new BigDecimal("1200.00"));
        approvalService.decide(step.getId(), decideReq);

        // 4. Verify claim row posted_period was redirected to the next open period
        YearMonth expectedNextPeriod = currentPeriod.plusMonths(1);
        ReimbursementClaim claim = transactionTemplate.execute(status ->
                claimRepository.findByTenantIdAndId(TENANT_A, submitted.id()).orElseThrow());
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getPostedPeriod())
                .as("posted_period must record the redirected open period")
                .isEqualTo(expectedNextPeriod.toString());

        // 5. Verify the pay input in the ledger landed in expectedNextPeriod
        PayInput payInput = transactionTemplate.execute(
                status -> payInputRepository.findById(claim.getPayInputId()).orElseThrow());
        assertThat(payInput.getPeriod()).isEqualTo(expectedNextPeriod);
        assertThat(payInput.getAmount()).isEqualByComparingTo(new BigDecimal("1200.00"));
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
