package com.infinevo.payroll.fbp;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.component.Reimbursement;
import com.infinevo.payroll.component.ReimbursementRepository;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryComponentItemRequest;
import com.infinevo.payroll.salary.SalaryConflictException;
import com.infinevo.payroll.salary.SalaryVersionRequest;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
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
 * Acceptance integration tests for Flexible Benefit Plan employee declaration (W-27.2).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class FbpDeclarationIT extends AbstractIntegrationTest {

    @Autowired
    private FbpDeclarationService fbpDeclarationService;

    @Autowired
    private FbpPlanService fbpPlanService;

    @Autowired
    private EmployeeSalaryService salaryService;

    @Autowired
    private EarningRepository earningRepository;

    @Autowired
    private ReimbursementRepository reimbursementRepository;

    private UUID employeeId;
    private UUID earningFbpId;
    private UUID reimbFbpId;

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
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        PayrollTestSchema.cleanTables();

        TenantContext.set(TENANT_A);

        employeeId = UUID.randomUUID();
        seedEmployee(TENANT_A, employeeId, "EMP-272", "Bob", "Builder");

        EmployeeResponse empResp = new EmployeeResponse(
                employeeId,
                TENANT_A,
                "EMP-272",
                "Bob",
                null,
                "Builder",
                "MALE",
                LocalDate.now(),
                null,
                null,
                "bob@example.com",
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
        PayrollTestApp.CURRENT_EMPLOYEE.set(empResp);

        // 1. Create FBP Earning: Fuel Allowance
        Earning fuel = new Earning(TENANT_A, "test");
        fuel.setCode("FUEL");
        fuel.setName("Fuel Allowance");
        fuel.setEarningType("ALLOWANCE");
        fuel.setCalculationType(CalculationType.FLAT);
        fuel.setIncludedInCtc(true);
        fuel.setFbpComponent(true);
        fuel.setActive(true);
        fuel = earningRepository.save(fuel);
        earningFbpId = fuel.getId();

        // 2. Create FBP Reimbursement: Meal Voucher
        Reimbursement meal = new Reimbursement(TENANT_A, "test");
        meal.setCode("MEAL");
        meal.setName("Meal Voucher");
        meal.setReimbursementType("FOOD");
        meal.setCalculationType(CalculationType.FLAT);
        meal.setIncludedInCtc(true);
        meal.setFbpComponent(true);
        meal.setActive(true);
        meal = reimbursementRepository.save(meal);
        reimbFbpId = meal.getId();

        // 3. Create Salary Structure Version for Employee: 48,000 Fuel + 24,000 Meal = 72,000 total FBP pool
        SalaryComponentItemRequest earningItem = new SalaryComponentItemRequest(
                earningFbpId, CalculationType.FLAT, new BigDecimal("4000.00"), null, true, "MONTHLY", null);
        SalaryComponentItemRequest reimbItem = new SalaryComponentItemRequest(
                reimbFbpId, CalculationType.FLAT, new BigDecimal("2000.00"), null, true, "MONTHLY", null);

        SalaryVersionRequest req = new SalaryVersionRequest(
                new BigDecimal("72000.00"),
                LocalDate.of(2026, 1, 1),
                "Initial Salary Structure with FBP",
                List.of(earningItem),
                List.of(),
                List.of(reimbItem));
        salaryService.create(employeeId, req);

        // 4. Configure FBP Plan for tenant: window open (April 1 to April 30, 2026 or covering today)
        LocalDate today = LocalDate.now();
        FbpPlanRequest planReq =
                new FbpPlanRequest(true, today.minusDays(5), today.plusDays(10), true, true, List.of(5, 1));
        fbpPlanService.upsert(planReq);
    }

    @AfterEach
    void tearDown() {
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "Acceptance: window open -> declareOwn succeeds, salary version-in-force shows declared amounts & unallocated")
    void fullDeclarationLifecycleAcceptance() {
        TenantContext.set(TENANT_A);

        // Declare 36,000 on Fuel (out of 48,000) and 12,000 on Meal (out of 24,000)
        FbpDeclarationRequest declReq = new FbpDeclarationRequest(List.of(
                new FbpDeclarationLineRequest("EARNING", earningFbpId, new BigDecimal("36000.0000")),
                new FbpDeclarationLineRequest("REIMBURSEMENT", reimbFbpId, new BigDecimal("12000.0000"))));

        FbpDeclarationResponse res = fbpDeclarationService.declareOwn(declReq);

        assertThat(res).isNotNull();
        assertThat(res.windowOpen()).isTrue();
        assertThat(res.declaredBy()).isEqualTo("EMPLOYEE");
        assertThat(res.summary().poolAnnual()).isEqualByComparingTo(new BigDecimal("72000.00"));
        assertThat(res.summary().declaredAnnual()).isEqualByComparingTo(new BigDecimal("48000.00"));
        assertThat(res.summary().unallocatedAnnual()).isEqualByComparingTo(new BigDecimal("24000.00"));

        // Verify GET salary as of today shows the declared overlay
        SalaryVersionResponse versionAsOf = salaryService.getAsOf(employeeId, LocalDate.now());
        assertThat(versionAsOf.fbp()).isNotNull();
        assertThat(versionAsOf.fbp().poolAnnual()).isEqualByComparingTo(new BigDecimal("72000.00"));
        assertThat(versionAsOf.fbp().declaredAnnual()).isEqualByComparingTo(new BigDecimal("48000.00"));
        assertThat(versionAsOf.fbp().unallocatedAnnual()).isEqualByComparingTo(new BigDecimal("24000.00"));

        assertThat(versionAsOf.earnings().get(0).isFbp()).isTrue();
        assertThat(versionAsOf.earnings().get(0).declaredAnnualAmount()).isEqualByComparingTo("36000.0000");
        assertThat(versionAsOf.earnings().get(0).declaredMonthlyAmount()).isEqualByComparingTo("3000.0000");

        assertThat(versionAsOf.reimbursements().get(0).isFbp()).isTrue();
        assertThat(versionAsOf.reimbursements().get(0).declaredAnnualAmount()).isEqualByComparingTo("12000.0000");
        assertThat(versionAsOf.reimbursements().get(0).declaredMonthlyAmount()).isEqualByComparingTo("1000.0000");
    }

    @Test
    @DisplayName("Window closed: declareOwn throws 409 WINDOW_CLOSED, officer PUT succeeds")
    void windowClosedBlocksEmployeeAllowsOfficer() {
        TenantContext.set(TENANT_A);

        // Lock the FBP plan window
        fbpPlanService.lock();

        // Employee declaration must fail with 409
        FbpDeclarationRequest empReq = new FbpDeclarationRequest(
                List.of(new FbpDeclarationLineRequest("EARNING", earningFbpId, new BigDecimal("20000.0000"))));

        assertThatThrownBy(() -> fbpDeclarationService.declareOwn(empReq))
                .isInstanceOf(SalaryConflictException.class)
                .hasMessageContaining("WINDOW_CLOSED");

        // Officer declaration succeeds even with window closed
        FbpDeclarationRequest officerReq = new FbpDeclarationRequest(List.of(
                new FbpDeclarationLineRequest("EARNING", earningFbpId, new BigDecimal("24000.0000")),
                new FbpDeclarationLineRequest("REIMBURSEMENT", reimbFbpId, new BigDecimal("10000.0000"))));

        FbpDeclarationResponse officerRes = fbpDeclarationService.set(employeeId, officerReq);
        assertThat(officerRes).isNotNull();
        assertThat(officerRes.declaredBy()).isEqualTo("OFFICER");
        assertThat(officerRes.summary().declaredAnnual()).isEqualByComparingTo(new BigDecimal("34000.00"));
    }

    @Test
    @DisplayName("Re-submitting declaration updates amounts in place without duplicate database rows")
    void reSubmitDeclarationUpdatesExistingRowsAndTotals() throws SQLException {
        TenantContext.set(TENANT_A);

        // 1. Initial declaration: Fuel 36k, Meal 12k (total 48k declared, 24k unallocated)
        FbpDeclarationRequest initialReq = new FbpDeclarationRequest(List.of(
                new FbpDeclarationLineRequest("EARNING", earningFbpId, new BigDecimal("36000.0000")),
                new FbpDeclarationLineRequest("REIMBURSEMENT", reimbFbpId, new BigDecimal("12000.0000"))));
        FbpDeclarationResponse initialRes = fbpDeclarationService.declareOwn(initialReq);

        assertThat(initialRes.summary().declaredAnnual()).isEqualByComparingTo(new BigDecimal("48000.00"));
        assertThat(initialRes.summary().unallocatedAnnual()).isEqualByComparingTo(new BigDecimal("24000.00"));

        // 2. Re-submit with new amounts: Fuel 24k, Meal 20k (total 44k declared, 28k unallocated)
        FbpDeclarationRequest revisedReq = new FbpDeclarationRequest(List.of(
                new FbpDeclarationLineRequest("EARNING", earningFbpId, new BigDecimal("24000.0000")),
                new FbpDeclarationLineRequest("REIMBURSEMENT", reimbFbpId, new BigDecimal("20000.0000"))));
        FbpDeclarationResponse revisedRes = fbpDeclarationService.declareOwn(revisedReq);

        assertThat(revisedRes.summary().declaredAnnual()).isEqualByComparingTo(new BigDecimal("44000.00"));
        assertThat(revisedRes.summary().unallocatedAnnual()).isEqualByComparingTo(new BigDecimal("28000.00"));

        FbpDeclarationLineResponse earningLine = revisedRes.lines().stream()
                .filter(l -> l.componentId().equals(earningFbpId))
                .findFirst()
                .orElseThrow();
        assertThat(earningLine.declaredAnnualAmount()).isEqualByComparingTo("24000.0000");

        FbpDeclarationLineResponse reimbLine = revisedRes.lines().stream()
                .filter(l -> l.componentId().equals(reimbFbpId))
                .findFirst()
                .orElseThrow();
        assertThat(reimbLine.declaredAnnualAmount()).isEqualByComparingTo("20000.0000");

        // 3. Verify exactly 2 rows exist in DB for this employee (no duplicate rows)
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM payroll.employee_fbp_component WHERE employee_id = ?")) {
            ps.setObject(1, employeeId);
            try (var rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
        }

        // 4. Verify salary in force reflects the re-submitted amounts
        SalaryVersionResponse version = salaryService.getAsOf(employeeId, LocalDate.now());
        assertThat(version.fbp().declaredAnnual()).isEqualByComparingTo(new BigDecimal("44000.00"));
        assertThat(version.fbp().unallocatedAnnual()).isEqualByComparingTo(new BigDecimal("28000.00"));
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
