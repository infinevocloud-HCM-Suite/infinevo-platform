package com.infinevo.payroll.taxdeclaration.summary;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPreTaxDeduction;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPreTaxDeductionRepository;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPrevEmployment;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPrevEmploymentRepository;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvSection6A;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvSection6ARepository;
import com.infinevo.payroll.taxdeclaration.deductions.PrevEmploymentKind;
import com.infinevo.payroll.taxdeclaration.deductions.Section6AItemReader;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoan;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoanRepository;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRent;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRentRepository;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutProperty;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutPropertyRepository;
import com.infinevo.payroll.taxdeclaration.housing.HousingRules;
import com.infinevo.payroll.taxdeclaration.summary.dto.ComputedTaxSummaryResponse;
import com.infinevo.payroll.taxdeclaration.summary.dto.DeclaredTotals;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryFigures;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryResponse;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TaxSummaryService} (W-32.4).
 */
@Service
@Transactional
public class TaxSummaryServiceImpl implements TaxSummaryService {

    private final TaxDeclarationService taxDeclarationService;
    private final EmployeeInvTaxSummaryRepository taxSummaryRepository;
    private final EmployeeInvOtherIncomeRepository otherIncomeRepository;
    private final EmployeeInvHouseRentRepository houseRentRepository;
    private final EmployeeInvHomeLoanRepository homeLoanRepository;
    private final EmployeeInvLetOutPropertyRepository letOutPropertyRepository;
    private final EmployeeInvSection6ARepository section6ARepository;
    private final EmployeeInvPreTaxDeductionRepository preTaxDeductionRepository;
    private final EmployeeInvPrevEmploymentRepository prevEmploymentRepository;
    private final Section6AItemReader section6AItemReader;
    private final EmployeeService employeeService;

