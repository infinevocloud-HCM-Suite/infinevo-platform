package com.itsdev.payroll.serviceimpl.employeeitdeclaration.taxCalculator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.NewTaxCalculationResult;
import com.itsdev.payroll.entity.EmployeeITDeclaration.*;
import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeeProofOfInvestment;
import com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.*;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.employee.CtcStructure;
import com.itsdev.payroll.entity.employee.EmployeeEarning;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employee.CtcStructureRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.EmployeeInvestmentDeclarationRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.NewTaxCalculationRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.taxcalculator.*;
import com.itsdev.payroll.service.employeeitdeclaration.taxCalculator.NewTaxCalculationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class NewTaxCalculationServiceImpl implements NewTaxCalculationService {

    private static final Logger log =
            LoggerFactory.getLogger(NewTaxCalculationServiceImpl.class);

    private final BasicDetailsRepository basicDetailsRepository;
    private final EmployeeInvestmentDeclarationRepository declarationRepo;
    private final CtcStructureRepository ctcStructureRepository;
    private final TaxSlabMasterRepository taxSlabRepo;
    private final Section87aRebateRuleMasterRepository rebateRuleRepo;
    private final CessSurchargeRuleMasterRepository cessSurchargeRepo;
    private final StandardDeductionRuleMasterRepository standardDeductionRepo;
    private final NewTaxCalculationRepository newTaxCalculationRepository;

    public NewTaxCalculationServiceImpl(
            BasicDetailsRepository basicDetailsRepository,
            EmployeeInvestmentDeclarationRepository declarationRepo,
            CtcStructureRepository ctcStructureRepository,
            TaxSlabMasterRepository taxSlabRepo,
            Section87aRebateRuleMasterRepository rebateRuleRepo,
            CessSurchargeRuleMasterRepository cessSurchargeRepo,
            StandardDeductionRuleMasterRepository standardDeductionRepo,
            NewTaxCalculationRepository newTaxCalculationRepository
    ) {
    	
        this.basicDetailsRepository = basicDetailsRepository;
        this.declarationRepo = declarationRepo;
        this.ctcStructureRepository = ctcStructureRepository;
        this.taxSlabRepo = taxSlabRepo;
        this.rebateRuleRepo = rebateRuleRepo;
        this.cessSurchargeRepo = cessSurchargeRepo;
        this.standardDeductionRepo = standardDeductionRepo;
        this.newTaxCalculationRepository = newTaxCalculationRepository;
    }

    @Override
    public NewTaxCalculationResult calculateNewTax(
            String organizationId,
            String employeeId,
            Integer financialYear
    ) {

        log.info("▶ NEW TAX calculation started | empId={} | FY={}", employeeId, financialYear);

        /* =====================================================
         * EMPLOYEE + ORGANIZATION
         * ===================================================== */
        BasicDetails employee =
                basicDetailsRepository
                        .findByEmployeeIdAndOrganization_OrganizationIdAndIsDeletedFalse(
                                employeeId,
                                organizationId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Employee not found for orgId=" + organizationId +
                                        ", employeeId=" + employeeId));

        Organization organization = employee.getOrganization();

        log.info("✅ Employee & Organization fetched | empId={} | orgId={}",
                employeeId, organizationId);

        /* =====================================================
         * IT DECLARATION
         * ===================================================== */
        EmployeeInvestmentDeclaration declaration =
                declarationRepo
                        .findByOrganizationAndEmployeeAndFiscalYear(
                                organization, employee, financialYear
                        )
                        .orElse(null);

        // if (declaration == null) {
        //     log.info(
        //             "ℹ️ IT Declaration not found | empId={} | FY={} | Proceeding with NEW tax calculation",
        //             employeeId,
        //             financialYear
        //     );
        // }
        
        // declaration.setTaxRegime("NEW");
        // declaration.setTaxRegimeFormatted("New Tax Regime");
        // declarationRepo.save(declaration);

        /* =====================================================
        * FULL FY GROSS SALARY (OLD + REVISED CTC)
        * ===================================================== */

        LocalDate joiningDate = getEmployeeJoiningDate(employee);

        BigDecimal grossSalary =
                calculateTotalFySalary(
                        organizationId,
                        employeeId,
                        financialYear,
                        joiningDate
                );

        log.info("💰 Total FY Salary (old + revised CTC) = {}", grossSalary);


        /* =====================================================
         * STANDARD DEDUCTION (NEW REGIME)
         * ===================================================== */

        // Canonical Financial Year format: YYYY-YY (example: 2025 -> 2024-25)
        String fy = (financialYear - 1) + "-" + String.valueOf(financialYear).substring(2);

        BigDecimal standardDeduction =
                standardDeductionRepo
                        .findByFinancialYearAndTaxRegimeAndIsActiveTrue(fy, "NEW")
                        .map(StandardDeductionRuleMaster::getAmount)
                        .orElse(BigDecimal.ZERO);

        log.info(
                "Standard Deduction fetched | FY={} | Regime=NEW | Amount={}",
                fy,
                standardDeduction
        );

        /* =====================================================
        * PREVIOUS EMPLOYMENT COMPUTATION (Section 192)
        * ===================================================== */
        PrevEmploymentComputation prev = computePreviousEmployment(declaration);

        log.info("💼 Previous Employer Net Income = {}", prev.netIncome);
        log.info("💼 Previous Employer TDS Paid = {}", prev.tdsPaid);

        /* =====================================================
        * INCOME FROM SALARY (CURRENT + PREVIOUS)
        * ===================================================== */
        BigDecimal incomeFromSalary = grossSalary.add(prev.netIncome);

        /* =====================================================
        * GROSS TOTAL INCOME (NEW REGIME)
        * ===================================================== */
        BigDecimal grossTotalIncome = incomeFromSalary;

        log.info("📊 Income From Salary (after prev employment) = {}", incomeFromSalary);
        log.info("📊 Gross Total Income (NEW regime) = {}", grossTotalIncome);


        /* =====================================================
        * TAXABLE INCOME (AFTER STANDARD DEDUCTION)
        * ===================================================== */
        BigDecimal taxableIncome = grossTotalIncome.subtract(standardDeduction);

        if (taxableIncome.compareTo(BigDecimal.ZERO) < 0) {
        taxableIncome = BigDecimal.ZERO;
        }

        log.info("🧾 Taxable Income (after standard deduction) = {}", taxableIncome);


        /* =====================================================
        * TAX SLAB CALCULATION (NEW REGIME)
        * ===================================================== */

        /*
        * 1️⃣ Fetch tax slab master for the given FY & Regime
        */
        List<TaxSlabMaster> slabMasters =
                taxSlabRepo.findByFinancialYearAndTaxRegimeAndIsActiveTrue(fy, "NEW");

        /*
        * Safety check: No slab configured
        */
        if (slabMasters.isEmpty()) {
            throw new RuntimeException(
                    "No tax slab configured for FY=" + fy + ", Regime=NEW"
            );
        }

        /*
        * Safety check: More than one active slab for same FY & regime
        */
        if (slabMasters.size() > 1) {
            throw new RuntimeException(
                    "Multiple active tax slabs found for FY=" + fy + ", Regime=NEW"
            );
        }

        /*
        * Exactly one slab master is expected
        */
        TaxSlabMaster slabMaster = slabMasters.get(0);

        log.info(
                "📊 Tax slab loaded | FY={} | Regime=NEW | slabId={}",
                fy,
                slabMaster.getId()
        );

        /*
        * 2️⃣ Parse slab JSON into objects
        */
        ObjectMapper mapper = new ObjectMapper();
        List<TaxSlab> slabs;

        try {
            slabs = mapper.readValue(
                    slabMaster.getSlabJson(),
                    mapper.getTypeFactory()
                            .constructCollectionType(List.class, TaxSlab.class)
            );
        } catch (Exception e) {
            throw new RuntimeException("Slab JSON parse failed", e);
        }

        /*
        * 3️⃣ CRITICAL FIX #1 — SORT SLABS BY LOWER BOUND
        * - Tax calculation MUST always proceed lowest → highest
        */
        slabs.sort((a, b) -> a.getFrom().compareTo(b.getFrom()));

        BigDecimal taxBeforeRebate = BigDecimal.ZERO;
        BigDecimal remainingIncome = taxableIncome;

        /*
        * 4️⃣ Iterate slab-by-slab using RELATIVE income consumption
        */
        for (TaxSlab slab : slabs) {
            // Stop once all income is consumed
            if (remainingIncome.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
            }

            BigDecimal slabWidth =
                    slab.getTo() != null
                            ? slab.getTo().subtract(slab.getFrom())
                            : remainingIncome;

            if (slabWidth.compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // Defensive: skip invalid slab
            }

            BigDecimal taxableInSlab =
                    remainingIncome.min(slabWidth);

            BigDecimal slabTax =
                    taxableInSlab
                            .multiply(slab.getRate())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            taxBeforeRebate = taxBeforeRebate.add(slabTax);
            remainingIncome = remainingIncome.subtract(taxableInSlab);

            log.info(
                    "🧮 Slab {}–{} @ {}% | taxable={} | tax={}",
                    slab.getFrom(),
                    slab.getTo() != null ? slab.getTo() : "∞",
                    slab.getRate(),
                    taxableInSlab,
                    slabTax
            );
        }

        log.info("✅ Tax Before Rebate = {}", taxBeforeRebate);

        /* =====================================================
         * SECTION 87A REBATE
         * ===================================================== */
        BigDecimal rebate = BigDecimal.ZERO;

        Section87ARebateRuleMaster rebateRule =
                rebateRuleRepo.findByTaxRegimeAndIsActiveTrue("NEW").orElse(null);

        if (rebateRule != null &&
                taxableIncome.compareTo(rebateRule.getIncomeThreshold()) <= 0) {

                if (Boolean.TRUE.equals(rebateRule.getFullRebate())) {
                // ✅ FULL REBATE (NEW regime ≤ ₹12L)
                rebate = taxBeforeRebate;
            } else {
                // ✅ PARTIAL REBATE
                rebate = taxBeforeRebate.min(rebateRule.getMaxRebateAmount());
            }
        }

        BigDecimal taxAfterRebate =
                taxBeforeRebate.subtract(rebate);

        log.info("🎁 Rebate Applied = {}", rebate);
        log.info("✅ Tax After Rebate = {}", taxAfterRebate);

        /* =====================================================
         * SURCHARGE + CESS
         * ===================================================== */
        BigDecimal surcharge = BigDecimal.ZERO;
        BigDecimal cess = BigDecimal.ZERO;

        /* ================= SURCHARGE ================= */
        for (CessSurchargeRuleMaster r : cessSurchargeRepo.findByIsActiveTrue()) {
            if (!"SURCHARGE".equalsIgnoreCase(r.getRuleType())) {
                continue;
            }

            if (!("NEW".equalsIgnoreCase(r.getTaxRegime())
                || "BOTH".equalsIgnoreCase(r.getTaxRegime()))) {
                continue;
            }

            // ✅ Apply surcharge ONLY if income falls in slab
            if (taxableIncome.compareTo(r.getIncomeFrom()) >= 0 &&
                (r.getIncomeTo() == null ||
                 taxableIncome.compareTo(r.getIncomeTo()) <= 0)) {

                surcharge =
                        taxAfterRebate
                                .multiply(r.getRate())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                break;
            }
        }

        BigDecimal taxAfterSurcharge =
                taxAfterRebate.add(surcharge);

        /* ================= CESS ================= */
        // ✅ Apply CESS only if there is some tax payable
        if (taxAfterSurcharge.compareTo(BigDecimal.ZERO) > 0) {
            for (CessSurchargeRuleMaster r : cessSurchargeRepo.findByIsActiveTrue()) {
                if (!"CESS".equalsIgnoreCase(r.getRuleType())) {
                    continue;
                }

                if (!("NEW".equalsIgnoreCase(r.getTaxRegime())
                    || "BOTH".equalsIgnoreCase(r.getTaxRegime()))) {
                    continue;
                }

                cess =
                        taxAfterSurcharge
                                .multiply(r.getRate())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                break;
            }
        }

        /* =====================================================
        * ADJUST PREVIOUS EMPLOYER TDS (Section 192)
        * ===================================================== */
        BigDecimal finalTax =
                taxAfterSurcharge.add(cess).subtract(prev.tdsPaid);

        if (finalTax.compareTo(BigDecimal.ZERO) < 0) {
        finalTax = BigDecimal.ZERO;
        }

        log.info("🧾 Previous Employer TDS Adjusted = {}", prev.tdsPaid);
        log.info("🎯 Final Tax Payable (after TDS adjustment) = {}", finalTax);


        log.info("🧾 Surcharge = {}", surcharge);
        log.info("🧾 Cess = {}", cess);
        log.info("🎯 Final Tax Payable (NEW Regime) = {}", finalTax);

        /* =====================================================
         * RESULT
         * ===================================================== */
        NewTaxCalculationResult result = new NewTaxCalculationResult();

        result.setIncomeFromSalary(incomeFromSalary);   // before deduction
        result.setGrossIncome(grossTotalIncome);        // before deduction

        result.setStandardDeduction(standardDeduction);
        result.setTaxableIncome(taxableIncome);
        result.setTaxBeforeRebate(taxBeforeRebate);
        result.setRebateAmount(rebate);
        result.setSurcharge(surcharge);
        result.setCess(cess);
        result.setTaxPayable(finalTax);

        // result.setRemainingMonths(12);
        // result.setTaxPerMonth(
        //         finalTax.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
        // );

        return result;
    }

    /**
     * NEW Regime tax calculation after POI approval.
     * Behaviour is EXACTLY same as calculateNewTax().
     * POI data does NOT affect calculation in NEW regime.
     */
    @Override
    @Transactional(readOnly = true)
    public NewTaxCalculationResult calculateNewTaxWithPOI(
            String organizationId,
            String employeeId,
            Integer financialYear
    ) {

        log.info(
                "▶ NEW TAX (WITH POI) calculation started | empId={} | FY={}",
                employeeId,
                financialYear
        );

        // 🔑 IMPORTANT:
        // - DO NOT read POI amounts
        // - DO NOT read IT declaration child tables
        // - DO NOT modify taxable income
        // - DO NOT apply deductions

        return calculateNewTax(organizationId, employeeId, financialYear);
    }
    
    @Override
    @Transactional
    public Long calculateAndSaveNewTax(
            String organizationId,
            String employeeId,
            Integer financialYear
    ) {

        String method = "calculateAndSaveNewTax";

        log.info("[{}] 🚀 START | orgId={}, employeeId={}, financialYear={}",
                method, organizationId, employeeId, financialYear);

        /* ================= CALCULATION ================= */
        log.info("[{}] 🧮 Triggering NEW TAX calculation", method);

        NewTaxCalculationResult result =
                calculateNewTax(organizationId, employeeId, financialYear);

        log.info("[{}] 🧾 Calculation complete | grossIncome={}, taxableIncome={}, finalTax={}",
                method,
                result.getGrossIncome(),
                result.getTaxableIncome(),
                result.getTaxPayable());

        /* ================= UPDATE IT DECLARATION ================= */
        BasicDetails employee =
                basicDetailsRepository.findByEmployeeId(employeeId)
                        .orElseThrow(() -> new RuntimeException("Employee not found"));

        Organization organization = employee.getOrganization();

        EmployeeInvestmentDeclaration declaration =
                declarationRepo
                        .findByOrganizationAndEmployeeAndFiscalYear(
                                organization, employee, financialYear
                        )
                        .orElse(null);

        if (declaration != null) {
                declaration.setTaxRegime("NEW");
                declaration.setTaxRegimeFormatted("New Tax Regime");

                declarationRepo.save(declaration);

                log.info("[{}] 📝 IT Declaration updated to NEW regime | empId={} | FY={}",
                        method, employeeId, financialYear);
        } else {
                log.warn("[{}] ⚠️ No IT Declaration found to update | empId={} | FY={}",
                        method, employeeId, financialYear);
        }

        /* ================= ENTITY BUILD ================= */
        log.info("[{}] 🏗️ Building NewTaxCalculation entity", method);

        NewTaxCalculation entity = new NewTaxCalculation();

        entity.setOrganizationId(organizationId);
        entity.setEmployeeId(employeeId);
        entity.setFinancialYear(financialYear);

        entity.setGrossIncome(result.getGrossIncome());
        entity.setIncomeFromSalary(result.getIncomeFromSalary());

        entity.setStandardDeduction(result.getStandardDeduction());
        entity.setTaxableIncome(result.getTaxableIncome());
        entity.setTaxBeforeRebate(result.getTaxBeforeRebate());
        entity.setRebateAmount(result.getRebateAmount());
        entity.setSurcharge(result.getSurcharge());
        entity.setCess(result.getCess());
        entity.setTaxPayable(result.getTaxPayable());

        entity.setRemainingMonths(result.getRemainingMonths());
        entity.setTaxPerMonth(result.getTaxPerMonth());

        /* ================= UPSERT ================= */
        newTaxCalculationRepository
                .findByOrganizationIdAndEmployeeIdAndFinancialYear(
                        organizationId, employeeId, financialYear)
                .ifPresent(existing -> {
                    entity.setId(existing.getId());
                    log.info("[{}] 🔁 Existing NEW TAX found | updating id={}",
                            method, existing.getId());
                });

        /* ================= SAVE ================= */
        NewTaxCalculation saved = newTaxCalculationRepository.save(entity);

        log.info("[{}] ✅ NEW TAX SAVED SUCCESSFULLY | taxId={} | emp={} | year={}",
                method, saved.getId(), employeeId, financialYear);

        log.info("[{}] 🏁 END", method);

        return saved.getId();
    }

        /**
         * NEW Regime tax calculation using revised salary.
         *
         * NOTE:
         * The base method {@link #calculateNewTax} already:
         * - Handles revised CTC across FY
         * - Calculates actual earned salary
         * - Applies deductions, slabs, rebate, surcharge, cess
         * - Adjusts previous employer TDS
         *
         * Therefore this method is only a semantic wrapper
         * for API clarity and future extensibility.
         */
        @Override
        @Transactional(readOnly = true)
        public NewTaxCalculationResult calculateNewTaxWithRevisedSalary(
                String organizationId,
                String employeeId,
                Integer fiscalYear
        ) {
        final String method = "calculateNewTaxWithRevisedSalary";

        log.info("[{}] ▶ START | empId={} | FY={}", method, employeeId, fiscalYear);

        // Delegate to single source of truth
        NewTaxCalculationResult result =
                calculateNewTax(organizationId, employeeId, fiscalYear);

        log.info("[{}] ✅ Delegated to calculateNewTax | finalTax={}",
                method, result.getTaxPayable());

        log.info("[{}] ◀ END", method);

        return result;
        }


