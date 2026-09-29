package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeePersonalRequest;
import com.infinevo.core.employee.detail.EmployeePersonalService;
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
import com.infinevo.payroll.taxdeclaration.deductions.DeductionDeclarationService;
import com.infinevo.payroll.taxdeclaration.deductions.PreTaxDeductionKind;
import com.infinevo.payroll.taxdeclaration.deductions.Section6AItemReader;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PreTaxDeductionRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.Section6ALineRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.housing.HousingDeclarationService;
import com.infinevo.payroll.taxdeclaration.housing.dto.HouseRentRequest;
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
 * Acceptance integration test for Old Regime tax calculation (W-33.2 spec § 7).
 *
 * <p>Validates:
 * <ul>
 *   <li>Employee with CTC 12L (Basic 6L, HRA 2.4L, Special 3.6L), header OLD with rent, 80C, 80D, PT</li>
 *   <li>Preview compute under OLD -> annual tax 73,861.00, HRA exemption 1,80,000.00</li>
 *   <li>POST .../tax/compute fills both NEW and OLD, and summary row records Old regime figures</li>
 *   <li>Employee personal DOB moved to 1960 -> senior citizen slab applies -> 71,261.00</li>
 * </ul>
 */
@SpringBootTest(classes = PayrollTestApp.class)
class OldRegimeComputeIT extends AbstractIntegrationTest {

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

    @Autowired
    private EmployeePersonalService employeePersonalService;

    @Autowired
    private HousingDeclarationService housingDeclarationService;

    @Autowired
    private DeductionDeclarationService deductionDeclarationService;

