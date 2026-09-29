package com.infinevo.payroll.taxdeclaration.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxdeclaration.DeclarationStatus;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPreTaxDeduction;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPreTaxDeductionRepository;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPrevEmployment;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPrevEmploymentRepository;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvSection6A;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvSection6ARepository;
import com.infinevo.payroll.taxdeclaration.deductions.EnteredBy;
import com.infinevo.payroll.taxdeclaration.deductions.PreTaxDeductionKind;
import com.infinevo.payroll.taxdeclaration.deductions.PrevEmploymentKind;
import com.infinevo.payroll.taxdeclaration.deductions.Section6AItemReader;
import com.infinevo.payroll.taxdeclaration.deductions.Section6AItemReader.Section6AItem;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoan;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoanRepository;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRent;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRentRepository;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutProperty;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutPropertyRepository;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryFigures;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryResponse;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeclaredTotalsTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID declarationId = UUID.randomUUID();
    private final String fy = "2024-2025";

    private TaxDeclarationService taxDeclarationService;
    private EmployeeInvTaxSummaryRepository taxSummaryRepository;
    private EmployeeInvOtherIncomeRepository otherIncomeRepository;
    private EmployeeInvHouseRentRepository houseRentRepository;
    private EmployeeInvHomeLoanRepository homeLoanRepository;
    private EmployeeInvLetOutPropertyRepository letOutPropertyRepository;
    private EmployeeInvSection6ARepository section6ARepository;
    private EmployeeInvPreTaxDeductionRepository preTaxDeductionRepository;
    private EmployeeInvPrevEmploymentRepository prevEmploymentRepository;
    private Section6AItemReader section6AItemReader;
    private EmployeeService employeeService;

    private TaxSummaryServiceImpl taxSummaryService;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);

        taxDeclarationService = mock(TaxDeclarationService.class);
        taxSummaryRepository = mock(EmployeeInvTaxSummaryRepository.class);
        otherIncomeRepository = mock(EmployeeInvOtherIncomeRepository.class);
        houseRentRepository = mock(EmployeeInvHouseRentRepository.class);
        homeLoanRepository = mock(EmployeeInvHomeLoanRepository.class);
        letOutPropertyRepository = mock(EmployeeInvLetOutPropertyRepository.class);
        section6ARepository = mock(EmployeeInvSection6ARepository.class);
        preTaxDeductionRepository = mock(EmployeeInvPreTaxDeductionRepository.class);
        prevEmploymentRepository = mock(EmployeeInvPrevEmploymentRepository.class);
        section6AItemReader = mock(Section6AItemReader.class);
        employeeService = mock(EmployeeService.class);

        taxSummaryService = new TaxSummaryServiceImpl(
                taxDeclarationService,
                taxSummaryRepository,
                otherIncomeRepository,
                houseRentRepository,
                homeLoanRepository,
                letOutPropertyRepository,
                section6ARepository,
                preTaxDeductionRepository,
                prevEmploymentRepository,
                section6AItemReader,
                employeeService);

        EmployeeInvestmentDeclaration decl = new EmployeeInvestmentDeclaration(tenantId, employeeId, fy, "OLD");
        decl.setId(declarationId);
        decl.setStatus(DeclarationStatus.DRAFT);

        given(taxDeclarationService.require(employeeId, fy)).willReturn(decl);
        given(taxDeclarationService.require(declarationId)).willReturn(decl);
        given(taxSummaryRepository.findByTenantIdAndDeclarationIdAndRegime(tenantId, declarationId, "OLD"))
                .willReturn(Optional.of(new EmployeeInvTaxSummary(tenantId, declarationId, "OLD")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("DeclaredTotals correctly calculates multi-section totals with scale 2 and group aggregation")
    void testDeclaredTotalsCalculation() {
        // 1. House rent: Period 1 (Apr-Sep, 6 mos @ 25k = 150k) + Period 2 (Oct-Mar, 6 mos @ 30k = 180k) = 330k
        List<EmployeeInvHouseRent> rents = List.of(
                new EmployeeInvHouseRent(
                        tenantId,
                        declarationId,
                        LocalDate.of(2024, 4, 1),
                        LocalDate.of(2024, 9, 1),
                        "Mumbai",
                        "Landlord A",
                        "ABCDE1234F",
                        true,
                        new BigDecimal("25000.0000")),
                new EmployeeInvHouseRent(
                        tenantId,
                        declarationId,
                        LocalDate.of(2024, 10, 1),
                        LocalDate.of(2025, 3, 1),
                        "Delhi",
                        "Landlord B",
                        "XYZPK9876Q",
                        false,
                        new BigDecimal("30000.0000")));
        given(houseRentRepository.findByTenantIdAndDeclarationIdOrderByFromMonthAsc(tenantId, declarationId))
                .willReturn(rents);

        // 2. Home loan: Principal 150k, Interest 200k
        List<EmployeeInvHomeLoan> loans = List.of(new EmployeeInvHomeLoan(
                tenantId,
                declarationId,
                "SBI",
                "AAACS1234F",
                new BigDecimal("150000.0000"),
                new BigDecimal("200000.0000"),
                true,
                null));
        given(homeLoanRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .willReturn(loans);

        // 3. Let out property: net loss -104,000.00
        List<EmployeeInvLetOutProperty> letOutProps = List.of(new EmployeeInvLetOutProperty(
                tenantId, declarationId, "Villa", "Bengaluru", new BigDecimal("-104000.0000")));
        given(letOutPropertyRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .willReturn(letOutProps);

        // 4. Section 6A: 80C (50k), 80CCC (30k) under 80C_GROUP, and 80D (25k) ungrouped
        UUID s6a80cId = UUID.randomUUID();
        UUID s6a80cccId = UUID.randomUUID();
        UUID s6a80dId = UUID.randomUUID();

        List<EmployeeInvSection6A> s6aLines = List.of(
                new EmployeeInvSection6A(tenantId, declarationId, s6a80cId, "LIC", new BigDecimal("50000.0000")),
                new EmployeeInvSection6A(tenantId, declarationId, s6a80cccId, "Annuity", new BigDecimal("30000.0000")),
                new EmployeeInvSection6A(tenantId, declarationId, s6a80dId, "Health", new BigDecimal("25000.0000")));
        given(section6ARepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .willReturn(s6aLines);

        given(section6AItemReader.findById(s6a80cId))
                .willReturn(Optional.of(new Section6AItem(
                        s6a80cId,
                        "80C",
                        "INVESTMENT",
                        "80C",
                        "desc",
                        new BigDecimal("150000.0000"),
                        "80C_GROUP",
                        true,
                        false,
                        false,
                        false,
                        1,
                        true)));
        given(section6AItemReader.findById(s6a80cccId))
                .willReturn(Optional.of(new Section6AItem(
                        s6a80cccId,
                        "80CCC",
                        "INVESTMENT",
                        "80CCC",
                        "desc",
                        new BigDecimal("150000.0000"),
                        "80C_GROUP",
                        true,
                        false,
                        false,
                        false,
                        2,
                        true)));
        given(section6AItemReader.findById(s6a80dId))
                .willReturn(Optional.of(new Section6AItem(
                        s6a80dId,
                        "80D",
                        "HEALTH",
                        "80D",
                        "desc",
                        new BigDecimal("100000.0000"),
                        null,
                        false,
                        true,
                        false,
                        false,
                        6,
                        true)));

        // 5. Pre-tax: VPF 20k + NPS 30k = 50k
        List<EmployeeInvPreTaxDeduction> preTax = List.of(
                new EmployeeInvPreTaxDeduction(
                        tenantId, declarationId, PreTaxDeductionKind.VPF, new BigDecimal("20000.0000")),
                new EmployeeInvPreTaxDeduction(
                        tenantId, declarationId, PreTaxDeductionKind.NPS_EMPLOYEE, new BigDecimal("30000.0000")));
        given(preTaxDeductionRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .willReturn(preTax);

        // 6. Prev employment: Income 500k, Tax 45k
        List<EmployeeInvPrevEmployment> prevEmp = List.of(
                new EmployeeInvPrevEmployment(
                        tenantId,
                        declarationId,
                        PrevEmploymentKind.INCOME,
                        new BigDecimal("500000.0000"),
                        "Old Corp",
                        "MUMB12345F",
                        EnteredBy.EMPLOYEE),
                new EmployeeInvPrevEmployment(
                        tenantId,
                        declarationId,
                        PrevEmploymentKind.INCOME_TAX_DEDUCTED,
                        new BigDecimal("45000.0000"),
                        "Old Corp",
                        "MUMB12345F",
                        EnteredBy.EMPLOYEE));
        given(prevEmploymentRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .willReturn(prevEmp);

        // 7. Other income: Savings 10k + FD 20k = 30k
        List<EmployeeInvOtherIncome> otherIncome = List.of(
                new EmployeeInvOtherIncome(
                        tenantId, declarationId, OtherIncomeKind.SAVINGS_INTEREST, null, new BigDecimal("10000.0000")),
                new EmployeeInvOtherIncome(
                        tenantId, declarationId, OtherIncomeKind.FD_INTEREST, null, new BigDecimal("20000.0000")));
        given(otherIncomeRepository.findByTenantIdAndDeclarationId(tenantId, declarationId))
                .willReturn(otherIncome);

        TaxSummaryResponse response = taxSummaryService.summary(employeeId, fy);
        assertThat(response).isNotNull();
        assertThat(response.financialYear()).isEqualTo(fy);
        assertThat(response.taxRegime()).isEqualTo("OLD");
        assertThat(response.status()).isEqualTo(DeclarationStatus.DRAFT);
        assertThat(response.computed()).isNull();

        var declared = response.declared();
        assertThat(declared.houseRentAnnual()).isEqualByComparingTo(new BigDecimal("330000.00"));
        assertThat(declared.homeLoanPrincipal()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(declared.homeLoanInterest()).isEqualByComparingTo(new BigDecimal("200000.00"));
        assertThat(declared.letOutNet()).isEqualByComparingTo(new BigDecimal("-104000.00"));
        assertThat(declared.section6aTotal()).isEqualByComparingTo(new BigDecimal("105000.00"));
        assertThat(declared.section6aByGroup().get("80C_GROUP")).isEqualByComparingTo(new BigDecimal("80000.00"));
        assertThat(declared.section6aByGroup().get("80D")).isEqualByComparingTo(new BigDecimal("25000.00"));
        assertThat(declared.preTaxTotal()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(declared.prevEmploymentIncome()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(declared.prevEmploymentTax()).isEqualByComparingTo(new BigDecimal("45000.00"));
        assertThat(declared.otherIncomeTotal()).isEqualByComparingTo(new BigDecimal("30000.00"));

        // Spec §7: every declared figure is returned at scale 2
        for (BigDecimal figure : List.of(
                declared.houseRentAnnual(),
                declared.homeLoanPrincipal(),
                declared.homeLoanInterest(),
                declared.letOutNet(),
                declared.section6aTotal(),
                declared.section6aByGroup().get("80C_GROUP"),
                declared.preTaxTotal(),
                declared.prevEmploymentIncome(),
                declared.prevEmploymentTax(),
                declared.otherIncomeTotal())) {
            assertThat(figure.scale()).as("scale of " + figure).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("DeclaredTotals returns zeroes when all sections are empty")
    void testDeclaredTotalsEmpty() {
        TaxSummaryResponse response = taxSummaryService.summary(employeeId, fy);
        var declared = response.declared();

        assertThat(declared.houseRentAnnual()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(declared.homeLoanPrincipal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(declared.homeLoanInterest()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(declared.letOutNet()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(declared.section6aTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(declared.section6aByGroup()).isEmpty();
        assertThat(declared.preTaxTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(declared.prevEmploymentIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(declared.prevEmploymentTax()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(declared.otherIncomeTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("record() throws WindowValidationException when figures are null")
    void testRecordNullFigures() {
        assertThatThrownBy(() -> taxSummaryService.record(declarationId, "OLD", null))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("Tax summary figures must not be null");
    }

    @Test
    @DisplayName("record() with null regime defaults to declaration tax regime and creates new summary")
    void testRecordNullRegimeDefaultsToDeclRegime() {
        TaxSummaryFigures figures = new TaxSummaryFigures(
                new BigDecimal("1000000.0000"),
                new BigDecimal("800000.0000"),
                new BigDecimal("75000.0000"),
                BigDecimal.ZERO,
                new BigDecimal("75000.0000"),
                new BigDecimal("75000.0000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                12);

        given(taxSummaryRepository.findByTenantIdAndDeclarationIdAndRegime(tenantId, declarationId, "OLD"))
                .willReturn(Optional.empty());

        taxSummaryService.record(declarationId, null, figures);

        ArgumentCaptor<EmployeeInvTaxSummary> captor = ArgumentCaptor.forClass(EmployeeInvTaxSummary.class);
        verify(taxSummaryRepository).save(captor.capture());
        EmployeeInvTaxSummary saved = captor.getValue();
        assertThat(saved.getRegime()).isEqualTo("OLD");
        assertThat(saved.getTaxableIncome()).isEqualByComparingTo(new BigDecimal("1000000.0000"));
        assertThat(saved.getRemainingMonths()).isEqualTo(12);
    }

    @Test
    @DisplayName("record() overwrites existing summary row for same tenant, declaration, and regime")
    void testRecordOverwritesExistingSummary() {
        TaxSummaryFigures figures = new TaxSummaryFigures(
                new BigDecimal("1200000.0000"),
                new BigDecimal("950000.0000"),
                new BigDecimal("102500.0000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                10);

        EmployeeInvTaxSummary existing = new EmployeeInvTaxSummary(tenantId, declarationId, "OLD");
        existing.setTaxableIncome(new BigDecimal("500000.0000"));
        existing.setRemainingMonths(6);

        given(taxSummaryRepository.findByTenantIdAndDeclarationIdAndRegime(tenantId, declarationId, "OLD"))
                .willReturn(Optional.of(existing));

        taxSummaryService.record(declarationId, "OLD", figures);

        ArgumentCaptor<EmployeeInvTaxSummary> captor = ArgumentCaptor.forClass(EmployeeInvTaxSummary.class);
        verify(taxSummaryRepository).save(captor.capture());
        EmployeeInvTaxSummary saved = captor.getValue();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getTaxableIncome()).isEqualByComparingTo(new BigDecimal("1200000.0000"));
        assertThat(saved.getRemainingMonths()).isEqualTo(10);
    }
}
