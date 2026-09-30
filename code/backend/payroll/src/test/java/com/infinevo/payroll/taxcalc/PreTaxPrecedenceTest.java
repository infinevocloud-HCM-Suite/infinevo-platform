package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.employee.detail.EmployeePersonalService;
import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryComponentItemResponse;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.payroll.statutory.lines.ContributionShare;
import com.infinevo.payroll.statutory.lines.CtcEpfComponentRepository;
import com.infinevo.payroll.statutory.lines.SalaryStatutoryItemResponse;
import com.infinevo.payroll.statutory.lines.StatutoryComponentCode;
import com.infinevo.payroll.statutory.pt.ProfessionalTaxService;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxcalc.model.TaxInput;
import com.infinevo.payroll.taxcalc.model.TaxSlabDetail;
import com.infinevo.payroll.taxcalc.reader.TaxRuleReader;
import com.infinevo.payroll.taxcalc.reader.model.CessSurchargeRule;
import com.infinevo.payroll.taxcalc.reader.model.HomeLoanRule;
import com.infinevo.payroll.taxcalc.reader.model.HraRule;
import com.infinevo.payroll.taxcalc.reader.model.LetOutRule;
import com.infinevo.payroll.taxcalc.reader.model.OtherIncomeRule;
import com.infinevo.payroll.taxcalc.reader.model.Section87aRebateRule;
import com.infinevo.payroll.taxcalc.reader.model.StandardDeductionRule;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPreTaxDeduction;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPreTaxDeductionRepository;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPrevEmploymentRepository;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvSection6ARepository;
import com.infinevo.payroll.taxdeclaration.deductions.PreTaxDeductionKind;
import com.infinevo.payroll.taxdeclaration.deductions.Section6AItemReader;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoanRepository;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRentRepository;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutPropertyRepository;
import com.infinevo.payroll.taxdeclaration.summary.EmployeeInvOtherIncomeRepository;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Pre-Tax deduction precedence rules driven through {@link TaxInputAssembler}
 * and {@link OldRegimeCalculator} (W-33.2 spec § 3a, § 7).
 */
class PreTaxPrecedenceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID declarationId = UUID.randomUUID();
    private final UUID workLocationId = UUID.randomUUID();
    private final UUID basicCompId = UUID.randomUUID();
    private final FinancialYear fy = FinancialYear.parse("2025-2026");

    private TaxDeclarationService declarationService;
    private EmployeeService employeeService;
    private EmployeePersonalService employeePersonalService;
    private EmployeeSalaryService employeeSalaryService;
    private EarningRepository earningRepository;
    private EmployeeInvPrevEmploymentRepository prevEmploymentRepository;
    private EmployeeInvHouseRentRepository houseRentRepository;
    private EmployeeInvHomeLoanRepository homeLoanRepository;
    private EmployeeInvLetOutPropertyRepository letOutPropertyRepository;
    private EmployeeInvSection6ARepository section6ARepository;
    private EmployeeInvPreTaxDeductionRepository preTaxDeductionRepository;
    private EmployeeInvOtherIncomeRepository otherIncomeRepository;
    private Section6AItemReader section6AItemReader;
    private ProfessionalTaxService professionalTaxService;
    private WorkLocationService workLocationService;
    private CtcEpfComponentRepository ctcEpfComponentRepository;

    private TaxInputAssembler assembler;
    private OldRegimeCalculator calculator;

    @BeforeEach
    void setUp() {
        declarationService = mock(TaxDeclarationService.class);
        employeeService = mock(EmployeeService.class);
        employeePersonalService = mock(EmployeePersonalService.class);
        employeeSalaryService = mock(EmployeeSalaryService.class);
        earningRepository = mock(EarningRepository.class);
        prevEmploymentRepository = mock(EmployeeInvPrevEmploymentRepository.class);
        houseRentRepository = mock(EmployeeInvHouseRentRepository.class);
        homeLoanRepository = mock(EmployeeInvHomeLoanRepository.class);
        letOutPropertyRepository = mock(EmployeeInvLetOutPropertyRepository.class);
        section6ARepository = mock(EmployeeInvSection6ARepository.class);
        preTaxDeductionRepository = mock(EmployeeInvPreTaxDeductionRepository.class);
        otherIncomeRepository = mock(EmployeeInvOtherIncomeRepository.class);
        section6AItemReader = mock(Section6AItemReader.class);
        professionalTaxService = mock(ProfessionalTaxService.class);
        workLocationService = mock(WorkLocationService.class);
        ctcEpfComponentRepository = mock(CtcEpfComponentRepository.class);

        EmployeeInvestmentDeclaration declaration = mock(EmployeeInvestmentDeclaration.class);
        when(declaration.getId()).thenReturn(declarationId);
        when(declarationService.find(employeeId, fy.label())).thenReturn(Optional.of(declaration));

        Earning basicEarning = mock(Earning.class);
        when(basicEarning.getId()).thenReturn(basicCompId);
        when(basicEarning.isTaxable()).thenReturn(true);
        when(basicEarning.getEarningType()).thenReturn("BASIC");
        when(earningRepository.findAllByTenantIdAndDeletedFalse(tenantId)).thenReturn(List.of(basicEarning));

        when(section6AItemReader.groupCap("80C_GROUP")).thenReturn(new BigDecimal("150000"));
        when(section6AItemReader.activeItems("OLD")).thenReturn(List.of());

        TaxRuleReader ruleReader = mock(TaxRuleReader.class);
        when(ruleReader.hra(fy))
                .thenReturn(new HraRule(
                        "2025-2026", BigDecimal.valueOf(10.00), BigDecimal.valueOf(50.00), BigDecimal.valueOf(40.00)));
        when(ruleReader.standardDeduction(fy, TaxRegime.OLD))
                .thenReturn(new StandardDeductionRule("2025-2026", "OLD", Money.of("50000"), "Standard deduction"));
        when(ruleReader.homeLoan(fy, "24B", "INTEREST", "SELF_OCCUPIED"))
                .thenReturn(new HomeLoanRule(
                        "2025-2026",
                        "24B",
                        "Interest on borrowed capital",
                        "INTEREST",
                        "SELF_OCCUPIED",
                        Money.of("200000"),
                        null,
                        null,
                        false));
        when(ruleReader.letOut(fy))
                .thenReturn(
                        new LetOutRule("2025-2026", "OLD", BigDecimal.valueOf(30.00), Money.of("200000"), true, true));
        when(ruleReader.otherIncomeRule(fy, "80TTA"))
                .thenReturn(new OtherIncomeRule(
                        "2025-2026",
                        "80TTA",
                        "Interest on savings",
                        "DEDUCTION",
                        "OLD",
                        Money.of("10000"),
                        BigDecimal.valueOf(100),
                        false,
                        false));
        when(ruleReader.otherIncomeRule(fy, "80TTB"))
                .thenReturn(new OtherIncomeRule(
                        "2025-2026",
                        "80TTB",
                        "Interest income, senior citizen",
                        "DEDUCTION",
                        "OLD",
                        Money.of("50000"),
                        BigDecimal.valueOf(100),
                        false,
                        true));
        when(ruleReader.homeLoanRules(fy)).thenReturn(List.of());
        when(ruleReader.slabs(eq(fy), eq(TaxRegime.OLD), any()))
                .thenReturn(List.of(
                        new TaxSlabDetail(Money.ZERO, Money.of("250000"), BigDecimal.ZERO, 1),
                        new TaxSlabDetail(Money.of("250000"), Money.of("500000"), new BigDecimal("5"), 2),
                        new TaxSlabDetail(Money.of("500000"), Money.of("1000000"), new BigDecimal("20"), 3),
                        new TaxSlabDetail(Money.of("1000000"), null, new BigDecimal("30"), 4)));
        when(ruleReader.rebate(fy, TaxRegime.OLD))
                .thenReturn(new Section87aRebateRule(
                        "2025-2026", "OLD", Money.of("500000"), Money.of("12500"), false, "Section 87A rebate"));
        when(ruleReader.surchargeBands(fy, TaxRegime.OLD)).thenReturn(List.of());
        when(ruleReader.cess(fy, TaxRegime.OLD))
                .thenReturn(new CessSurchargeRule(
                        "2025-2026", "CESS", "BOTH", null, null, BigDecimal.valueOf(4.00), false, "Cess"));

        assembler = new TaxInputAssembler(
                declarationService,
                employeeService,
                employeePersonalService,
                employeeSalaryService,
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
        calculator = new OldRegimeCalculator(ruleReader, section6AItemReader);
    }

    @Test
    @DisplayName("Structure wins: CTC EPF and PT service override declared pre-tax rows")
    void structureWinsOverDeclaredRows() {
        when(employeeService.get(employeeId)).thenReturn(employeeWithWorkLocation(workLocationId));
        when(workLocationService.list(false)).thenReturn(List.of(workLocation(workLocationId, "MH")));

        // Structure has 3750/month EPF (= 45,000/yr) and PT service resolves 200/month (= 2,400/yr)
        SalaryStatutoryItemResponse epfLine = new SalaryStatutoryItemResponse(
                UUID.randomUUID(),
                StatutoryComponentCode.EPF_EMPLOYEE.name(),
                ContributionShare.EMPLOYEE,
                new BigDecimal("31250"),
                new BigDecimal("12"),
                new BigDecimal("3750"),
                new BigDecimal("45000"),
                true);
        SalaryVersionResponse version = salaryVersion(List.of(epfLine));
        when(employeeSalaryService.versionInForce(eq(tenantId), eq(employeeId), any()))
                .thenReturn(version);
        when(professionalTaxService.resolve(eq(tenantId), eq("MH"), any(), any(), any()))
                .thenReturn(Money.of("200"));

        // Declared rows have different amounts (40,000 EPF and 1,800 PT)
        EmployeeInvPreTaxDeduction declEpf = preTaxRow(PreTaxDeductionKind.EMPLOYEE_PF, "40000");
        EmployeeInvPreTaxDeduction declPt = preTaxRow(PreTaxDeductionKind.PROFESSIONAL_TAX, "1800");
        when(preTaxDeductionRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .thenReturn(List.of(declEpf, declPt));

        TaxInput input = assembler.assemble(tenantId, employeeId, fy);

        assertThat(input.epfFromCtc()).contains(Money.of("45000"));
        assertThat(input.resolvedEmployeePf()).isEqualTo(Money.of("45000"));
        assertThat(input.professionalTaxFromService()).contains(Money.of("2400"));
        assertThat(input.resolvedProfessionalTax()).isEqualTo(Money.of("2400"));

        TaxComputation computation = calculator.compute(input, fy);
        assertThat(computation.professionalTax()).isEqualTo(Money.of("2400"));
        assertThat(computation.chapterViaDeductions()).isEqualTo(Money.of("45000"));
        assertThat(computation.assumptions())
                .doesNotContain(
                        "No Employee PF found in salary structure or tax declaration; assumed zero",
                        "No Professional Tax found in salary structure or tax declaration; assumed zero");
    }

    @Test
    @DisplayName("Declared fallback: when CTC has no EPF and employee has no PT location, declared rows are used")
    void declaredFallbackWhenStructureAbsent() {
        when(employeeService.get(employeeId)).thenReturn(employeeWithWorkLocation(null));
        SalaryVersionResponse version = salaryVersion(List.of());
        when(employeeSalaryService.versionInForce(eq(tenantId), eq(employeeId), any()))
                .thenReturn(version);

        EmployeeInvPreTaxDeduction declEpf = preTaxRow(PreTaxDeductionKind.EMPLOYEE_PF, "40000");
        EmployeeInvPreTaxDeduction declPt = preTaxRow(PreTaxDeductionKind.PROFESSIONAL_TAX, "1800");
        when(preTaxDeductionRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .thenReturn(List.of(declEpf, declPt));

        TaxInput input = assembler.assemble(tenantId, employeeId, fy);

        assertThat(input.epfFromCtc()).isEmpty();
        assertThat(input.resolvedEmployeePf()).isEqualTo(Money.of("40000"));
        assertThat(input.professionalTaxFromService()).isEmpty();
        assertThat(input.resolvedProfessionalTax()).isEqualTo(Money.of("1800"));

        TaxComputation computation = calculator.compute(input, fy);
        assertThat(computation.professionalTax()).isEqualTo(Money.of("1800"));
        assertThat(computation.chapterViaDeductions()).isEqualTo(Money.of("40000"));
        assertThat(computation.assumptions())
                .doesNotContain(
                        "No Employee PF found in salary structure or tax declaration; assumed zero",
                        "No Professional Tax found in salary structure or tax declaration; assumed zero");
    }

    @Test
    @DisplayName("Neither present: resolves to ZERO and records explicit assumptions for missing EPF and PT")
    void neitherPresentYieldsZeroAndAssumptions() {
        when(employeeService.get(employeeId)).thenReturn(employeeWithWorkLocation(null));
        SalaryVersionResponse version = salaryVersion(List.of());
        when(employeeSalaryService.versionInForce(eq(tenantId), eq(employeeId), any()))
                .thenReturn(version);
        when(preTaxDeductionRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .thenReturn(List.of());

        TaxInput input = assembler.assemble(tenantId, employeeId, fy);

        assertThat(input.epfFromCtc()).isEmpty();
        assertThat(input.resolvedEmployeePf()).isEqualTo(Money.ZERO);
        assertThat(input.professionalTaxFromService()).isEmpty();
        assertThat(input.resolvedProfessionalTax()).isEqualTo(Money.ZERO);

        TaxComputation computation = calculator.compute(input, fy);
        assertThat(computation.professionalTax()).isEqualTo(Money.ZERO);
        assertThat(computation.chapterViaDeductions()).isEqualTo(Money.ZERO);
        assertThat(computation.assumptions())
                .contains(
                        "No Employee PF found in salary structure or tax declaration; assumed zero",
                        "No Professional Tax found in salary structure or tax declaration; assumed zero");
    }

    private EmployeeResponse employeeWithWorkLocation(UUID locId) {
        return new EmployeeResponse(
                employeeId,
                tenantId,
                "EMP-001",
                "Asha",
                null,
                "Rao",
                "FEMALE",
                LocalDate.of(2024, 4, 1),
                null,
                EmploymentStatus.ACTIVE,
                "asha@example.com",
                "9999999999",
                true,
                null,
                null,
                null,
                locId,
                Instant.now(),
                Instant.now());
    }

    private WorkLocationResponse workLocation(UUID locId, String stateCode) {
        return new WorkLocationResponse(
                locId,
                tenantId,
                "LOC-MH",
                "Mumbai",
                "Line 1",
                null,
                "Mumbai",
                "Maharashtra",
                stateCode,
                "400001",
                "IN",
                true,
                true,
                Instant.now(),
                Instant.now());
    }

    private SalaryVersionResponse salaryVersion(List<SalaryStatutoryItemResponse> statutory) {
        SalaryComponentItemResponse basicItem = new SalaryComponentItemResponse(
                UUID.randomUUID(),
                basicCompId,
                "BASIC",
                "Basic Salary",
                null,
                new BigDecimal("50000"),
                null,
                new BigDecimal("50000"),
                new BigDecimal("600000"),
                true,
                true,
                "MONTHLY",
                null);
        return new SalaryVersionResponse(
                UUID.randomUUID(),
                employeeId,
                LocalDate.of(2025, 4, 1),
                new BigDecimal("600000"),
                new BigDecimal("50000"),
                false,
                null,
                null,
                null,
                List.of(basicItem),
                List.of(),
                List.of(),
                statutory);
    }

    private EmployeeInvPreTaxDeduction preTaxRow(PreTaxDeductionKind kind, String amount) {
        EmployeeInvPreTaxDeduction row = mock(EmployeeInvPreTaxDeduction.class);
        when(row.getKind()).thenReturn(kind);
        when(row.getAmount()).thenReturn(new BigDecimal(amount));
        return row;
    }
}
