package com.infinevo.payroll.salary;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
 * Acceptance integration test for effective-dated CTC structures and revisions (W-26.2).
 * Spec § 7: Create v1 on 2026-01-01, revise on 2026-04-01; as-of 2026-03-31 returns v1,
 * as-of 2026-04-01 returns v2; v1 row unchanged; history lists both.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class SalaryVersionIT extends AbstractIntegrationTest {

    @Autowired
    private EmployeeSalaryService salaryService;

    @Autowired
    private EarningRepository earningRepository;

    private UUID employeeId;
    private UUID basicEarningId;

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

        employeeId = UUID.randomUUID();
        seedEmployee(TENANT_A, employeeId, "EMP-001", "Alice", "Smith");

        TenantContext.set(TENANT_A);
        Earning basic = new Earning(TENANT_A, "system");
        basic.setCode("BASIC");
        basic.setName("Basic Salary");
        basic.setEarningType("BASIC");
        basic.setCalculationType(CalculationType.FLAT);
        basic.setIncludedInCtc(true);
        basic = earningRepository.save(basic);
        basicEarningId = basic.getId();
    }

    @AfterEach
    void tearDown() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName(
            "Salary version lifecycle: create v1 on 2026-01-01, revise on 2026-04-01, verify as-of queries and history")
    void salaryVersionLifecycle() {
        TenantContext.set(TENANT_A);

        // 1. Create v1 effective 2026-01-01 (600,000 CTC = 50,000/mo Basic)
        SalaryComponentItemRequest basicV1 = new SalaryComponentItemRequest(
                basicEarningId, CalculationType.FLAT, new BigDecimal("50000.00"), null, true, "MONTHLY", null);
        SalaryVersionRequest reqV1 = new SalaryVersionRequest(
                new BigDecimal("600000.00"),
                LocalDate.of(2026, 1, 1),
                "V1 Offer",
                List.of(basicV1),
                List.of(),
                List.of());

        SalaryVersionResponse v1 = salaryService.create(employeeId, reqV1);
        assertThat(v1.id()).isNotNull();
        assertThat(v1.annualCtc()).isEqualByComparingTo(new BigDecimal("600000.00"));
        assertThat(v1.monthlyCtc()).isEqualByComparingTo(new BigDecimal("50000.00"));

        // 2. Revise to v2 effective 2026-04-01 (720,000 CTC = 60,000/mo Basic)
        SalaryComponentItemRequest basicV2 = new SalaryComponentItemRequest(
                basicEarningId, CalculationType.FLAT, new BigDecimal("60000.00"), null, true, "MONTHLY", null);
        SalaryVersionRequest reqV2 = new SalaryVersionRequest(
                new BigDecimal("720000.00"),
                LocalDate.of(2026, 4, 1),
                "V2 Increment",
                List.of(basicV2),
                List.of(),
                List.of());

        SalaryVersionResponse v2 = salaryService.revise(employeeId, reqV2);
        assertThat(v2.id()).isNotNull();
        assertThat(v2.annualCtc()).isEqualByComparingTo(new BigDecimal("720000.00"));
        assertThat(v2.changeInPercent()).isEqualByComparingTo(new BigDecimal("20.00"));

        // 3. Query as-of 2026-03-31 -> returns v1 (600,000)
        SalaryVersionResponse asOfOld = salaryService.getAsOf(employeeId, LocalDate.of(2026, 3, 31));
        assertThat(asOfOld.id()).isEqualTo(v1.id());
        assertThat(asOfOld.annualCtc()).isEqualByComparingTo(new BigDecimal("600000.00"));
        assertThat(asOfOld.notes()).isEqualTo("V1 Offer");

        // 4. Query as-of 2026-04-01 -> returns v2 (720,000)
        SalaryVersionResponse asOfNew = salaryService.getAsOf(employeeId, LocalDate.of(2026, 4, 1));
        assertThat(asOfNew.id()).isEqualTo(v2.id());
        assertThat(asOfNew.annualCtc()).isEqualByComparingTo(new BigDecimal("720000.00"));
        assertThat(asOfNew.notes()).isEqualTo("V2 Increment");

        // 5. Query history -> lists both versions, newest first
        List<SalaryVersionResponse> history = salaryService.listVersions(employeeId);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).id()).isEqualTo(v2.id());
        assertThat(history.get(1).id()).isEqualTo(v1.id());
    }

    @Test
    @DisplayName("Editing a future salary version clears and refills collections properly")
    void editFutureVersionClearsAndRefillsCollections() {
        TenantContext.set(TENANT_A);

        LocalDate futureDate = LocalDate.now().plusMonths(2);

        // 1. Create a future version
        SalaryComponentItemRequest basicV1 = new SalaryComponentItemRequest(
                basicEarningId, CalculationType.FLAT, new BigDecimal("50000.00"), null, true, "MONTHLY", null);
        SalaryVersionRequest req = new SalaryVersionRequest(
                new BigDecimal("600000.00"), futureDate, "Future Planned", List.of(basicV1), List.of(), List.of());

        SalaryVersionResponse created = salaryService.create(employeeId, req);
        assertThat(created.id()).isNotNull();
        assertThat(created.notes()).isEqualTo("Future Planned");
        assertThat(created.earnings()).hasSize(1);
        assertThat(created.earnings().get(0).monthlyAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));

        // 2. Edit the future version (change CTC to 660,000, 55,000/mo Basic)
        SalaryComponentItemRequest basicV2 = new SalaryComponentItemRequest(
                basicEarningId, CalculationType.FLAT, new BigDecimal("55000.00"), null, true, "MONTHLY", null);
        SalaryVersionRequest updateReq = new SalaryVersionRequest(
                new BigDecimal("660000.00"), futureDate, "Future Revised", List.of(basicV2), List.of(), List.of());

        SalaryVersionResponse updated = salaryService.update(employeeId, created.id(), updateReq);
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.notes()).isEqualTo("Future Revised");
        assertThat(updated.annualCtc()).isEqualByComparingTo(new BigDecimal("660000.00"));
        assertThat(updated.monthlyCtc()).isEqualByComparingTo(new BigDecimal("55000.00"));
        assertThat(updated.earnings()).hasSize(1);
        assertThat(updated.earnings().get(0).monthlyAmount()).isEqualByComparingTo(new BigDecimal("55000.00"));

        // 3. Verify retrieval reflects the updated version
        SalaryVersionResponse fetched = salaryService.getAsOf(employeeId, futureDate);
        assertThat(fetched.notes()).isEqualTo("Future Revised");
        assertThat(fetched.annualCtc()).isEqualByComparingTo(new BigDecimal("660000.00"));
        assertThat(fetched.earnings()).hasSize(1);
        assertThat(fetched.earnings().get(0).monthlyAmount()).isEqualByComparingTo(new BigDecimal("55000.00"));
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