    @Autowired
    private Section6AItemReader section6AItemReader;

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
                TaxDeclarationTestSchema.TENANT_A, "EMP-332", "oldcalc.emp@acme.com", "OldTax", "Tester");

        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(employeeId));

        // Window for 2025-2026
        TaxDeclarationWindowRequest winReq = new TaxDeclarationWindowRequest(
                fy2025_2026.start(), fy2025_2026.end(), false, "OLD", true, true, false, false);
        windowService.upsert(currentFy, winReq);

        // Header for OLD regime with rented house
        taxDeclarationService.saveOwn(currentFy, new TaxDeclarationRequest("OLD", true, false, false));

        // Create earning components: BASIC, HRA, SPECIAL
        Earning basic = new Earning(TaxDeclarationTestSchema.TENANT_A, "system");
        basic.setCode("BASIC");
        basic.setName("Basic Salary");
        basic.setEarningType("BASIC");
        basic.setCalculationType(CalculationType.FLAT);
        basic.setIncludedInCtc(true);
        basic.setTaxable(true);
        basic = earningRepository.save(basic);

        Earning hra = new Earning(TaxDeclarationTestSchema.TENANT_A, "system");
        hra.setCode("HRA");
        hra.setName("House Rent Allowance");
        hra.setEarningType("HRA");
        hra.setCalculationType(CalculationType.FLAT);
        hra.setIncludedInCtc(true);
        hra.setTaxable(true);
        hra = earningRepository.save(hra);

        Earning special = new Earning(TaxDeclarationTestSchema.TENANT_A, "system");
        special.setCode("SPECIAL");
        special.setName("Special Allowance");
        special.setEarningType("ALLOWANCE");
        special.setCalculationType(CalculationType.FLAT);
        special.setIncludedInCtc(true);
        special.setTaxable(true);
        special = earningRepository.save(special);

        // CTC 12,00,000: Basic 50,000/mo, HRA 20,000/mo, Special 30,000/mo
        SalaryComponentItemRequest basicItem = new SalaryComponentItemRequest(
                basic.getId(), CalculationType.FLAT, new BigDecimal("50000.00"), null, true, "MONTHLY", null);
        SalaryComponentItemRequest hraItem = new SalaryComponentItemRequest(
                hra.getId(), CalculationType.FLAT, new BigDecimal("20000.00"), null, true, "MONTHLY", null);
        SalaryComponentItemRequest specialItem = new SalaryComponentItemRequest(
                special.getId(), CalculationType.FLAT, new BigDecimal("30000.00"), null, true, "MONTHLY", null);

        SalaryVersionRequest salReq = new SalaryVersionRequest(
                new BigDecimal("1200000.00"),
                LocalDate.of(2025, 4, 1),
                "Standard 12L Structure",
                List.of(basicItem, hraItem, specialItem),
                List.of(),
                List.of());
        salaryService.create(employeeId, salReq);

        // Rent: 20,000 / month metro in Mumbai
        HouseRentRequest rentReq = new HouseRentRequest(
                "2025-04",
                "2026-03",
                "Marine Drive, Mumbai",
                "Landlord Name",
                "ABCDE1234F",
                true,
                new BigDecimal("20000.00"));
        housingDeclarationService.replaceHouseRentOwn(currentFy, List.of(rentReq));

        // Find 80C and 80D items from reference catalogue
        var activeItems = section6AItemReader.activeItems("OLD");
        UUID item80cId = activeItems.stream()
                .filter(i -> "80C".equals(i.sectionCode()))
                .findFirst()
                .orElseThrow()
                .id();
        UUID item80dId = activeItems.stream()
                .filter(i -> "80D".equals(i.sectionCode()))
                .findFirst()
                .orElseThrow()
                .id();

        Section6ALineRequest sec80cReq =
                new Section6ALineRequest(item80cId, "PPF Investment", new BigDecimal("150000.00"));
        Section6ALineRequest sec80dReq = new Section6ALineRequest(item80dId, "Mediclaim", new BigDecimal("25000.00"));
        deductionDeclarationService.replaceSection6AOwn(currentFy, List.of(sec80cReq, sec80dReq));

        // Pre-tax deduction: PT 2,400
        PreTaxDeductionRequest ptReq =
                new PreTaxDeductionRequest(PreTaxDeductionKind.PROFESSIONAL_TAX, new BigDecimal("2400.00"));
        deductionDeclarationService.replacePreTaxDeductionsOwn(currentFy, List.of(ptReq));
    }

    @AfterEach
    void cleanUp() throws SQLException {
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        TaxDeclarationTestSchema.clearDeclarations();
        PayrollTestSchema.cleanTables();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Old Regime acceptance test: preview 73,861, compute & record, and senior citizen 71,261")
    void testOldRegimeAcceptance() {
        // 1. GET .../tax?regime=OLD preview
        TaxComputation preview = taxCalculationService.compute(employeeId, fy2025_2026, TaxRegime.OLD);
        assertThat(preview.annualTax().raw()).isEqualByComparingTo(new BigDecimal("73861"));
        assertThat(preview.hraExemption().raw()).isEqualByComparingTo(new BigDecimal("180000"));
        assertThat(preview.standardDeduction().raw()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(preview.professionalTax().raw()).isEqualByComparingTo(new BigDecimal("2400"));
        assertThat(preview.grossTotalIncome().raw()).isEqualByComparingTo(new BigDecimal("967600"));
        assertThat(preview.taxableIncome().raw()).isEqualByComparingTo(new BigDecimal("792600"));
        assertThat(preview.taxBeforeRebate().raw()).isEqualByComparingTo(new BigDecimal("71020"));
        assertThat(preview.cess().raw()).isEqualByComparingTo(new BigDecimal("2840.8000"));

        // 2. POST .../tax/compute -> computes both regimes, records summary
        Map<TaxRegime, TaxComputation> computeResults = taxCalculationService.computeAndRecord(employeeId, fy2025_2026);
        assertThat(computeResults).containsKey(TaxRegime.OLD);
        assertThat(computeResults).containsKey(TaxRegime.NEW);
        assertThat(computeResults.get(TaxRegime.OLD).annualTax().raw()).isEqualByComparingTo(new BigDecimal("73861"));

        // OLD and NEW taxes must differ
        assertThat(computeResults.get(TaxRegime.OLD).annualTax())
                .isNotEqualTo(computeResults.get(TaxRegime.NEW).annualTax());

        TaxSummaryResponse summary = taxSummaryService.summaryOwn(currentFy);
        assertThat(summary.computed()).isNotNull();
        assertThat(summary.computed().taxToBePaid()).isEqualByComparingTo(new BigDecimal("73861"));
        assertThat(summary.computed().exemptionUnderSection10()).isEqualByComparingTo(new BigDecimal("180000"));
        assertThat(summary.computed().exemptionUnderSection6a()).isEqualByComparingTo(new BigDecimal("175000"));

        // 3. Move date of birth to 1960 (Senior citizen: 65 years old on 2026-03-31)
        employeePersonalService.put(
                employeeId,
                new EmployeePersonalRequest(LocalDate.of(1960, 5, 15), null, null, null, null, null, false));

        TaxComputation seniorComputation = taxCalculationService.compute(employeeId, fy2025_2026, TaxRegime.OLD);
        assertThat(seniorComputation.taxableIncome().raw()).isEqualByComparingTo(new BigDecimal("792600"));
        assertThat(seniorComputation.taxBeforeRebate().raw()).isEqualByComparingTo(new BigDecimal("68520"));
        assertThat(seniorComputation.cess().raw()).isEqualByComparingTo(new BigDecimal("2740.8000"));
        assertThat(seniorComputation.annualTax().raw()).isEqualByComparingTo(new BigDecimal("71261"));
    }
}
