package com.infinevo.payroll.taxcalc;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeeDetailService;
import com.infinevo.core.employee.detail.EmployeePersonalResponse;
import com.infinevo.core.employee.detail.EmployeePersonalService;
import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryNotFoundException;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.payroll.statutory.lines.CtcEpfComponent;
import com.infinevo.payroll.statutory.lines.CtcEpfComponentRepository;
import com.infinevo.payroll.statutory.lines.SalaryStatutoryItemResponse;
import com.infinevo.payroll.statutory.lines.StatutoryComponentCode;
import com.infinevo.payroll.statutory.pt.ProfessionalTaxService;
import com.infinevo.payroll.taxcalc.engine.ChapterViaDeductions.DeclaredItem;
import com.infinevo.payroll.taxcalc.engine.HraExemption.HraMonthSalary;
import com.infinevo.payroll.taxcalc.engine.SalaryProjection;
import com.infinevo.payroll.taxcalc.model.MonthProjection;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ProfessionalTaxService professionalTaxService;
    private final WorkLocationService workLocationService;
    private final CtcEpfComponentRepository ctcEpfComponentRepository;

    @Autowired
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
            Section6AItemReader section6AItemReader,
            ObjectProvider<ProfessionalTaxService> professionalTaxServiceProvider,
            ObjectProvider<WorkLocationService> workLocationServiceProvider,
            ObjectProvider<CtcEpfComponentRepository> ctcEpfComponentRepositoryProvider) {
        this(
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
                professionalTaxServiceProvider != null ? professionalTaxServiceProvider.getIfAvailable() : null,
                workLocationServiceProvider != null ? workLocationServiceProvider.getIfAvailable() : null,
                ctcEpfComponentRepositoryProvider != null ? ctcEpfComponentRepositoryProvider.getIfAvailable() : null);
    }

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
            Section6AItemReader section6AItemReader,
            ProfessionalTaxService professionalTaxService,
            WorkLocationService workLocationService,
            CtcEpfComponentRepository ctcEpfComponentRepository) {
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
        this.professionalTaxService = professionalTaxService;
        this.workLocationService = workLocationService;
        this.ctcEpfComponentRepository = ctcEpfComponentRepository;
    }

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
        this(
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
                (ProfessionalTaxService) null,
                null,
                null);
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

        // Preload tenant earnings once to avoid N+1 queries across months and components
        Map<UUID, Earning> earningsById = new HashMap<>();
        List<Earning> tenantEarnings = earningRepository.findAllByTenantIdAndDeletedFalse(tenantId);
        if (tenantEarnings != null) {
            for (Earning earning : tenantEarnings) {
                if (earning != null && earning.getId() != null) {
                    earningsById.put(earning.getId(), earning);
                }
            }
        }

        // Cache salary versions by lookup date so versionInForce is not called twice for the same date. The
        // version each month actually used is carried on its MonthProjection; nothing reads this cache by key.
        // B-1 fix: SalaryNotFoundException (no version on the 1st of a month — e.g. mid-month joiner whose
        // effectiveFrom is after the 1st) is caught here and mapped to null. SalaryProjection.annual() already
        // handles null/cancelled versions as zero-salary months, producing a 200 instead of a 500.
        Map<LocalDate, SalaryVersionResponse> versionCache = new HashMap<>();

        SalaryProjectionResult salaryResult = SalaryProjection.annual(
                fy,
                dateOfJoining,
                date -> versionCache.computeIfAbsent(date, d -> {
                    try {
                        return employeeSalaryService.versionInForce(tenantId, employeeId, d);
                    } catch (SalaryNotFoundException e) {
                        try {
                            LocalDate endOfMonth = YearMonth.from(d).atEndOfMonth();
                            if (!endOfMonth.equals(d)) {
                                return employeeSalaryService.versionInForce(tenantId, employeeId, endOfMonth);
                            }
                        } catch (SalaryNotFoundException ignored) {
                        }
                        return null;
                    }
                }),
                componentId -> resolveEarning(tenantId, componentId, earningsById)
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

        // Resolve employee work location stateCode once for Professional Tax resolution
        String stateCode = resolveEmployeeStateCode(employee);

        // Reuse projected months to derive HRA monthly Basic/HRA, structure EPF, and structure PT
        List<HraMonthSalary> hraSalaries = new ArrayList<>();
        boolean hasStructureEpf = false;
        Money epfTotal = Money.ZERO;
        boolean hasStructurePt = false;
        Money ptTotal = Money.ZERO;
        Map<UUID, Optional<Money>> epfMonthlyByVersionId = new HashMap<>();

        for (MonthProjection mp : salaryResult.months()) {
            YearMonth currentYm = mp.month();
            // Use exactly the version the projection used for this month. Re-deriving a lookup key here
            // (e.g. the 1st of the month) misses a mid-month joiner's version, resolved as of dateOfJoining,
            // and silently drops that month's Basic/HRA, EPF and PT while its salary is still counted.
            SalaryVersionResponse version = mp.version();
            Money basic = Money.ZERO;
            Money hra = Money.ZERO;

            if (version != null && !version.cancelled()) {
                if (version.earnings() != null) {
                    for (var item : version.earnings()) {
                        if (item.enabled()) {
                            // B-2 fix: resolve the Earning to check isTaxable before accumulating Basic or HRA.
                            // SalaryProjection.annual() only sums taxable earnings into the salary projection;
                            // the HRA exemption must be computed on the same taxable Basic/HRA slice, otherwise
                            // a non-taxable HRA is deducted from a salary figure that never included it.
                            Optional<Earning> earning = resolveEarning(tenantId, item.componentId(), earningsById);
                            boolean taxable = earning.map(Earning::isTaxable).orElse(false);
                            String code = item.componentCode();
                            String earningType =
                                    earning.map(Earning::getEarningType).orElse("");
                            BigDecimal amt = item.monthlyAmount();
                            if (amt != null && taxable) {
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

                Optional<Money> monthlyEpf = resolveMonthlyEpf(tenantId, version, epfMonthlyByVersionId);
                if (monthlyEpf.isPresent()) {
                    hasStructureEpf = true;
                    epfTotal = epfTotal.add(monthlyEpf.get());
                }

                if (professionalTaxService != null && stateCode != null && !stateCode.isBlank()) {
                    Money monthlyPt = professionalTaxService.resolve(
                            tenantId, stateCode, mp.taxableSalary(), employee.gender(), currentYm.atEndOfMonth());
                    if (monthlyPt != null) {
                        hasStructurePt = true;
                        ptTotal = ptTotal.add(monthlyPt);
                    }
                }
            }

            hraSalaries.add(new HraMonthSalary(currentYm, basic, hra));
        }

        Optional<Money> structureEpf = hasStructureEpf ? Optional.of(epfTotal) : Optional.empty();
        Optional<Money> structurePt = hasStructurePt ? Optional.of(ptTotal) : Optional.empty();

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
                structureEpf,
                structurePt,
                hraSalaries);
    }

    private Optional<Earning> resolveEarning(UUID tenantId, UUID componentId, Map<UUID, Earning> earningsById) {
        if (componentId == null) {
            return Optional.empty();
        }
        Earning cached = earningsById.get(componentId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<Earning> found = earningRepository.findByIdAndTenantIdAndDeletedFalse(componentId, tenantId);
        found.ifPresent(e -> earningsById.put(componentId, e));
        return found;
    }

    private String resolveEmployeeStateCode(EmployeeResponse employee) {
        if (workLocationService == null || employee == null || employee.workLocationId() == null) {
            return null;
        }
        List<WorkLocationResponse> locations = workLocationService.list(false);
        if (locations == null) {
            return null;
        }
        for (WorkLocationResponse loc : locations) {
            if (loc != null && employee.workLocationId().equals(loc.id())) {
                return loc.stateCode();
            }
        }
        return null;
    }

    private Optional<Money> resolveMonthlyEpf(
            UUID tenantId, SalaryVersionResponse version, Map<UUID, Optional<Money>> cache) {
        if (version.id() != null && cache.containsKey(version.id())) {
            return cache.get(version.id());
        }

        Optional<Money> result = Optional.empty();
        if (version.statutory() != null && !version.statutory().isEmpty()) {
            Money sum = Money.ZERO;
            boolean found = false;
            for (SalaryStatutoryItemResponse item : version.statutory()) {
                if (item != null
                        && StatutoryComponentCode.EPF_EMPLOYEE.name().equalsIgnoreCase(item.componentCode())
                        && item.monthlyAmount() != null) {
                    sum = sum.add(Money.of(item.monthlyAmount()));
                    found = true;
                }
            }
            if (found) {
                result = Optional.of(sum);
            }
        } else if (ctcEpfComponentRepository != null && version.id() != null) {
            List<CtcEpfComponent> epfComponents =
                    ctcEpfComponentRepository.findByTenantIdAndCtcStructureId(tenantId, version.id());
            if (epfComponents != null) {
                Money sum = Money.ZERO;
                boolean found = false;
                for (CtcEpfComponent epf : epfComponents) {
                    if (epf != null
                            && epf.getComponentCode() == StatutoryComponentCode.EPF_EMPLOYEE
                            && epf.getMonthlyAmount() != null) {
                        sum = sum.add(Money.of(epf.getMonthlyAmount()));
                        found = true;
                    }
                }
                if (found) {
                    result = Optional.of(sum);
                }
            }
        }

        if (version.id() != null) {
            cache.put(version.id(), result);
        }
        return result;
    }
}
