package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeePersonalService;
import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryComponentItemResponse;
import com.infinevo.payroll.salary.SalaryNotFoundException;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.payroll.statutory.lines.ContributionShare;
import com.infinevo.payroll.statutory.lines.CtcEpfComponentRepository;
import com.infinevo.payroll.statutory.lines.SalaryStatutoryItemResponse;
import com.infinevo.payroll.statutory.pt.ProfessionalTaxService;
import com.infinevo.payroll.taxcalc.engine.HraExemption.HraMonthSalary;
import com.infinevo.payroll.taxcalc.model.TaxInput;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPreTaxDeductionRepository;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPrevEmploymentRepository;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvSection6ARepository;
import com.infinevo.payroll.taxdeclaration.deductions.Section6AItemReader;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoanRepository;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRentRepository;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutPropertyRepository;
import com.infinevo.payroll.taxdeclaration.summary.EmployeeInvOtherIncomeRepository;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for TaxInputAssembler covering B-1 and B-2 blocker fixes (W-33.1, W-33.2).
 *
 * <p>B-1: Mid-month joiner (no salary version on the 1st of the joining month) must produce a
 * 200-OK result with that month counted as zero taxable salary, NOT a 500 crash from an
 * unhandled SalaryNotFoundException.
 *
 * <p>B-2: Non-taxable HRA earnings must NOT contribute to the HRA exemption calculation.
 * SalaryProjection.annual() only sums taxable earnings; the HRA exemption must work on the
 * same taxable slice, otherwise a non-taxable HRA is deducted from a salary figure that
 * never included it, producing a lower-than-correct tax liability.
 * This class uses LENIENT strictness because stubCommon() stubs all repositories upfront;
 * tests that force all-throw or zero-version paths may legitimately not exercise every stub.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class TaxInputAssemblerTest {

    @Mock
    TaxDeclarationService declarationService;

    @Mock
    EmployeeService employeeService;

    @Mock
    EmployeePersonalService personalService;

    @Mock
    EmployeeSalaryService salaryService;

    @Mock
    EarningRepository earningRepository;

    @Mock
    EmployeeInvPrevEmploymentRepository prevEmploymentRepository;

    @Mock
    EmployeeInvHouseRentRepository houseRentRepository;

    @Mock
    EmployeeInvHomeLoanRepository homeLoanRepository;

    @Mock
    EmployeeInvLetOutPropertyRepository letOutPropertyRepository;

    @Mock
    EmployeeInvSection6ARepository section6ARepository;

    @Mock
    EmployeeInvPreTaxDeductionRepository preTaxDeductionRepository;

    @Mock
    EmployeeInvOtherIncomeRepository otherIncomeRepository;

    @Mock
    Section6AItemReader section6AItemReader;

    @Mock
    CtcEpfComponentRepository ctcEpfComponentRepository;

    @Mock
    ProfessionalTaxService professionalTaxService;

    @Mock
    WorkLocationService workLocationService;

    private TaxInputAssembler assembler;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID DECL_ID = UUID.randomUUID();
    private static final UUID BASIC_COMP = UUID.randomUUID();
    private static final UUID HRA_COMP = UUID.randomUUID();
    private static final UUID WORK_LOCATION_ID = UUID.randomUUID();
    // FY 2025-2026: April 2025 to March 2026
    private static final FinancialYear FY = FinancialYear.of(2025, 2026);

    @BeforeEach
    void setUp() {
        // Use the package-private constructor that takes resolved services directly
        // (null for optional ProfessionalTaxService and WorkLocationService so they don't interfere)
        assembler = new TaxInputAssembler(
                declarationService,
                employeeService,
                personalService,
                salaryService,
                earningRepository,
                prevEmploymentRepository,
                houseRentRepository,
                homeLoanRepository,
                letOutPropertyRepository,
                section6ARepository,
                preTaxDeductionRepository,
                otherIncomeRepository,
                section6AItemReader,
                null, // ProfessionalTaxService - optional
                null, // WorkLocationService - optional
                ctcEpfComponentRepository);
    }

    // ── B-1 tests ─────────────────────────────────────────────────────────────

    /**
     * B-1 core: employee joins 2025-10-15. SalaryProjection asks for the version on Oct-01.
     * No version exists before Oct-15, so versionInForce throws SalaryNotFoundException.
     * Before fix: 500. After fix: exception swallowed, Oct counted as zero, 200 returned.
     */
    @Test
    @DisplayName("B-1: mid-month joiner Oct-15 -> version resolved as of joining date, October counted")
    void b1_midMonthJoiner_noExceptionThrown() {
        stubCommon(LocalDate.of(2025, 10, 15));
        stubTaxableBasic();

        SalaryVersionResponse v = version(LocalDate.of(2025, 10, 15));
        given(salaryService.versionInForce(eq(TENANT_ID), eq(EMPLOYEE_ID), any(LocalDate.class)))
                .willAnswer(inv -> {
                    LocalDate d = inv.getArgument(2);
                    if (!d.isBefore(LocalDate.of(2025, 10, 15))) {
                        return v;
                    }
                    // Pre-Oct-15: no version before joining date
                    throw new SalaryNotFoundException("No active salary version as of " + d);
                });

        TaxInput input = assembler.assemble(TENANT_ID, EMPLOYEE_ID, FY);
        assertThat(input.salary().months()).hasSize(6);
        assertThat(input.salary().annualTaxableSalary()).isEqualTo(Money.of(300000));
    }

    /**
     * B-1 variant: employee added to system with no CTC structure at all.
     * Every versionInForce call throws. Must succeed with zero annual salary.
     */
    @Test
    @DisplayName("B-1: no salary structure exists -> zero salary returned, no exception")
    void b1_noSalaryStructure_returnsZero() {
        stubCommon(LocalDate.of(2025, 4, 1));
        given(salaryService.versionInForce(eq(TENANT_ID), eq(EMPLOYEE_ID), any(LocalDate.class)))
                .willThrow(new SalaryNotFoundException("No salary version for employee"));

        TaxInput input = assembler.assemble(TENANT_ID, EMPLOYEE_ID, FY);

        assertThat(input.salary().annualTaxableSalary()).isEqualTo(Money.ZERO);
        // All 12 months projected (Apr to Mar), each at zero taxable salary
        assertThat(input.salary().months()).hasSize(12);
    }

    /**
     * B-1 happy path: employee joins Apr-01, version exists on the 1st.
     * Must project all 12 months correctly. Ensures fix does not break normal flow.
     */
    @Test
    @DisplayName("B-1: joining Apr-01, version on 1st -> 12 months projected, 6L annual salary")
    void b1_joinsFirstOfMonth_happyPath() {
        stubCommon(LocalDate.of(2025, 4, 1));
        given(salaryService.versionInForce(eq(TENANT_ID), eq(EMPLOYEE_ID), any(LocalDate.class)))
                .willReturn(version(LocalDate.of(2025, 4, 1)));
        stubTaxableBasic();

        TaxInput input = assembler.assemble(TENANT_ID, EMPLOYEE_ID, FY);

        // 12 months x 50,000 Basic = 6,00,000
        assertThat(input.salary().annualTaxableSalary()).isEqualTo(Money.of("600000"));
        assertThat(input.salary().months()).hasSize(12);
    }

    // ── B-2 tests ─────────────────────────────────────────────────────────────

    /**
     * B-2 core: Basic 50,000 (taxable=true), HRA 20,000 (taxable=false).
     *
     * Before fix: HRA loop added non-taxable HRA to hraSalaries regardless of isTaxable flag.
     * HRA exemption engine then deducted it from a salary projection that never included it
     * -> wrong (artificially lower) tax.
     *
     * After fix: non-taxable HRA excluded from hraSalaries. Every HraMonthSalary has hra=0.
     */
    @Test
    @DisplayName("B-2: non-taxable HRA must NOT appear in HraMonthSalary (hra=0 every month)")
    void b2_nonTaxableHra_excludedFromHraSalaries() {
        stubCommon(LocalDate.of(2025, 4, 1));
        given(salaryService.versionInForce(eq(TENANT_ID), eq(EMPLOYEE_ID), any(LocalDate.class)))
                .willReturn(versionWithHra(LocalDate.of(2025, 4, 1)));

        // BASIC taxable, HRA NOT taxable
        given(earningRepository.findAllByTenantIdAndDeletedFalse(TENANT_ID))
                .willReturn(
                        List.of(earning(BASIC_COMP, "BASIC", "BASIC", true), earning(HRA_COMP, "HRA", "HRA", false)));

        TaxInput input = assembler.assemble(TENANT_ID, EMPLOYEE_ID, FY);

        // Projection: non-taxable HRA excluded -> 12 x 50k = 6,00,000 (not 8,40,000)
        assertThat(input.salary().annualTaxableSalary()).isEqualTo(Money.of("600000"));

        List<HraMonthSalary> hs = input.hraSalaries();
        assertThat(hs).isNotEmpty();
        hs.forEach(h -> assertThat(h.hra())
                .as("Month %s: non-taxable HRA must be zero in hraSalaries", h.month())
                .isEqualTo(Money.ZERO));
        hs.forEach(h -> assertThat(h.basic())
                .as("Month %s: taxable Basic must be 50000 in hraSalaries", h.month())
                .isEqualTo(Money.of("50000")));
    }

    /**
     * B-2 positive: when HRA IS taxable, it must appear normally in hraSalaries.
     * Ensures the fix does not over-exclude taxable HRA.
     */
    @Test
    @DisplayName("B-2: taxable HRA MUST appear in HraMonthSalary (hra=20000 every month)")
    void b2_taxableHra_includedInHraSalaries() {
        stubCommon(LocalDate.of(2025, 4, 1));
        given(salaryService.versionInForce(eq(TENANT_ID), eq(EMPLOYEE_ID), any(LocalDate.class)))
                .willReturn(versionWithHra(LocalDate.of(2025, 4, 1)));

        // Both BASIC and HRA are taxable
        given(earningRepository.findAllByTenantIdAndDeletedFalse(TENANT_ID))
                .willReturn(
                        List.of(earning(BASIC_COMP, "BASIC", "BASIC", true), earning(HRA_COMP, "HRA", "HRA", true)));

        TaxInput input = assembler.assemble(TENANT_ID, EMPLOYEE_ID, FY);

        // Projection: both taxable -> 12 x (50k + 20k) = 8,40,000
        assertThat(input.salary().annualTaxableSalary()).isEqualTo(Money.of("840000"));

        input.hraSalaries().forEach(h -> assertThat(h.hra())
                .as("Month %s: taxable HRA must be 20000", h.month())
                .isEqualTo(Money.of("20000")));
    }

    // ── Mid-month joiner and mid-year revision: per-month HRA / EPF / PT inputs ──

    /**
     * Mid-month joiner, full working: joins 2025-10-15, first version effective that day. The projection
     * resolves October's version as of the joining date; the HRA/EPF/PT loop must read that same version.
     * On the old code it looked the version up under 2025-10-01, found nothing, and recorded October as
     * Basic 0 / HRA 0 / no EPF / no PT while October's salary was still counted.
     */
    @Test
    @DisplayName("Mid-month joiner Oct-15: October's Basic/HRA, structure EPF and PT are all counted")
    void midMonthJoiner_joiningMonthCarriesHraEpfAndPt() {
        LocalDate joining = LocalDate.of(2025, 10, 15);
        stubCommon(joining, WORK_LOCATION_ID);
        given(earningRepository.findAllByTenantIdAndDeletedFalse(TENANT_ID))
                .willReturn(
                        List.of(earning(BASIC_COMP, "BASIC", "BASIC", true), earning(HRA_COMP, "HRA", "HRA", true)));
        SalaryVersionResponse v = versionWithHraAndEpf(joining, BigDecimal.valueOf(1800));
        given(salaryService.versionInForce(eq(TENANT_ID), eq(EMPLOYEE_ID), any(LocalDate.class)))
                .willAnswer(inv -> {
                    LocalDate d = inv.getArgument(2);
                    if (!d.isBefore(joining)) {
                        return v;
                    }
                    throw new SalaryNotFoundException("No active salary version as of " + d);
                });
        given(workLocationService.list(false)).willReturn(List.of(workLocation(WORK_LOCATION_ID, "MH")));
        given(professionalTaxService.resolve(eq(TENANT_ID), eq("MH"), any(), any(), any()))
                .willReturn(Money.of("200"));

        TaxInput input = assemblerWithPt().assemble(TENANT_ID, EMPLOYEE_ID, FY);

        // Oct..Mar = 6 months x (50,000 + 20,000)
        assertThat(input.salary().annualTaxableSalary()).isEqualTo(Money.of("420000"));
        assertThat(input.hraSalaries()).hasSize(6);
        HraMonthSalary october = input.hraSalaries().get(0);
        assertThat(october.month()).isEqualTo(YearMonth.of(2025, 10));
        assertThat(october.basic()).isEqualTo(Money.of("50000"));
        assertThat(october.hra()).isEqualTo(Money.of("20000"));
        // 6 months x 1,800 EPF (old code: 5 months = 9,000)
        assertThat(input.epfFromCtc()).contains(Money.of("10800"));
        // 6 months x 200 PT (old code: 5 months = 1,000)
        assertThat(input.professionalTaxFromService()).contains(Money.of("1200"));
    }

    /**
     * CTC revised mid-year: Apr-Sep on one version, Oct-Mar on a revised one. Each month's HRA inputs
     * must come from the version in force that month.
     */
    @Test
    @DisplayName("CTC revised from October: hraSalaries carry the old Basic/HRA before, the new after")
    void ctcRevisedMidYear_hraSalariesFollowEachVersion() {
        stubCommon(LocalDate.of(2025, 4, 1));
        given(earningRepository.findAllByTenantIdAndDeletedFalse(TENANT_ID))
                .willReturn(
                        List.of(earning(BASIC_COMP, "BASIC", "BASIC", true), earning(HRA_COMP, "HRA", "HRA", true)));
        SalaryVersionResponse original = versionWithHra(LocalDate.of(2025, 4, 1));
        SalaryVersionResponse revised =
                versionWith(LocalDate.of(2025, 10, 1), BigDecimal.valueOf(60000), BigDecimal.valueOf(24000), List.of());
        given(salaryService.versionInForce(eq(TENANT_ID), eq(EMPLOYEE_ID), any(LocalDate.class)))
                .willAnswer(inv -> {
                    LocalDate d = inv.getArgument(2);
                    return d.isBefore(LocalDate.of(2025, 10, 1)) ? original : revised;
                });

        TaxInput input = assembler.assemble(TENANT_ID, EMPLOYEE_ID, FY);

        assertThat(input.hraSalaries()).hasSize(12);
        HraMonthSalary september = input.hraSalaries().get(5);
        HraMonthSalary october = input.hraSalaries().get(6);
        assertThat(september.basic()).isEqualTo(Money.of("50000"));
        assertThat(september.hra()).isEqualTo(Money.of("20000"));
        assertThat(october.basic()).isEqualTo(Money.of("60000"));
        assertThat(october.hra()).isEqualTo(Money.of("24000"));
        // 6 x 70,000 + 6 x 84,000
        assertThat(input.salary().annualTaxableSalary()).isEqualTo(Money.of("924000"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TaxInputAssembler assemblerWithPt() {
        return new TaxInputAssembler(
                declarationService,
                employeeService,
                personalService,
                salaryService,
                earningRepository,
                prevEmploymentRepository,
                houseRentRepository,
                homeLoanRepository,
                letOutPropertyRepository,
                section6ARepository,
                preTaxDeductionRepository,
                otherIncomeRepository,
                section6AItemReader,
                professionalTaxService,
                workLocationService,
                ctcEpfComponentRepository);
    }

    private WorkLocationResponse workLocation(UUID id, String stateCode) {
        return new WorkLocationResponse(
                id,
                TENANT_ID,
                "WL1",
                "Mumbai",
                null,
                null,
                "Mumbai",
                "Maharashtra",
                stateCode,
                null,
                "IN",
                false,
                true,
                null,
                null);
    }

    /** Stubs declaration header, employee, personal record, and all empty repository lookups. */
    private void stubCommon(LocalDate joining) {
        stubCommon(joining, null);
    }

    private void stubCommon(LocalDate joining, UUID workLocationId) {
        EmployeeInvestmentDeclaration decl = new EmployeeInvestmentDeclaration();
        decl.setId(DECL_ID);
        decl.setTenantId(TENANT_ID);
        decl.setEmployeeId(EMPLOYEE_ID);
        decl.setFinancialYear(FY.label());
        given(declarationService.find(EMPLOYEE_ID, FY.label())).willReturn(java.util.Optional.of(decl));

        given(employeeService.get(EMPLOYEE_ID))
                .willReturn(new com.infinevo.core.employee.EmployeeResponse(
                        EMPLOYEE_ID,
                        TENANT_ID,
                        "EMP001",
                        "Test",
                        null, // middleName
                        "Employee",
                        null, // gender
                        joining,
                        null, // terminationDate
                        com.infinevo.core.employee.EmploymentStatus.ACTIVE,
                        null, // workEmail
                        null, // mobile
                        false, // portalEnabled
                        null, // userAccountId
                        null, // departmentId
                        null, // designationId
                        workLocationId,
                        null, // createdAt
                        null)); // updatedAt
        given(personalService.get(EMPLOYEE_ID))
                .willReturn(new com.infinevo.core.employee.detail.EmployeePersonalResponse(
                        UUID.randomUUID(),
                        TENANT_ID,
                        EMPLOYEE_ID,
                        LocalDate.of(1990, 6, 15), // dateOfBirth
                        null, // maritalStatus
                        null, // nationality
                        null, // ethnicity
                        null, // fatherName
                        null, // differentlyAbledType
                        false, // eligibleForFullTaxExemption
                        null, // createdAt
                        null)); // updatedAt

        given(earningRepository.findAllByTenantIdAndDeletedFalse(TENANT_ID)).willReturn(List.of());
        given(prevEmploymentRepository.findByTenantIdAndDeclarationId(TENANT_ID, DECL_ID))
                .willReturn(List.of());
        given(houseRentRepository.findByTenantIdAndDeclarationIdOrderByFromMonthAsc(TENANT_ID, DECL_ID))
                .willReturn(List.of());
        given(homeLoanRepository.findByTenantIdAndDeclarationId(TENANT_ID, DECL_ID))
                .willReturn(List.of());
        given(letOutPropertyRepository.findByTenantIdAndDeclarationId(TENANT_ID, DECL_ID))
                .willReturn(List.of());
        given(section6ARepository.findByTenantIdAndDeclarationId(TENANT_ID, DECL_ID))
                .willReturn(List.of());
        given(preTaxDeductionRepository.findByTenantIdAndDeclarationId(TENANT_ID, DECL_ID))
                .willReturn(List.of());
        given(otherIncomeRepository.findByTenantIdAndDeclarationId(TENANT_ID, DECL_ID))
                .willReturn(List.of());
        // EPF: repository returns empty list -> no EPF calculated from structure
        given(ctcEpfComponentRepository.findByTenantIdAndCtcStructureId(any(), any()))
                .willReturn(List.of());
    }

    private void stubTaxableBasic() {
        given(earningRepository.findAllByTenantIdAndDeletedFalse(TENANT_ID))
                .willReturn(List.of(earning(BASIC_COMP, "BASIC", "BASIC", true)));
    }

    /** Builds an Earning entity with the given taxable flag. Injects id via reflection. */
    private Earning earning(UUID id, String code, String type, boolean taxable) {
        Earning e = new Earning(TENANT_ID, "test");
        e.setCode(code);
        e.setName(type);
        e.setEarningType(type);
        e.setTaxable(taxable);
        try {
            java.lang.reflect.Field f = com.infinevo.payroll.component.SalaryComponent.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(e, id);
        } catch (Exception ex) {
            throw new RuntimeException("Could not set Earning.id in test", ex);
        }
        return e;
    }

    /** Salary version with only BASIC 50,000/month. */
    private SalaryVersionResponse version(LocalDate effectiveFrom) {
        return new SalaryVersionResponse(
                UUID.randomUUID(),
                EMPLOYEE_ID,
                effectiveFrom,
                BigDecimal.valueOf(600000),
                BigDecimal.valueOf(50000),
                false,
                null,
                "test-version",
                BigDecimal.ZERO,
                List.of(item(BASIC_COMP, "BASIC", BigDecimal.valueOf(50000))),
                List.of(),
                List.of(),
                List.of(),
                null);
    }

    /** Salary version with BASIC 50,000 + HRA 20,000 per month. */
    private SalaryVersionResponse versionWithHra(LocalDate effectiveFrom) {
        return versionWith(effectiveFrom, BigDecimal.valueOf(50000), BigDecimal.valueOf(20000), List.of());
    }

    /** Salary version with BASIC 50,000 + HRA 20,000 per month and an EPF_EMPLOYEE statutory line. */
    private SalaryVersionResponse versionWithHraAndEpf(LocalDate effectiveFrom, BigDecimal epfMonthly) {
        SalaryStatutoryItemResponse epf = new SalaryStatutoryItemResponse(
                UUID.randomUUID(),
                "EPF_EMPLOYEE",
                ContributionShare.EMPLOYEE,
                BigDecimal.valueOf(15000),
                BigDecimal.valueOf(12),
                epfMonthly,
                epfMonthly.multiply(BigDecimal.valueOf(12)),
                false);
        return versionWith(effectiveFrom, BigDecimal.valueOf(50000), BigDecimal.valueOf(20000), List.of(epf));
    }

    /** Salary version with the given monthly BASIC and HRA and statutory lines. */
    private SalaryVersionResponse versionWith(
            LocalDate effectiveFrom, BigDecimal basic, BigDecimal hra, List<SalaryStatutoryItemResponse> statutory) {
        BigDecimal monthly = basic.add(hra);
        return new SalaryVersionResponse(
                UUID.randomUUID(),
                EMPLOYEE_ID,
                effectiveFrom,
                monthly.multiply(BigDecimal.valueOf(12)),
                monthly,
                false,
                null,
                "test-version-with-hra",
                BigDecimal.ZERO,
                List.of(item(BASIC_COMP, "BASIC", basic), item(HRA_COMP, "HRA", hra)),
                List.of(),
                List.of(),
                statutory,
                null);
    }

    /** Minimal SalaryComponentItemResponse for a version earnings list entry. */
    private SalaryComponentItemResponse item(UUID componentId, String code, BigDecimal monthly) {
        return new SalaryComponentItemResponse(
                UUID.randomUUID(),
                componentId,
                code,
                code,
                null,
                null,
                null,
                monthly,
                monthly.multiply(BigDecimal.valueOf(12)),
                true,
                true,
                "MONTHLY",
                null);
    }

    @Test
    @DisplayName("assemble() succeeds with null declarationId when declaration is absent")
    void assembleWithoutDeclarationSucceeds() {
        given(declarationService.find(EMPLOYEE_ID, FY.label())).willReturn(java.util.Optional.empty());
        given(employeeService.get(EMPLOYEE_ID))
                .willReturn(new com.infinevo.core.employee.EmployeeResponse(
                        EMPLOYEE_ID,
                        TENANT_ID,
                        "EMP001",
                        "Test",
                        null,
                        "Employee",
                        null,
                        LocalDate.of(2025, 4, 1),
                        null,
                        com.infinevo.core.employee.EmploymentStatus.ACTIVE,
                        "emp@test.com",
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        Instant.now(),
                        Instant.now()));

        TaxInput input = assembler.assemble(TENANT_ID, EMPLOYEE_ID, FY);

        assertThat(input).isNotNull();
        assertThat(input.declarationId()).isNull();
        assertThat(input.section6A()).isEmpty();
        assertThat(input.houseRent()).isEmpty();
        assertThat(input.homeLoans()).isEmpty();
        assertThat(input.letOutProperties()).isEmpty();
        assertThat(input.preTaxDeductions()).isEmpty();
        assertThat(input.otherIncome()).isEmpty();
        assertThat(input.isStayingInRentedHouse()).isFalse();
        assertThat(input.isRepayingSelfOccupiedLoan()).isFalse();
        assertThat(input.hasLetOutProperty()).isFalse();
    }
}
