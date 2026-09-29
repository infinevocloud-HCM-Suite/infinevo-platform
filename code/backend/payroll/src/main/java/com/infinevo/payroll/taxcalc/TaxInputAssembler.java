package com.infinevo.payroll.taxcalc;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeeDetailService;
import com.infinevo.core.employee.detail.EmployeePersonalResponse;
import com.infinevo.core.employee.detail.EmployeePersonalService;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.taxcalc.engine.ChapterViaDeductions.DeclaredItem;
import com.infinevo.payroll.taxcalc.engine.HraExemption.HraMonthSalary;
import com.infinevo.payroll.taxcalc.engine.SalaryProjection;
import com.infinevo.payroll.taxcalc.model.SalaryProjectionResult;
import com.infinevo.payroll.taxcalc.model.TaxInput;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPreTaxDeduction;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPreTaxDeductionRepository;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPrevEmployment;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvPrevEmploymentRepository;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvSection6A;
import com.infinevo.payroll.taxdeclaration.deductions.EmployeeInvSection6ARepository;
import com.infinevo.payroll.taxdeclaration.deductions.PreTaxDeductionKind;
import com.infinevo.payroll.taxdeclaration.deductions.PrevEmploymentKind;
import com.infinevo.payroll.taxdeclaration.deductions.Section6AItemReader;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoan;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoanRepository;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRent;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRentRepository;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutProperty;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutPropertyRepository;
import com.infinevo.payroll.taxdeclaration.summary.EmployeeInvOtherIncome;
import com.infinevo.payroll.taxdeclaration.summary.EmployeeInvOtherIncomeRepository;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Gathers and aggregates all input needed for tax calculation across employee, salary, and
 * declaration tables (W-33.1 spec § 3, § 4, W-33.2 spec § 3).
 */
@Component
public class TaxInputAssembler {

    private final TaxDeclarationService declarationService;
    private final EmployeeService employeeService;
    private final EmployeePersonalService employeePersonalService;
    private final EmployeeSalaryService employeeSalaryService;
    private final EarningRepository earningRepository;
    private final EmployeeInvPrevEmploymentRepository prevEmploymentRepository;
    private final EmployeeInvHouseRentRepository houseRentRepository;
    private final EmployeeInvHomeLoanRepository homeLoanRepository;
    private final EmployeeInvLetOutPropertyRepository letOutPropertyRepository;
    private final EmployeeInvSection6ARepository section6ARepository;
    private final EmployeeInvPreTaxDeductionRepository preTaxDeductionRepository;
    private final EmployeeInvOtherIncomeRepository otherIncomeRepository;
    private final Section6AItemReader section6AItemReader;

