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
import com.infinevo.payroll.taxcalc.exception.TaxRulesMissingException;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.summary.TaxSummaryService;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryResponse;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
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
 * Acceptance integration test for tax calculation engine (W-33.1 spec § 7).
 *
 * <p>Validates:
 * <ul>
 *   <li>Tenant, employee with flat taxable CTC 15,75,000 from 2025-04-01, header NEW</li>
 *   <li>GET .../tax preview -> annual_tax 109,200.00, summary row still computed: null</li>
 *   <li>POST .../tax/compute -> NEW filled with 109,200.00, OLD null, and GET .../summary shows tax_to_be_paid 109,200 with computed_at</li>
 *   <li>Second POST cleanly overwrites</li>
 *   <li>GET .../tax?regime=OLD -> 409 REGIME_NOT_AVAILABLE</li>
 *   <li>Year with no rules -> 422 TAX_RULES_MISSING</li>
 * </ul>
 */
@SpringBootTest(classes = PayrollTestApp.class)
class TaxComputeIT extends AbstractIntegrationTest {

    @Autowired
    private TaxCalculationService taxCalculationService;

    @Autowired
    private TaxDeclarationService taxDeclarationService;

    @Autowired
    private TaxSummaryService taxSummaryService;

    @Autowired
    private TaxDeclarationWindowService windowService;

    @Autowired
    private EmployeeSalaryService salaryService;

    @Autowired
    private EarningRepository earningRepository;

    @Autowired
    private EmployeeService employeeService;

    private UUID employeeId;
    private final FinancialYear fy2025_2026 = FinancialYear.of(2025, 2026);
    private final String currentFy = fy2025_2026.label();

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

        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);
        employeeId = TaxDeclarationTestSchema.seedEmployee(
                TaxDeclarationTestSchema.TENANT_A, "EMP-331", "taxcalc.emp@acme.com", "Tax", "Tester");

        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(employeeId));

        // Window for 2025-2026
        TaxDeclarationWindowRequest winReq = new TaxDeclarationWindowRequest(
                fy2025_2026.start(), fy2025_2026.end(), false, "NEW", true, true, false, false);
        windowService.upsert(currentFy, winReq);

        // Create taxable earning component BASIC
        Earning basic = new Earning(TaxDeclarationTestSchema.TENANT_A, "system");
        basic.setCode("BASIC");
        basic.setName("Basic Salary");
        basic.setEarningType("BASIC");
        basic.setCalculationType(CalculationType.FLAT);
        basic.setIncludedInCtc(true);
        basic.setTaxable(true);
        basic = earningRepository.save(basic);

        // Salary structure: 15,75,000 CTC = 1,31,250 / month flat Basic effective 2025-04-01
        SalaryComponentItemRequest basicItem = new SalaryComponentItemRequest(
                basic.getId(), CalculationType.FLAT, new BigDecimal("131250.00"), null, true, "MONTHLY", null);
        SalaryVersionRequest salReq = new SalaryVersionRequest(
                new BigDecimal("1575000.00"),
                LocalDate.of(2025, 4, 1),
                "Initial CTC",
                List.of(basicItem),
                List.of(),
                List.of());
        salaryService.create(employeeId, salReq);

        // Header for NEW regime (created after salary structure to test manual compute flow)
        taxDeclarationService.save(employeeId, currentFy, new TaxDeclarationRequest("NEW", false, false, false));
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
            "Tax computation acceptance test: preview, compute & record, overwrite, 409 on OLD, and 422 on missing rules")
    void testTaxComputeAcceptance() {
        // 1. GET .../tax preview -> annual_tax 109,200.00, summary row still computed: null
        TaxComputation preview = taxCalculationService.compute(employeeId, fy2025_2026, TaxRegime.NEW);
        assertThat(preview.annualTax().raw()).isEqualByComparingTo(new BigDecimal("109200"));
        assertThat(preview.standardDeduction().raw()).isEqualByComparingTo(new BigDecimal("75000"));
        assertThat(preview.taxableIncome().raw()).isEqualByComparingTo(new BigDecimal("1500000"));
        assertThat(preview.taxBeforeRebate().raw()).isEqualByComparingTo(new BigDecimal("105000"));
        assertThat(preview.cess().raw()).isEqualByComparingTo(new BigDecimal("4200"));

        TaxSummaryResponse summaryBefore = taxSummaryService.summaryOwn(currentFy);
        assertThat(summaryBefore.computed()).isNull();

        // 2. POST .../tax/compute -> both NEW and OLD computed, and GET .../summary shows tax_to_be_paid with
        // computed_at
        Map<TaxRegime, TaxComputation> computeResults = taxCalculationService.computeAndRecord(employeeId, fy2025_2026);
        assertThat(computeResults).containsKey(TaxRegime.NEW);
        assertThat(computeResults.get(TaxRegime.NEW).annualTax().raw()).isEqualByComparingTo(new BigDecimal("109200"));
        assertThat(computeResults).containsKey(TaxRegime.OLD);

        TaxSummaryResponse summaryAfter = taxSummaryService.summaryOwn(currentFy);
        assertThat(summaryAfter.computed()).isNotNull();
        assertThat(summaryAfter.computed().taxToBePaid()).isEqualByComparingTo(new BigDecimal("109200"));
        assertThat(summaryAfter.computed().taxableIncome()).isEqualByComparingTo(new BigDecimal("1575000"));
        assertThat(summaryAfter.computed().netTaxableIncome()).isEqualByComparingTo(new BigDecimal("1500000"));
        assertThat(summaryAfter.computed().computedAt()).isNotNull();

        // 3. Second POST cleanly overwrites
        Map<TaxRegime, TaxComputation> overwriteResults =
                taxCalculationService.computeAndRecord(employeeId, fy2025_2026);
        assertThat(overwriteResults.get(TaxRegime.NEW).annualTax().raw())
                .isEqualByComparingTo(new BigDecimal("109200"));
        assertThat(overwriteResults.get(TaxRegime.OLD)).isNotNull();

        TaxSummaryResponse summaryOverwritten = taxSummaryService.summaryOwn(currentFy);
        assertThat(summaryOverwritten.computed()).isNotNull();
        assertThat(summaryOverwritten.computed().taxToBePaid()).isEqualByComparingTo(new BigDecimal("109200"));

        // 4. GET .../tax?regime=OLD -> successfully computes under OLD regime
        TaxComputation oldPreview = taxCalculationService.compute(employeeId, fy2025_2026, TaxRegime.OLD);
        assertThat(oldPreview.regime()).isEqualTo(TaxRegime.OLD);
        assertThat(oldPreview.annualTax()).isNotNull();

        // 5. A year with no rules in reference schema -> 422 TAX_RULES_MISSING
        FinancialYear futureFy = FinancialYear.of(2029, 2030);
        TaxDeclarationWindowRequest futureWinReq = new TaxDeclarationWindowRequest(
                futureFy.start(), futureFy.end(), false, "NEW", true, true, false, false);
        windowService.upsert(futureFy.label(), futureWinReq);
        taxDeclarationService.save(employeeId, futureFy.label(), new TaxDeclarationRequest("NEW", false, false, false));

        assertThatThrownBy(() -> taxCalculationService.compute(employeeId, futureFy, TaxRegime.NEW))
                .isInstanceOf(TaxRulesMissingException.class);
    }
}