/**
 * Calculates actual earned salary within a financial year.
 *
 * Supports:
 * - Mid-year joining
 * - CTC effective start date
 * - CTC revision end date (optional)
 * - Financial year boundary (Apr–Mar)
 * - Single CTC & revised CTC using same logic
 */
private BigDecimal calculateActualAnnualAmount(
        BigDecimal monthlyAmount,
        LocalDate joiningDate,
        LocalDate effectiveDate,
        LocalDate endDate,          // can be NULL for single-CTC case
        Integer financialYear
) {

    /* ---------- BASIC VALIDATION ---------- */
    if (monthlyAmount == null || monthlyAmount.compareTo(BigDecimal.ZERO) <= 0) {
        return BigDecimal.ZERO;
    }

    if (financialYear == null) {
        throw new IllegalArgumentException("financialYear cannot be null");
    }

    /* ---------- FINANCIAL YEAR RANGE ---------- */
    LocalDate fyStart = LocalDate.of(financialYear - 1, 4, 1);
    LocalDate fyEnd   = LocalDate.of(financialYear, 3, 31);

    /* ---------- START DATE CALCULATION ---------- */
    LocalDate salaryStart = fyStart;

    if (joiningDate != null && joiningDate.isAfter(salaryStart)) {
        salaryStart = joiningDate;
    }

    if (effectiveDate != null && effectiveDate.isAfter(salaryStart)) {
        salaryStart = effectiveDate;
    }

    /* ---------- END DATE CALCULATION ---------- */
    LocalDate salaryEnd = (endDate != null && endDate.isBefore(fyEnd))
            ? endDate
            : fyEnd;

    /* ---------- NO OVERLAP BETWEEN RANGE ---------- */
    if (salaryStart.isAfter(salaryEnd)) {
        return BigDecimal.ZERO;
    }

    /* ---------- MONTH COUNT (INCLUSIVE) ---------- */
    int months =
            (salaryEnd.getYear() - salaryStart.getYear()) * 12
            + salaryEnd.getMonthValue()
            - salaryStart.getMonthValue()
            + 1;

    /* ---------- FINAL AMOUNT ---------- */
    return monthlyAmount.multiply(BigDecimal.valueOf(months));
}

    /**
     * Converts employee dateOfJoining (String) to LocalDate safely
     */
    private LocalDate getEmployeeJoiningDate(BasicDetails employee) {

        if (employee == null || employee.getDateOfJoining() == null) {
            return null;
        }

        return LocalDate.parse(employee.getDateOfJoining());
    }

    /**
     * Calculates monthly gross salary from CTC earnings
     * (same logic used in calculateNewTax).
     */
    private BigDecimal calculateMonthlyGrossFromEarnings(CtcStructure ctc) {

        if (ctc == null || ctc.getEarnings() == null) {
            return BigDecimal.ZERO;
        }

        return ctc.getEarnings().stream()
                .filter(e -> e != null)
                .filter(e -> Boolean.TRUE.equals(e.getEnabled()))
                .map(e -> BigDecimal.valueOf(
                        e.getAmount() != null ? e.getAmount() : 0.0
                ))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

        private BigDecimal calculateTotalFySalary(
                String organizationId,
                String employeeId,
                Integer fiscalYear,
                LocalDate joiningDate
        ) {

                final String method = "calculateTotalFySalary";

        /* ---------- FY RANGE ---------- */
        LocalDate fyStart = LocalDate.of(fiscalYear - 1, 4, 1);
        LocalDate fyEnd   = LocalDate.of(fiscalYear, 3, 31);

            log.info("[{}] ▶ START | empId={} | FY={} | fyStart={} | fyEnd={} | joiningDate={}",
            method, employeeId, fiscalYear, fyStart, fyEnd, joiningDate);

        /*
        * STEP 1 — fetch ALL CTCs effective on/before FY end
        */
        List<CtcStructure> allCtcs =
                ctcStructureRepository
                        .findByOrganization_OrganizationIdAndEmployee_EmployeeId(
                                organizationId,
                                employeeId
                        )
                        .stream()
                        .filter(ctc -> !Boolean.TRUE.equals(ctc.getDeleted()))
                        .filter(ctc -> ctc.getEffectiveDate() != null)
                        .filter(ctc -> !ctc.getEffectiveDate().isAfter(fyEnd))
                        .sorted(Comparator.comparing(CtcStructure::getEffectiveDate))
                        .toList();

                        log.info("[{}] CTCs fetched till FY end = {}", method, allCtcs.size());

        if (allCtcs.isEmpty()) {
                log.error("[{}] ❌ No valid CTC found for employee {}", method, employeeId);
                throw new RuntimeException("No valid CTC found for employee");
        }

        /*
        * STEP 2 — determine ANCHOR CTC
        *
        * CASE A → employee joined BEFORE FY start
        *          → pick CTC active on Apr-1
        *
        * CASE B → employee joined AFTER FY start
        *          → pick first CTC after joining
        */
        CtcStructure anchorCtc = null;

        // CASE A — joined before FY
        if (joiningDate == null || !joiningDate.isAfter(fyStart)) {

                log.info("[{}] Employee joined BEFORE FY start → resolving CTC active on Apr-1",
                method);

                for (CtcStructure ctc : allCtcs) {
                if (!ctc.getEffectiveDate().isAfter(fyStart)) {
                        anchorCtc = ctc;   // latest CTC active on/before Apr-1
                } else {
                        break;
                }
                }

                
                // If no CTC exists before Apr-1 → use earliest available CTC
                if (anchorCtc == null) {
                anchorCtc = allCtcs.get(0);
                log.warn("[{}] No CTC found on/before Apr-1, fallback to earliest CTC | effectiveDate={}",
                    method, anchorCtc.getEffectiveDate());
                }
        }

        // CASE B — joined after FY start
        else {
                log.info("[{}] Employee joined AFTER FY start → resolving first CTC after joining",
                method);
                for (CtcStructure ctc : allCtcs) {
                if (!ctc.getEffectiveDate().isBefore(joiningDate)) {
                        anchorCtc = ctc;   // first CTC after joining
                        break;
                }
                }
        }

        if (anchorCtc == null) {
                log.error("[{}] ❌ No valid anchor CTC found | empId={} | joiningDate={}",
                method, employeeId, joiningDate);
                throw new RuntimeException("No valid CTC found in FY timeline");
        }

        log.info("[{}] Anchor CTC selected | ctcId={} | effectiveDate={}",
            method, anchorCtc.getId(), anchorCtc.getEffectiveDate());

        /*
        * FINAL FIX — make anchor date effectively final for lambda usage
        */
        final LocalDate anchorDate = anchorCtc.getEffectiveDate();

        /*
        * STEP 3 — build FY-relevant CTC list
        * Includes:
        * - Anchor CTC
        * - All revisions after anchor within FY
        */
        List<CtcStructure> ctcList =
                allCtcs.stream()
                        .filter(ctc -> !ctc.getEffectiveDate().isBefore(anchorDate))
                        .toList();

                        log.info("[{}] FY-relevant CTC count = {}", method, ctcList.size());

        /*
        * STEP 4 — calculate earned salary across revisions
        */
        BigDecimal totalSalary = BigDecimal.ZERO;

        for (int i = 0; i < ctcList.size(); i++) {

                CtcStructure current = ctcList.get(i);

                // Determine end date of this CTC period
                LocalDate endDate =
                        (i + 1 < ctcList.size())
                                ? ctcList.get(i + 1).getEffectiveDate().minusDays(1)
                                : fyEnd;

                BigDecimal monthlyGross = calculateMonthlyGrossFromEarnings(current);

                BigDecimal earned =
                        calculateActualAnnualAmount(
                                monthlyGross,
                                joiningDate,
                                current.getEffectiveDate(),
                                endDate,
                                fiscalYear
                        );

                log.info("[{}] CTC ID={} | effectiveDate={} | endDate={} | monthlyGross={} | earned={}",
                method,
                current.getId(),
                current.getEffectiveDate(),
                endDate,
                monthlyGross,
                earned);


                totalSalary = totalSalary.add(earned);
        }

            log.info("[{}] ✅ TOTAL FY EARNED SALARY = {}", method, totalSalary);
            log.info("[{}] ◀ END", method);

        return totalSalary;
        }

        /**
         * Holds computed previous employment impact.
         */
        private static class PrevEmploymentComputation {
        BigDecimal netIncome = BigDecimal.ZERO;   // income − professional tax
        BigDecimal tdsPaid   = BigDecimal.ZERO;   // previous employer TDS
        }

        /**
         * Computes previous employment impact as per Section 192.
         *
         * Rules:
         * - type = "income" → add to salary
         * - type = "professional_tax" → subtract from income
         * - type = "income_tax" → subtract later from final tax (TDS already paid)
         */
        private PrevEmploymentComputation computePreviousEmployment(
                EmployeeInvestmentDeclaration declaration
        ) {
        PrevEmploymentComputation result = new PrevEmploymentComputation();

        if (declaration == null ||
                declaration.getPrevEmploymentDeclarations() == null) {
                return result;
        }

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal professionalTax = BigDecimal.ZERO;
        BigDecimal tds = BigDecimal.ZERO;

        for (EmployeeInvPrevEmployment item : declaration.getPrevEmploymentDeclarations()) {

                if (item == null || item.getAmount() == null) continue;

                String type = item.getType() != null
                        ? item.getType().toLowerCase()
                        : "";

                switch (type) {
                case "income":
                        income = income.add(item.getAmount());
                        break;

                case "professional_tax":
                        professionalTax = professionalTax.add(item.getAmount());
                        break;

                case "income_tax":
                        tds = tds.add(item.getAmount());
                        break;
                }
        }

        BigDecimal net = income.subtract(professionalTax);

        result.netIncome = net.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : net;
        result.tdsPaid = tds;

        return result;
        }



    // TaxSlab inner class - Moved to the end of the class for proper structure
    public static class TaxSlab {
        private BigDecimal from;
        private BigDecimal to;
        private BigDecimal rate;

        public BigDecimal getFrom() { return from; }
        public BigDecimal getTo() { return to; }
        public BigDecimal getRate() { return rate; }
        
        // Setters are usually needed for Jackson deserialization
        public void setFrom(BigDecimal from) { this.from = from; }
        public void setTo(BigDecimal to) { this.to = to; }
        public void setRate(BigDecimal rate) { this.rate = rate; }
    }
}