    public TaxInputAssembler(
            TaxDeclarationService declarationService,
            EmployeeService employeeService,
            EmployeePersonalService employeePersonalService,
            EmployeeSalaryService employeeSalaryService,
            EarningRepository earningRepository,
            EmployeeInvPrevEmploymentRepository prevEmploymentRepository,
            EmployeeInvHouseRentRepository houseRentRepository,
            EmployeeInvHomeLoanRepository homeLoanRepository,
            EmployeeInvLetOutPropertyRepository letOutPropertyRepository,
            EmployeeInvSection6ARepository section6ARepository,
            EmployeeInvPreTaxDeductionRepository preTaxDeductionRepository,
            EmployeeInvOtherIncomeRepository otherIncomeRepository,
            Section6AItemReader section6AItemReader) {
        this.declarationService = Objects.requireNonNull(declarationService, "declarationService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.employeePersonalService =
                Objects.requireNonNull(employeePersonalService, "employeePersonalService must not be null");
        this.employeeSalaryService =
                Objects.requireNonNull(employeeSalaryService, "employeeSalaryService must not be null");
        this.earningRepository = Objects.requireNonNull(earningRepository, "earningRepository must not be null");
        this.prevEmploymentRepository =
                Objects.requireNonNull(prevEmploymentRepository, "prevEmploymentRepository must not be null");
        this.houseRentRepository = Objects.requireNonNull(houseRentRepository, "houseRentRepository must not be null");
        this.homeLoanRepository = Objects.requireNonNull(homeLoanRepository, "homeLoanRepository must not be null");
        this.letOutPropertyRepository =
                Objects.requireNonNull(letOutPropertyRepository, "letOutPropertyRepository must not be null");
        this.section6ARepository = Objects.requireNonNull(section6ARepository, "section6ARepository must not be null");
        this.preTaxDeductionRepository =
                Objects.requireNonNull(preTaxDeductionRepository, "preTaxDeductionRepository must not be null");
        this.otherIncomeRepository =
                Objects.requireNonNull(otherIncomeRepository, "otherIncomeRepository must not be null");
        this.section6AItemReader = Objects.requireNonNull(section6AItemReader, "section6AItemReader must not be null");
    }

    /**
     * Gathers inputs for the given employee and financial year in the given tenant.
     *
     * @param tenantId tenant identifier
     * @param employeeId employee identifier
     * @param fy the financial year
     * @return assembled {@link TaxInput}
     */
    public TaxInput assemble(UUID tenantId, UUID employeeId, FinancialYear fy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(fy, "fy must not be null");

        EmployeeInvestmentDeclaration declaration = declarationService.require(employeeId, fy.label());
        UUID declarationId = declaration.getId();
        EmployeeResponse employee = employeeService.get(employeeId);

        LocalDate dateOfJoining = employee.dateOfJoining();
        LocalDate dateOfBirth = null;
        try {
            EmployeePersonalResponse personal = employeePersonalService.get(employeeId);
            if (personal != null) {
                dateOfBirth = personal.dateOfBirth();
            }
        } catch (EmployeeDetailService.NotFoundException e) {
            // General category when personal section not written
        }

        AgeCategory ageCategory = AgeCategory.at(dateOfBirth, fy.end());

        SalaryProjectionResult salaryResult = SalaryProjection.annual(
                fy,
                dateOfJoining,
                date -> employeeSalaryService.versionInForce(tenantId, employeeId, date),
                componentId -> earningRepository
                        .findByIdAndTenantIdAndDeletedFalse(componentId, tenantId)
                        .map(Earning::isTaxable)
                        .orElse(false));

        List<EmployeeInvPrevEmployment> prevEmploymentRows =
                prevEmploymentRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);

        Map<PrevEmploymentKind, Money> prevEmploymentMap = new EnumMap<>(PrevEmploymentKind.class);
        for (EmployeeInvPrevEmployment row : prevEmploymentRows) {
            Money current = prevEmploymentMap.getOrDefault(row.getKind(), Money.ZERO);
            prevEmploymentMap.put(row.getKind(), current.add(Money.of(row.getAmount())));
        }

        // Housing records
        List<EmployeeInvHouseRent> houseRentRows =
                houseRentRepository.findByTenantIdAndDeclarationIdOrderByFromMonthAsc(tenantId, declarationId);
        List<EmployeeInvHomeLoan> homeLoanRows =
                homeLoanRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);
        List<EmployeeInvLetOutProperty> letOutRows =
                letOutPropertyRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);

        // Section 6A items
        List<EmployeeInvSection6A> sec6aRows =
                section6ARepository.findByTenantIdAndDeclarationId(tenantId, declarationId);
        List<DeclaredItem> declaredItems = new ArrayList<>();
        for (EmployeeInvSection6A row : sec6aRows) {
            var item = section6AItemReader.require(row.getSection6aItemId());
            Money maxLimit = item.maxLimit() != null ? Money.of(item.maxLimit()) : null;
            declaredItems.add(new DeclaredItem(
                    item.sectionCode(), item.name(), item.categoryGroupCode(), Money.of(row.getAmount()), maxLimit));
        }

        // Pre-tax deductions
        List<EmployeeInvPreTaxDeduction> preTaxRows =
                preTaxDeductionRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);
        Map<PreTaxDeductionKind, Money> preTaxMap = new EnumMap<>(PreTaxDeductionKind.class);
        for (EmployeeInvPreTaxDeduction row : preTaxRows) {
            Money current = preTaxMap.getOrDefault(row.getKind(), Money.ZERO);
            preTaxMap.put(row.getKind(), current.add(Money.of(row.getAmount())));
        }

        // Other income
        List<EmployeeInvOtherIncome> otherIncomeRows =
                otherIncomeRepository.findByTenantIdAndDeclarationId(tenantId, declarationId);

        // Project monthly basic and HRA earnings for Section 10(13A)
        List<HraMonthSalary> hraSalaries = new ArrayList<>();
        LocalDate startDate = fy.start();
        if (dateOfJoining != null && dateOfJoining.isAfter(fy.start())) {
            startDate = dateOfJoining;
        }
        if (dateOfJoining == null || !dateOfJoining.isAfter(fy.end())) {
            java.time.YearMonth currentYm = java.time.YearMonth.from(startDate);
            java.time.YearMonth endYm = java.time.YearMonth.from(fy.end());
            while (!currentYm.isAfter(endYm)) {
                var version = employeeSalaryService.versionInForce(tenantId, employeeId, currentYm.atDay(1));
                Money basic = Money.ZERO;
                Money hra = Money.ZERO;
                if (version != null && !version.cancelled() && version.earnings() != null) {
                    for (var item : version.earnings()) {
                        if (item.enabled()) {
                            String code = item.componentCode();
                            var earningOpt =
                                    earningRepository.findByIdAndTenantIdAndDeletedFalse(item.componentId(), tenantId);
                            String earningType =
                                    earningOpt.map(Earning::getEarningType).orElse("");
                            BigDecimal amt = item.monthlyAmount();
                            if (amt != null) {
                                if ("BASIC".equalsIgnoreCase(code) || "BASIC".equalsIgnoreCase(earningType)) {
                                    basic = basic.add(Money.of(amt));
                                }
                                if ("HRA".equalsIgnoreCase(code) || "HRA".equalsIgnoreCase(earningType)) {
                                    hra = hra.add(Money.of(amt));
                                }
                            }
                        }
                    }
                }
                hraSalaries.add(new HraMonthSalary(currentYm, basic, hra));
                currentYm = currentYm.plusMonths(1);
            }
        }

        return new TaxInput(
                employeeId,
                declarationId,
                salaryResult,
                prevEmploymentMap,
                ageCategory,
                declaration.isStayingInRentedHouse(),
                declaration.isRepayingSelfOccupiedLoan(),
                declaration.hasLetOutProperty(),
                houseRentRows,
                homeLoanRows,
                letOutRows,
                declaredItems,
                preTaxMap,
                otherIncomeRows,
                Optional.empty(),
                Optional.empty(),
                hraSalaries);
    }
}