    public TaxSummaryServiceImpl(
            TaxDeclarationService taxDeclarationService,
            EmployeeInvTaxSummaryRepository taxSummaryRepository,
            EmployeeInvOtherIncomeRepository otherIncomeRepository,
            EmployeeInvHouseRentRepository houseRentRepository,
            EmployeeInvHomeLoanRepository homeLoanRepository,
            EmployeeInvLetOutPropertyRepository letOutPropertyRepository,
            EmployeeInvSection6ARepository section6ARepository,
            EmployeeInvPreTaxDeductionRepository preTaxDeductionRepository,
            EmployeeInvPrevEmploymentRepository prevEmploymentRepository,
            Section6AItemReader section6AItemReader,
            EmployeeService employeeService) {
        this.taxDeclarationService =
                Objects.requireNonNull(taxDeclarationService, "taxDeclarationService must not be null");
        this.taxSummaryRepository =
                Objects.requireNonNull(taxSummaryRepository, "taxSummaryRepository must not be null");
        this.otherIncomeRepository =
                Objects.requireNonNull(otherIncomeRepository, "otherIncomeRepository must not be null");
        this.houseRentRepository = Objects.requireNonNull(houseRentRepository, "houseRentRepository must not be null");
        this.homeLoanRepository = Objects.requireNonNull(homeLoanRepository, "homeLoanRepository must not be null");
        this.letOutPropertyRepository =
                Objects.requireNonNull(letOutPropertyRepository, "letOutPropertyRepository must not be null");
        this.section6ARepository = Objects.requireNonNull(section6ARepository, "section6ARepository must not be null");
        this.preTaxDeductionRepository =
                Objects.requireNonNull(preTaxDeductionRepository, "preTaxDeductionRepository must not be null");
        this.prevEmploymentRepository =
                Objects.requireNonNull(prevEmploymentRepository, "prevEmploymentRepository must not be null");
        this.section6AItemReader = Objects.requireNonNull(section6AItemReader, "section6AItemReader must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @Override
    @Transactional
    public TaxSummaryResponse summaryOwn(String financialYear) {
        EmployeeResponse current = currentEmployeeOrDeny("payroll.tax_declaration.read_own");
        return summary(current.id(), financialYear);
    }

    @Override
    @Transactional
    public TaxSummaryResponse summary(UUID employeeId, String financialYear) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(employeeId, fy.label());

        DeclaredTotals declaredTotals = computeDeclaredTotals(tenantId, decl.getId());

        taxSummaryRepository.insertIgnoreConflict(tenantId, decl.getId(), decl.getTaxRegime());
        EmployeeInvTaxSummary summary = taxSummaryRepository
                .findByTenantIdAndDeclarationIdAndRegime(tenantId, decl.getId(), decl.getTaxRegime())
                .orElseThrow(() -> new IllegalStateException("Failed to find or create tax summary record"));

        ComputedTaxSummaryResponse computed = toComputedResponse(summary);
        return new TaxSummaryResponse(
                decl.getFinancialYear(), decl.getTaxRegime(), decl.getStatus(), declaredTotals, computed);
    }

    @Override
    public void record(UUID declarationId, String regime, TaxSummaryFigures figures) {
        if (figures == null) {
            throw new WindowValidationException("Tax summary figures must not be null");
        }
        UUID tenantId = TenantContext.require();
        EmployeeInvestmentDeclaration decl = taxDeclarationService.require(declarationId);

        if (regime != null && !regime.equalsIgnoreCase("OLD") && !regime.equalsIgnoreCase("NEW")) {
            throw new WindowValidationException("Tax regime must be OLD or NEW");
        }
        String targetRegime = regime != null ? regime.toUpperCase() : decl.getTaxRegime();

        EmployeeInvTaxSummary summary = taxSummaryRepository
                .findByTenantIdAndDeclarationIdAndRegime(tenantId, declarationId, targetRegime)
                .orElseGet(() -> new EmployeeInvTaxSummary(tenantId, declarationId, targetRegime));

        summary.setTaxableIncome(figures.taxableIncome());
        summary.setNetTaxableIncome(figures.netTaxableIncome());
        summary.setTaxOnTaxableIncome(figures.taxOnTaxableIncome());
        summary.setTaxYtdAmount(figures.taxYtdAmount());
        summary.setTaxToBePaid(figures.taxToBePaid());
        summary.setTdsThroughPayroll(figures.tdsThroughPayroll());
        summary.setTdsPreviousEmployer(figures.tdsPreviousEmployer());
        summary.setTdsOtherIncome(figures.tdsOtherIncome());
        summary.setOtherSourcesIncome(figures.otherSourcesIncome());
        summary.setExemptionUnderSection10(figures.exemptionUnderSection10());
        summary.setExemptionUnderSection6a(figures.exemptionUnderSection6a());
        summary.setRemainingMonths(figures.remainingMonths());
        summary.setComputedAt(Instant.now());

        taxSummaryRepository.save(summary);
    }

    private DeclaredTotals computeDeclaredTotals(UUID tenantId, UUID declarationId) {
        // House rent
        List<EmployeeInvHouseRent> houseRents =
                houseRentRepository.findByTenantIdAndDeclarationIdOrderByFromMonthAsc(tenantId, declarationId);
        Money rentTotal = Money.ZERO;
        for (EmployeeInvHouseRent rent : houseRents) {
            long months = HousingRules.calculateMonthsInclusive(rent.getFromMonth(), rent.getToMonth());
            Money monthly = Money.of(rent.getAmountPerMonth());
            rentTotal = rentTotal.add(monthly.multiply(BigDecimal.valueOf(months)));
        }

        // Home loan
        List<EmployeeInvHomeLoan> homeLoans =
                homeLoanRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);
        Money loanPrincipal = Money.ZERO;
        Money loanInterest = Money.ZERO;
        for (EmployeeInvHomeLoan loan : homeLoans) {
            loanPrincipal = loanPrincipal.add(Money.of(loan.getPrincipalPaid()));
            loanInterest = loanInterest.add(Money.of(loan.getInterestPaid()));
        }

        // Let-out property
        List<EmployeeInvLetOutProperty> letOutProps =
                letOutPropertyRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);
        Money letOutTotal = Money.ZERO;
        for (EmployeeInvLetOutProperty prop : letOutProps) {
            letOutTotal = letOutTotal.add(Money.of(prop.getNetIncomeLoss()));
        }

