package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryComponentItemRequest;
import com.infinevo.payroll.salary.SalaryVersionRequest;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotFoundException;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
 * Row-Level Security (RLS) integration test for tax calculations (W-33.1 spec § 7).
 *
 * <p>Validates:
 * <ul>
 *   <li>Tenant A's officer attempting to compute Tenant B's employee gets 404 (declaration not found due to tenant isolation)</li>
 *   <li>Tax computation record lands on Tenant A's row only; Tenant B sees no records</li>
 * </ul>
 */
@SpringBootTest(classes = PayrollTestApp.class)
class TaxComputeRlsIT extends AbstractIntegrationTest {

    @Autowired
    private TaxCalculationService taxCalculationService;

    @Autowired
    private TaxDeclarationService taxDeclarationService;

    @Autowired
    private TaxDeclarationWindowService windowService;

    @Autowired
    private EmployeeSalaryService salaryService;

    @Autowired
    private EarningRepository earningRepository;

    @Autowired
    private EmployeeService employeeService;

    private UUID employeeA;
    private UUID employeeB;
    private final FinancialYear fy = FinancialYear.of(2025, 2026);
    private final String currentFy = fy.label();

    @BeforeAll
    static void applySchema() throws Exception {
        TaxDeclarationTestSchema.apply();
        PayrollTestSchema.apply();
    }

    @AfterAll
    static void tearDown() throws SQLException {
        TaxDeclarationTestSchema.clearAll();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        TaxDeclarationTestSchema.seedTenants();
        TaxDeclarationTestSchema.clearDeclarations();
        PayrollTestSchema.cleanTables();

        // 1. Seed Tenant A employee & declaration
        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);
        employeeA = TaxDeclarationTestSchema.seedEmployee(
                TaxDeclarationTestSchema.TENANT_A, "EMP-RLS-A", "rls.a@acme.com", "Alice", "TenantA");
        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(employeeA));

        TaxDeclarationWindowRequest winReqA =
                new TaxDeclarationWindowRequest(fy.start(), fy.end(), false, "NEW", true, true, false, false);
        windowService.upsert(currentFy, winReqA);
        Earning basicA = new Earning(TaxDeclarationTestSchema.TENANT_A, "system");
        basicA.setCode("BASIC_A");
        basicA.setName("Basic Salary A");
        basicA.setEarningType("BASIC");
        basicA.setCalculationType(CalculationType.FLAT);
        basicA.setIncludedInCtc(true);
        basicA.setTaxable(true);
        basicA = earningRepository.save(basicA);

        SalaryComponentItemRequest itemA = new SalaryComponentItemRequest(
                basicA.getId(), CalculationType.FLAT, new BigDecimal("131250.00"), null, true, "MONTHLY", null);
        salaryService.create(
                employeeA,
                new SalaryVersionRequest(
                        new BigDecimal("1575000.00"),
                        LocalDate.of(2025, 4, 1),
                        "CTC A",
                        List.of(itemA),
                        List.of(),
                        List.of()));

        taxDeclarationService.save(employeeA, currentFy, new TaxDeclarationRequest("NEW", false, false, false));

        // 2. Seed Tenant B employee & declaration
        TenantContext.set(TaxDeclarationTestSchema.TENANT_B);
        employeeB = TaxDeclarationTestSchema.seedEmployee(
                TaxDeclarationTestSchema.TENANT_B, "EMP-RLS-B", "rls.b@globex.com", "Bob", "TenantB");
        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(employeeB));

        TaxDeclarationWindowRequest winReqB =
                new TaxDeclarationWindowRequest(fy.start(), fy.end(), false, "NEW", true, true, false, false);
        windowService.upsert(currentFy, winReqB);

        Earning basicB = new Earning(TaxDeclarationTestSchema.TENANT_B, "system");
        basicB.setCode("BASIC_B");
        basicB.setName("Basic Salary B");
        basicB.setEarningType("BASIC");
        basicB.setCalculationType(CalculationType.FLAT);
        basicB.setIncludedInCtc(true);
        basicB.setTaxable(true);
        basicB = earningRepository.save(basicB);

        SalaryComponentItemRequest itemB = new SalaryComponentItemRequest(
                basicB.getId(), CalculationType.FLAT, new BigDecimal("131250.00"), null, true, "MONTHLY", null);
        salaryService.create(
                employeeB,
                new SalaryVersionRequest(
                        new BigDecimal("1575000.00"),
                        LocalDate.of(2025, 4, 1),
                        "CTC B",
                        List.of(itemB),
                        List.of(),
                        List.of()));

        taxDeclarationService.save(employeeB, currentFy, new TaxDeclarationRequest("NEW", false, false, false));

        TenantContext.clear();
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
    }

    @AfterEach
    void cleanUp() throws SQLException {
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        TaxDeclarationTestSchema.clearDeclarations();
        PayrollTestSchema.cleanTables();
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "Cross-tenant tax calculation: Tenant A cannot compute Tenant B employee (404), records land only on Tenant A")
    void testCrossTenantIsolation() throws SQLException {
        // Active context is Tenant A
        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);
        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(employeeA));

        // Tenant A officer attempting to compute Tenant B employee throws 404
        assertThatThrownBy(() -> taxCalculationService.compute(employeeB, fy, TaxRegime.NEW))
                .isInstanceOfAny(DeclarationNotFoundException.class, EmployeeService.NotFoundException.class);

        assertThatThrownBy(() -> taxCalculationService.computeAndRecord(employeeB, fy))
                .isInstanceOf(DeclarationNotFoundException.class);

        // Tenant A computes its own employee
        Map<TaxRegime, TaxComputation> result = taxCalculationService.computeAndRecord(employeeA, fy);
        assertThat(result.get(TaxRegime.NEW).annualTax().raw()).isEqualByComparingTo(new BigDecimal("109200"));

        // Direct DB verification via non-owner app connection with RLS:
        // Tenant A sees the summary row
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TaxDeclarationTestSchema.TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM payroll.employee_inv_tax_summary WHERE tax_to_be_paid = 109200")) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }

        // Tenant B sees 0 summary rows
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TaxDeclarationTestSchema.TENANT_B);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM payroll.employee_inv_tax_summary WHERE tax_to_be_paid = 109200")) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(0);
                }
            }
        }
    }
}
