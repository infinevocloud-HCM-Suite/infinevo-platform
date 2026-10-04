package com.infinevo.payroll.reimbursement;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.approval.ApprovalDecideRequest;
import com.infinevo.core.approval.ApprovalDecision;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.approval.ApprovalStepRepository;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.payinput.PayInput;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputRepository;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.component.Reimbursement;
import com.infinevo.payroll.component.ReimbursementRepository;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
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
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * End-to-end integration tests for reimbursement claims lifecycle (W-35.1, spec section 7).
 * Verifies submission, approval engine routing, ledger post, rejection, and ownership isolation.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class ReimbursementClaimIT extends AbstractIntegrationTest {

    private static final String SUBMIT_OWN = "payroll.reimbursement_claim.submit_own";

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
    private PayInputRepository payInputRepository;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private UUID employee1Id;
    private UUID employee2Id;
    private UUID adminEmployeeId;
    private UUID reimbursementId;

    private EmployeeResponse employee1;
    private EmployeeResponse employee2;
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

        employee1Id = UUID.randomUUID();
        employee2Id = UUID.randomUUID();
        adminEmployeeId = UUID.randomUUID();

        seedEmployee(TENANT_A, employee1Id, "EMP-001", "Alice", "Smith");
        seedEmployee(TENANT_A, employee2Id, "EMP-002", "Bob", "Jones");
        seedEmployee(TENANT_A, adminEmployeeId, "ADM-001", "Admin", "User");

        employee1 = PayrollTestSchema.createTestEmployee(
                employee1Id, TENANT_A, "EMP-001", "Alice", "Smith", "alice@example.com");

        employee2 = PayrollTestSchema.createTestEmployee(
                employee2Id, TENANT_A, "EMP-002", "Bob", "Jones", "bob@example.com");

        adminEmployee = PayrollTestSchema.createTestEmployee(
                adminEmployeeId, TENANT_A, "ADM-001", "Admin", "User", "admin@example.com");

        Reimbursement reimb = new Reimbursement(TENANT_A, "test");
        reimb.setCode("TRAVEL");
        reimb.setName("Travel Reimbursement");
        reimb.setReimbursementType("TRAVEL");
        reimb.setMaxLimit(new BigDecimal("5000.00"));
        reimb.setActive(true);
        reimb = reimbursementRepository.save(reimb);
        reimbursementId = reimb.getId();

        // Assign step approver resolver to adminEmployeeId
        PayrollTestApp.APPROVER_ID.set(adminEmployeeId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        PayrollTestApp.APPROVER_ID.remove();
    }

    @Test
    @DisplayName(
            "Submit creates one claim row and one approval_instance with claim as subject; approving with 1500 on 2000 writes ledger row for 1500")
    void submitAndApprovePartialWritesLedgerRow() {
        // 1. Employee 1 submits a claim for 2000
        PayrollTestApp.CURRENT_EMPLOYEE.set(employee1);

        ReimbursementClaimRequest req = new ReimbursementClaimRequest(
                reimbursementId, new BigDecimal("2000.00"), LocalDate.now(), "Client site travel", null);
        ReimbursementClaimResponse submitted = claimService.submit(req);

        assertThat(submitted.id()).isNotNull();
        assertThat(submitted.status()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(submitted.approvalInstanceId()).isNotNull();

        // Assert one approval_instance created with claim as subject
        ApprovalInstance instance =
                instanceRepository.findById(submitted.approvalInstanceId()).orElseThrow();
        assertThat(instance.getSubjectTable()).isEqualTo("payroll.employee_reimbursement_request");
        assertThat(instance.getSubjectId()).isEqualTo(submitted.id());
        assertThat(instance.getSubjectEmployeeId()).isEqualTo(employee1Id);

        // 2. Admin decides APPROVE with approvedAmount = 1500
        PayrollTestApp.CURRENT_EMPLOYEE.set(adminEmployee);

        List<ApprovalStep> steps = transactionTemplate.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_A, instance.getId()));
        assertThat(steps).hasSize(1);
        ApprovalStep step = steps.get(0);

        ApprovalDecideRequest decideReq =
                new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Approved partially", new BigDecimal("1500.00"));
        approvalService.decide(step.getId(), decideReq);

        // 3. Verify claim row updated
        ReimbursementClaim claim = transactionTemplate.execute(status ->
                claimRepository.findByTenantIdAndId(TENANT_A, submitted.id()).orElseThrow());
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getApprovedAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(claim.getPayInputId()).isNotNull();
        assertThat(claim.getPostedPeriod()).isEqualTo(YearMonth.now().toString());
        assertThat(claim.getApprovedBy()).isEqualTo(adminEmployeeId);
        assertThat(claim.getApprovedAt()).isNotNull();

        // 4. Verify core.pay_input row exists with amount 1500
        PayInput payInput = transactionTemplate.execute(
                status -> payInputRepository.findById(claim.getPayInputId()).orElseThrow());
        assertThat(payInput.getEmployeeId()).isEqualTo(employee1Id);
        assertThat(payInput.getAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(payInput.getKind()).isEqualTo(PayInputKind.REIMBURSEMENT);
        assertThat(payInput.getPeriod()).isEqualTo(YearMonth.now());
        assertThat(payInput.getSourceModule()).isEqualTo("payroll");
        assertThat(payInput.getSourceRef()).isEqualTo("reimbursement_claim:" + claim.getId());
    }

    @Test
    @DisplayName("Rejecting a submitted claim leaves status REJECTED and no pay input ledger row")
    void rejectingClaimLeavesNoLedgerRow() {
        PayrollTestApp.CURRENT_EMPLOYEE.set(employee1);

        ReimbursementClaimRequest req = new ReimbursementClaimRequest(
                reimbursementId, new BigDecimal("800.00"), LocalDate.now(), "Dinner with team", null);
        ReimbursementClaimResponse submitted = claimService.submit(req);

        // Admin decides REJECT
        PayrollTestApp.CURRENT_EMPLOYEE.set(adminEmployee);

        ApprovalInstance instance =
                instanceRepository.findById(submitted.approvalInstanceId()).orElseThrow();
        List<ApprovalStep> steps = transactionTemplate.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_A, instance.getId()));
        ApprovalStep step = steps.get(0);

        ApprovalDecideRequest decideReq =
                new ApprovalDecideRequest(ApprovalDecision.REJECTED, "Alcohol not covered under policy");
        approvalService.decide(step.getId(), decideReq);

        ReimbursementClaim claim = transactionTemplate.execute(status ->
                claimRepository.findByTenantIdAndId(TENANT_A, submitted.id()).orElseThrow());
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(claim.getRemarks()).isEqualTo("Alcohol not covered under policy");
        assertThat(claim.getPayInputId()).isNull();

        // Verify no ledger row for this claim
        List<PayInput> inputs = transactionTemplate.execute(
                status -> payInputRepository.findByTenantIdAndEmployeeIdAndPeriodAndRunRefIsNull(
                        TENANT_A, employee1Id, YearMonth.now()));
        assertThat(inputs).noneMatch(p -> ("reimbursement_claim:" + claim.getId()).equals(p.getSourceRef()));
    }

    @Test
    @DisplayName("GET /api/v1/me/reimbursement-claims/{id} from another employee returns 404")
    void getOwnFromAnotherEmployeeIs404() {
        // Employee 1 submits claim
        PayrollTestApp.CURRENT_EMPLOYEE.set(employee1);
        ReimbursementClaimRequest req = new ReimbursementClaimRequest(
                reimbursementId, new BigDecimal("350.00"), LocalDate.now(), "Stationery", null);
        ReimbursementClaimResponse submitted = claimService.submit(req);

        // Employee 2 tries to fetch Employee 1's claim via getOwn
        PayrollTestApp.CURRENT_EMPLOYEE.set(employee2);
        assertThatThrownBy(() -> claimService.getOwn(submitted.id()))
                .isInstanceOf(ReimbursementClaimNotFoundException.class)
                .hasMessageContaining("Reimbursement claim not found: " + submitted.id());

        // Employee 2's own list should be empty
        List<ReimbursementClaimResponse> bobClaims = claimService.listOwn();
        assertThat(bobClaims).noneMatch(c -> c.id().equals(submitted.id()));
    }

    @Test
    @DisplayName(
            "GET /api/v1/me/reimbursement-claims/components: an employee gets 200 with the active, undeleted, own-tenant components only")
    void componentsForEmployee() throws Exception {
        Reimbursement retired = new Reimbursement(TENANT_A, "test");
        retired.setCode("FUEL");
        retired.setName("Fuel");
        retired.setReimbursementType("FUEL");
        retired.setActive(false);
        reimbursementRepository.save(retired);
        Reimbursement deleted = new Reimbursement(TENANT_A, "test");
        deleted.setCode("PHONE");
        deleted.setName("Phone");
        deleted.setReimbursementType("PHONE");
        deleted.setActive(true);
        deleted.setDeleted(true);
        reimbursementRepository.save(deleted);
        TenantContext.set(TENANT_B);
        Reimbursement theirs = new Reimbursement(TENANT_B, "test");
        theirs.setCode("TRAVEL_B");
        theirs.setName("Their travel");
        theirs.setReimbursementType("TRAVEL");
        theirs.setActive(true);
        reimbursementRepository.save(theirs);
        TenantContext.set(TENANT_A);

        PayrollTestApp.CURRENT_EMPLOYEE.set(employee1);
        guardedMvc(SUBMIT_OWN)
                .perform(get("/api/v1/me/reimbursement-claims/components"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(reimbursementId.toString()))
                .andExpect(jsonPath("$.data[0].code").value("TRAVEL"))
                .andExpect(jsonPath("$.data[0].name").value("Travel Reimbursement"))
                .andExpect(jsonPath("$.data[0].max_limit").value(5000.0));
    }

    @Test
    @DisplayName("GET /components: a login linked to no employee gets 403; without submit_own it is 403 too")
    void componentsRefusedForUnlinkedLogin() throws Exception {
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        guardedMvc(SUBMIT_OWN)
                .perform(get("/api/v1/me/reimbursement-claims/components"))
                .andExpect(status().isForbidden());

        PayrollTestApp.CURRENT_EMPLOYEE.set(employee1);
        guardedMvc("payroll.reimbursement_claim.read_own")
                .perform(get("/api/v1/me/reimbursement-claims/components"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Claim rows carry employee_name on the officer list, the officer read, the own list and the own read")
    void rowsCarryEmployeeName() throws Exception {
        PayrollTestApp.CURRENT_EMPLOYEE.set(employee1);
        ReimbursementClaimResponse alices = claimService.submit(new ReimbursementClaimRequest(
                reimbursementId, new BigDecimal("120.00"), LocalDate.now(), "Taxi", null));
        assertThat(alices.employeeName()).isEqualTo("Alice Smith");
        PayrollTestApp.CURRENT_EMPLOYEE.set(employee2);
        claimService.submit(
                new ReimbursementClaimRequest(reimbursementId, new BigDecimal("80.00"), LocalDate.now(), "Bus", null));

        List<ReimbursementClaimResponse> page =
                claimService.list(null, null, null, null, PageRequest.of(0, 25)).getContent();
        assertThat(page)
                .extracting(ReimbursementClaimResponse::employeeName)
                .containsExactlyInAnyOrder("Alice Smith", "Bob Jones");
        assertThat(claimService.get(alices.id()).employeeName()).isEqualTo("Alice Smith");
        assertThat(claimService.listOwn())
                .extracting(ReimbursementClaimResponse::employeeName)
                .containsExactly("Bob Jones");

        PayrollTestApp.CURRENT_EMPLOYEE.set(employee1);
        assertThat(claimService.getOwn(alices.id()).employeeName()).isEqualTo("Alice Smith");
        guardedMvc("payroll.reimbursement_claim.read_own")
                .perform(get("/api/v1/me/reimbursement-claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].employee_name").value("Alice Smith"));
    }

    /** The real controller and service behind the real {@link RequiresActionAspect}; {@code held} is the one action granted. */
    private MockMvc guardedMvc(String held) {
        PermissionService permissionService = mock(PermissionService.class);
        given(permissionService.holds(anyString())).willAnswer(inv -> held.equals(inv.getArgument(0)));
        doAnswer(invocation -> {
                    String action = invocation.getArgument(0);
                    if (!held.equals(action)) {
                        throw new PermissionDeniedException(action);
                    }
                    return null;
                })
                .when(permissionService)
                .require(any());
        AspectJProxyFactory factory = new AspectJProxyFactory(new ReimbursementClaimController(claimService));
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));
        return MockMvcBuilders.standaloneSetup((ReimbursementClaimController) factory.getProxy())
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
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