        // Section 6A
        List<EmployeeInvSection6A> section6aLines =
                section6ARepository.findByTenantIdAndDeclarationId(tenantId, declarationId);
        Map<String, Money> groupMoneyMap = new LinkedHashMap<>();
        Money s6aTotal = Money.ZERO;
        for (EmployeeInvSection6A s6a : section6aLines) {
            Money amt = Money.of(s6a.getAmount());
            s6aTotal = s6aTotal.add(amt);
            var itemOpt = section6AItemReader.findById(s6a.getSection6aItemId());
            String key = itemOpt.map(i -> (i.categoryGroupCode() != null
                                    && !i.categoryGroupCode().isBlank())
                            ? i.categoryGroupCode()
                            : i.sectionCode())
                    .orElse("UNKNOWN");
            groupMoneyMap.put(key, groupMoneyMap.getOrDefault(key, Money.ZERO).add(amt));
        }
        Map<String, BigDecimal> section6aByGroup = new LinkedHashMap<>();
        groupMoneyMap.forEach((k, v) -> section6aByGroup.put(k, v.toAmount()));

        // Pre-tax deductions
        List<EmployeeInvPreTaxDeduction> preTaxLines =
                preTaxDeductionRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);
        Money preTaxTotal = Money.ZERO;
        for (EmployeeInvPreTaxDeduction preTax : preTaxLines) {
            preTaxTotal = preTaxTotal.add(Money.of(preTax.getAmount()));
        }

        // Previous employment
        List<EmployeeInvPrevEmployment> prevEmpLines =
                prevEmploymentRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);
        Money prevIncome = Money.ZERO;
        Money prevTax = Money.ZERO;
        for (EmployeeInvPrevEmployment prev : prevEmpLines) {
            if (prev.getKind() == PrevEmploymentKind.INCOME) {
                prevIncome = prevIncome.add(Money.of(prev.getAmount()));
            } else if (prev.getKind() == PrevEmploymentKind.INCOME_TAX_DEDUCTED) {
                prevTax = prevTax.add(Money.of(prev.getAmount()));
            }
        }

        // Other income
        List<EmployeeInvOtherIncome> otherIncomeLines =
                otherIncomeRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);
        Money otherIncomeTotal = Money.ZERO;
        for (EmployeeInvOtherIncome other : otherIncomeLines) {
            otherIncomeTotal = otherIncomeTotal.add(Money.of(other.getAmount()));
        }

        return new DeclaredTotals(
                rentTotal.toAmount(),
                loanPrincipal.toAmount(),
                loanInterest.toAmount(),
                letOutTotal.toAmount(),
                section6aByGroup,
                s6aTotal.toAmount(),
                preTaxTotal.toAmount(),
                prevIncome.toAmount(),
                prevTax.toAmount(),
                otherIncomeTotal.toAmount());
    }

    private static ComputedTaxSummaryResponse toComputedResponse(EmployeeInvTaxSummary summary) {
        if (summary == null || summary.getComputedAt() == null) {
            return null;
        }
        return new ComputedTaxSummaryResponse(
                scale2(summary.getTaxableIncome()),
                scale2(summary.getNetTaxableIncome()),
                scale2(summary.getTaxOnTaxableIncome()),
                scale2(summary.getTaxYtdAmount()),
                scale2(summary.getTaxToBePaid()),
                scale2(summary.getTdsThroughPayroll()),
                scale2(summary.getTdsPreviousEmployer()),
                scale2(summary.getTdsOtherIncome()),
                scale2(summary.getOtherSourcesIncome()),
                scale2(summary.getExemptionUnderSection10()),
                scale2(summary.getExemptionUnderSection6a()),
                summary.getRemainingMonths(),
                summary.getComputedAt());
    }

    private static BigDecimal scale2(BigDecimal val) {
        return val != null ? val.setScale(2, RoundingMode.HALF_UP) : null;
    }

    private EmployeeResponse currentEmployeeOrDeny(String actionCode) {
        return employeeService.currentEmployee().orElseThrow(() -> new PermissionDeniedException(actionCode));
    }
}
