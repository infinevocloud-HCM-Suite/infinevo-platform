package com.infinevo.payroll.fbp;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

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
 * Integration test for FBP declaration carry-forward on salary structure revision (W-27.2).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class FbpCarryForwardIT extends AbstractIntegrationTest {

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
        seedEmployee(TENANT_A, employeeId, "EMP-CF", "Charlie", "Fox");

        EmployeeResponse empResp = new EmployeeResponse(
                employeeId,
                TENANT_A,
                "EMP-CF",
                "Charlie",
                null,
                "Fox",
                "MALE",
                LocalDate.now(),
                null,
                null,
                "cf@example.com",
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
        PayrollTestApp.CURRENT_EMPLOYEE.set(empResp);

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

        // Configure plan
        LocalDate today = LocalDate.now();
        fbpPlanService.upsert(
                new FbpPlanRequest(true, today.minusDays(5), today.plusDays(10), true, true, List.of(5, 1)));
    }

    @AfterEach
    void tearDown() {
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Revise salary structure carries forward declaration capped at new lines, leaves old version intact")
    void revisionCarriesForwardDeclaration() {
        TenantContext.set(TENANT_A);

        // 1. Create v1 on 2026-01-01: Fuel 50,000 ceiling, Meal 20,000 ceiling
        SalaryComponentItemRequest v1Fuel = new SalaryComponentItemRequest(
                earningFbpId, CalculationType.FLAT, new BigDecimal("4166.6667"), null, true, "MONTHLY", null);
        SalaryComponentItemRequest v1Meal = new SalaryComponentItemRequest(
                reimbFbpId, CalculationType.FLAT, new BigDecimal("1666.6667"), null, true, "MONTHLY", null);

        SalaryVersionRequest reqV1 = new SalaryVersionRequest(
                new BigDecimal("70000.00"),
                LocalDate.of(2026, 1, 1),
                "Version 1",
                List.of(v1Fuel),
                List.of(),
                List.of(v1Meal));
        SalaryVersionResponse v1 = salaryService.create(employeeId, reqV1);

        // 2. Employee declares 40,000 on Fuel, 15,000 on Meal
        FbpDeclarationRequest declReq = new FbpDeclarationRequest(List.of(
                new FbpDeclarationLineRequest("EARNING", earningFbpId, new BigDecimal("40000.0000")),
                new FbpDeclarationLineRequest("REIMBURSEMENT", reimbFbpId, new BigDecimal("15000.0000"))));
        fbpDeclarationService.declareOwn(declReq);

        // 3. Officer revises structure on 2026-07-01: Fuel reduced to 30,000 ceiling, Meal increased to 30,000 ceiling
        SalaryComponentItemRequest v2Fuel = new SalaryComponentItemRequest(
                earningFbpId, CalculationType.FLAT, new BigDecimal("2500.0000"), null, true, "MONTHLY", null);
        SalaryComponentItemRequest v2Meal = new SalaryComponentItemRequest(
                reimbFbpId, CalculationType.FLAT, new BigDecimal("2500.0000"), null, true, "MONTHLY", null);

        SalaryVersionRequest reqV2 = new SalaryVersionRequest(
                new BigDecimal("60000.00"),
                LocalDate.of(2026, 7, 1),
                "Version 2 Revision",
                List.of(v2Fuel),
                List.of(),
                List.of(v2Meal));
        SalaryVersionResponse v2 = salaryService.revise(employeeId, reqV2);

        // 4. Verify v2 declaration:
        // Fuel was 40,000 but v2 line ceiling is 30,000 -> capped at 30,000!
        // Meal was 15,000 and v2 line ceiling is 30,000 -> retained at 15,000!
        SalaryVersionResponse fetchedV2 = salaryService.getAsOf(employeeId, LocalDate.of(2026, 7, 1));
        assertThat(fetchedV2.earnings().get(0).declaredAnnualAmount()).isEqualByComparingTo("30000.0000");
        assertThat(fetchedV2.reimbursements().get(0).declaredAnnualAmount()).isEqualByComparingTo("15000.0000");
        assertThat(fetchedV2.fbp().declaredAnnual()).isEqualByComparingTo(new BigDecimal("45000.00"));
        assertThat(fetchedV2.fbp().unallocatedAnnual()).isEqualByComparingTo(new BigDecimal("15000.00"));

        // 5. Verify v1 declaration is UNCHANGED: 40,000 and 15,000
        SalaryVersionResponse fetchedV1 = salaryService.getAsOf(employeeId, LocalDate.of(2026, 3, 1));
        assertThat(fetchedV1.earnings().get(0).declaredAnnualAmount()).isEqualByComparingTo("40000.0000");
        assertThat(fetchedV1.reimbursements().get(0).declaredAnnualAmount()).isEqualByComparingTo("15000.0000");
    }

    @Test
    @DisplayName("Editing future salary version re-caps carried FBP lines and removes deleted components")
    void updateFutureSalaryVersionRecapsCarriedFbpDeclarations() throws SQLException {
        TenantContext.set(TENANT_A);

        // 1. Setup V1 with Fuel (ceiling 48k) and Meal (ceiling 24k)
        SalaryComponentItemRequest v1Fuel = new SalaryComponentItemRequest(
                earningFbpId, CalculationType.FLAT, new BigDecimal("4000.0000"), null, true, "MONTHLY", null);
        SalaryComponentItemRequest v1Meal = new SalaryComponentItemRequest(
                reimbFbpId, CalculationType.FLAT, new BigDecimal("2000.0000"), null, true, "MONTHLY", null);

        SalaryVersionRequest reqV1 = new SalaryVersionRequest(
                new BigDecimal("72000.00"),
                LocalDate.of(2026, 1, 1),
                "Version 1",
                List.of(v1Fuel),
                List.of(),
                List.of(v1Meal));
        salaryService.create(employeeId, reqV1);

        // Declare Fuel 40k and Meal 15k
        FbpDeclarationRequest declReq = new FbpDeclarationRequest(List.of(
                new FbpDeclarationLineRequest("EARNING", earningFbpId, new BigDecimal("40000.0000")),
                new FbpDeclarationLineRequest("REIMBURSEMENT", reimbFbpId, new BigDecimal("15000.0000"))));
        fbpDeclarationService.set(employeeId, declReq);

        // 2. Revise to future version V2 (effective 3 months in the future) with Fuel ceiling 30k and Meal ceiling 30k
        LocalDate futureDate = LocalDate.now().plusMonths(3);
        SalaryComponentItemRequest v2Fuel = new SalaryComponentItemRequest(
                earningFbpId, CalculationType.FLAT, new BigDecimal("2500.0000"), null, true, "MONTHLY", null);
        SalaryComponentItemRequest v2Meal = new SalaryComponentItemRequest(
                reimbFbpId, CalculationType.FLAT, new BigDecimal("2500.0000"), null, true, "MONTHLY", null);

        SalaryVersionRequest reqV2 = new SalaryVersionRequest(
                new BigDecimal("60000.00"),
                futureDate,
                "Version 2 Revision",
                List.of(v2Fuel),
                List.of(),
                List.of(v2Meal));
        SalaryVersionResponse v2 = salaryService.revise(employeeId, reqV2);

        // Prior to update, V2 has carried Fuel 30k and Meal 15k
        assertThat(v2.earnings().get(0).declaredAnnualAmount()).isEqualByComparingTo("30000.0000");

        // 3. Edit future version V2: cut Fuel line from 30,000 to 24,000 and REMOVE Meal component
        SalaryComponentItemRequest updatedFuel = new SalaryComponentItemRequest(
                earningFbpId, CalculationType.FLAT, new BigDecimal("2000.0000"), null, true, "MONTHLY", null);
        SalaryVersionRequest reqUpdate = new SalaryVersionRequest(
                new BigDecimal("24000.00"),
                futureDate,
                "Version 2 Updated",
                List.of(updatedFuel),
                List.of(),
                List.of());

        SalaryVersionResponse updatedV2 = salaryService.update(employeeId, v2.id(), reqUpdate);

        // 4. Verify V2 declaration:
        // Fuel was 30,000, new line ceiling is 24,000 -> re-capped to 24,000!
        // Meal was removed -> declaration row removed!
        // Unallocated pool is exactly 0.00 (not negative!)
        assertThat(updatedV2.earnings().get(0).declaredAnnualAmount()).isEqualByComparingTo("24000.0000");
        assertThat(updatedV2.reimbursements()).isEmpty();
        assertThat(updatedV2.fbp().poolAnnual()).isEqualByComparingTo(new BigDecimal("24000.00"));
        assertThat(updatedV2.fbp().declaredAnnual()).isEqualByComparingTo(new BigDecimal("24000.00"));
        assertThat(updatedV2.fbp().unallocatedAnnual()).isEqualByComparingTo(BigDecimal.ZERO);

        // 5. Verify direct query via getAsOf
        SalaryVersionResponse fetchedUpdated = salaryService.getAsOf(employeeId, futureDate);
        assertThat(fetchedUpdated.fbp().unallocatedAnnual()).isEqualByComparingTo(BigDecimal.ZERO);

        // 6. Verify in DB: only 1 row remains for V2 (Meal row removed)
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM payroll.employee_fbp_component WHERE ctc_structure_id = ?")) {
            ps.setObject(1, v2.id());
            try (var rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
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
