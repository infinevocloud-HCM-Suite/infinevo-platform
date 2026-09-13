package com.itsdev.payroll.serviceimpl.employeeitdeclaration.taxCalculator;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.CtcPeriod;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.PreviousEmploymentSummary;
import com.itsdev.payroll.entity.EmployeeITDeclaration.EmployeeInvestmentDeclaration;

import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.OldTaxCalculationResult;
import com.itsdev.payroll.entity.EmployeeITDeclaration.*;
import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeePOIItem;
import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeeProofOfInvestment;
import com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.*;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.employee.CtcStructure;
import com.itsdev.payroll.entity.employee.EmployeeEarning;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.enumeration.payruns.PayRunStatus;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.Section6AItemMasterRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.poi.EmployeeProofOfInvestmentRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.taxcalculator.*;
import com.itsdev.payroll.repository.employeeitdeclaration.EmployeeInvestmentDeclarationRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.OldTaxCalculationRepository;
import com.itsdev.payroll.repository.employee.CtcStructureRepository;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;


import com.itsdev.payroll.service.employee.CtcStructureService;
import com.itsdev.payroll.service.employeeitdeclaration.taxCalculator.OldTaxCalculationService;
import com.itsdev.payroll.service.payruns.EmployeePayRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.stream.Collectors;

@Service
public class OldTaxCalculationServiceImpl
        implements OldTaxCalculationService {

    private static final Logger log =
            LoggerFactory.getLogger(OldTaxCalculationServiceImpl.class);

    // ===== RULE MASTER REPOSITORIES =====
    private final HraRuleMasterRepository hraRuleRepo;
    private final HomeLoanRuleMasterRepository homeLoanRuleRepo;
    private final LetOutPropertyRuleMasterRepository letOutRuleRepo;
    private final OtherIncomeRuleMasterRepository otherIncomeRuleRepo;
    private final TaxSlabMasterRepository taxSlabRepo;
    private final Section87aRebateRuleMasterRepository rebateRuleRepo;
    private final CessSurchargeRuleMasterRepository cessSurchargeRepo;
    private final Section6AItemMasterRepository section6AItemRepo;
    private final CtcStructureRepository ctcStructureRepository;
    private final OldTaxCalculationRepository oldTaxCalculationRepository;
    private final OldTaxCalculationRevisionRepository oldTaxCalculationRevisionRepository;




    // ===== EMPLOYEE DECLARATION =====
    private final EmployeeInvestmentDeclarationRepository declarationRepo;

    // ===== EMPLOYEE MASTER =====
    private final BasicDetailsRepository basicDetailsRepository;

    // ===== CTC STRUCTURE SERVICE =====
    private final CtcStructureService ctcStructureService;



    // ===== PAYRUN SERVICE =====
    private final EmployeePayRunService employeePayRunService;
    private final EmployeeProofOfInvestmentRepository poiRepository;

    // ===== MANUAL CONSTRUCTOR INJECTION =====
    public OldTaxCalculationServiceImpl(
            HraRuleMasterRepository hraRuleRepo,
            HomeLoanRuleMasterRepository homeLoanRuleRepo,
            LetOutPropertyRuleMasterRepository letOutRuleRepo,
            OtherIncomeRuleMasterRepository otherIncomeRuleRepo,
            TaxSlabMasterRepository taxSlabRepo,
            Section87aRebateRuleMasterRepository rebateRuleRepo,
            CessSurchargeRuleMasterRepository cessSurchargeRepo,
            Section6AItemMasterRepository section6AItemRepo,
            EmployeeInvestmentDeclarationRepository declarationRepo,
            CtcStructureService ctcStructureService,
            CtcStructureRepository ctcStructureRepository,
// 👈 ADD
            BasicDetailsRepository basicDetailsRepository,

            EmployeePayRunService employeePayRunService,
            OldTaxCalculationRepository oldTaxCalculationRepository,
            OldTaxCalculationRevisionRepository oldTaxCalculationRevisionRepository,
            EmployeeProofOfInvestmentRepository poiRepository) {

        this.hraRuleRepo = hraRuleRepo;
        this.homeLoanRuleRepo = homeLoanRuleRepo;
        this.letOutRuleRepo = letOutRuleRepo;
        this.otherIncomeRuleRepo = otherIncomeRuleRepo;
        this.taxSlabRepo = taxSlabRepo;
        this.rebateRuleRepo = rebateRuleRepo;
        this.cessSurchargeRepo = cessSurchargeRepo;
        this.section6AItemRepo = section6AItemRepo;
        this.declarationRepo = declarationRepo;
        this.ctcStructureService = ctcStructureService;
        this.ctcStructureRepository = ctcStructureRepository;

        this.basicDetailsRepository = basicDetailsRepository; // 👈 ADD THIS
        this.employeePayRunService = employeePayRunService;
        this.oldTaxCalculationRepository = oldTaxCalculationRepository;
        this.poiRepository = poiRepository;
        this.oldTaxCalculationRevisionRepository =
                oldTaxCalculationRevisionRepository;

    }


    @Override
    public OldTaxCalculationResult calculateOldTax(
            String organizationId,
            String employeeId,
            Integer financialYear
    ) {

        BigDecimal incomeFromHouseProperty = BigDecimal.ZERO;
        BigDecimal totalChapterVIA = BigDecimal.ZERO;
       BigDecimal grossTotalIncome = BigDecimal.ZERO;



        log.info("▶ Starting OLD TAX calculation | empId={} | FY={}",
                employeeId, financialYear);


        /* =====================================================
         * STEP 1: FETCH EMPLOYEE (BasicDetails)
         * ===================================================== */
        BasicDetails employee =
                basicDetailsRepository
                        .findByEmployeeIdAndOrganization_OrganizationIdAndIsDeletedFalse(
                                employeeId,
                                organizationId
                        )
                        .orElseThrow(() -> {
                            log.error("❌ Employee not found | empId={}", employeeId);
                            return new RuntimeException("Employee not found");
                        });

        log.info("✅ Employee fetched | empId={} | name={}",
                employee.getEmployeeId(),
                employee.getFirstName());


        /* =====================================================
         * STEP 2: FETCH ORGANIZATION
         * ===================================================== */
        Organization organization = employee.getOrganization();

        if (organization == null) {
            log.error("❌ Organization not linked with employee | empId={}", employeeId);
            throw new RuntimeException("Organization not found for employee");
        }

        log.info("✅ Organization fetched | orgId={}",
                organization.getOrganizationId());




        validateCtcExistsForFy(organizationId, employeeId, financialYear);



        /* =====================================================
         * CHECK SALARY REVISION IN CURRENT FY
         * ===================================================== */

        boolean hasRevisionInFy =
                hasSalaryRevisionInCurrentFY(
                        organizationId,
                        employeeId,
                        financialYear
                );

        if (hasRevisionInFy) {

            log.info("🔁 Salary revision detected in FY → delegating to calculateOldTaxWithRevisedSalary()");

            return calculateOldTaxWithRevisedSalary(
                    organizationId,
                    employeeId,
                    financialYear
            );
        }






        /* =====================================================
         * STEP 3: FETCH IT DECLARATION
         * ===================================================== */
        EmployeeInvestmentDeclaration declaration =
                declarationRepo
                        .findByOrganizationAndEmployeeAndFiscalYear(
                                organization,
                                employee,
                                financialYear
                        )
                        .orElse(null);

        if (declaration == null) {
            log.info(
                    "ℹ️ IT Declaration not found | empId={} | FY={} | Proceeding with basic OLD tax calculation",
                    employeeId,
                    financialYear
            );
        } else {

            log.info("✅ IT Declaration fetched | declarationId={}", declaration.getId());

            declaration.setTaxRegime("OLD");
            declaration.setTaxRegimeFormatted("Old Tax Regime");
            declarationRepo.save(declaration);
        }



        /* =====================================================
         * STEP 4: HRA EXEMPTION (SECTION 10(13A))
         * ===================================================== */

// 1️⃣ Fetch active HRA rule
        HraRuleMaster hraRule =
                hraRuleRepo.findByIsActiveTrue()
                        .orElseThrow(() ->
                                new RuntimeException("Active HRA rule not found"));

// 2️⃣ Fetch active CTC structure
        List<CtcStructure> ctcList =
                ctcStructureRepository
                        .findByOrganization_OrganizationIdAndEmployee_EmployeeId(
                                organization.getOrganizationId(),
                                employee.getEmployeeId()
                        );

        if (ctcList == null || ctcList.isEmpty()) {
            throw new RuntimeException("CTC structure not found for employee");
        }

        CtcStructure ctc = ctcList.get(0);

        /* -----------------------------------------------------
         * 3️⃣ Derive MONTHLY BASIC & MONTHLY HRA (NO *12)
         * ----------------------------------------------------- */
        BigDecimal monthlyBasic = BigDecimal.ZERO;
        BigDecimal monthlyHra   = BigDecimal.ZERO;


        if (ctc.getEarnings() != null) {

            for (EmployeeEarning e : ctc.getEarnings()) {

                if (!Boolean.TRUE.equals(e.getEnabled())
                        || e.getEarning() == null
                        || e.getAmount() == null) {
                    continue;
                }

                String earningName =
                        e.getEarning().getEarningName().toUpperCase();

                BigDecimal monthlyAmount =
                        BigDecimal.valueOf(e.getAmount());

                if (earningName.contains("BASIC")) {
                    monthlyBasic = monthlyBasic.add(monthlyAmount);
                }

                if (earningName.contains("HRA")
                        || earningName.contains("HOUSE RENT")) {
                    monthlyHra = monthlyHra.add(monthlyAmount);
                }
            }
        }

        /* -----------------------------------------------------
         * 4️⃣ Convert MONTHLY → ANNUAL using EFFECTIVE DATE
         * ----------------------------------------------------- */

        // Convert joining date String → LocalDate
        LocalDate joiningDate =
                LocalDate.parse(
                        employee.getDateOfJoining(),
                        DateTimeFormatter.ISO_LOCAL_DATE // yyyy-MM-dd
                );


        BigDecimal annualBasic =
                calculateGrossSalaryForFY(
                        monthlyBasic,
                        joiningDate,
                        financialYear
                );

        BigDecimal annualHraReceived =
                calculateGrossSalaryForFY(
                        monthlyHra,
                        joiningDate,
                        financialYear
                );

        log.info(
                "✅ Annual Earnings (Effective Date) | basic={} | hra={}",
                annualBasic,
                annualHraReceived
        );


        /* -----------------------------------------------------
         * 5️⃣ HRA EXEMPTION CALCULATION (MONTH-WISE, DATE AWARE)
         * ----------------------------------------------------- */
        BigDecimal hraExemption = BigDecimal.ZERO;

        if (declaration != null
                && Boolean.TRUE.equals(declaration.getIsStayingInRentedHouse())
                && declaration.getHouseRents() != null
                && !declaration.getHouseRents().isEmpty()) {


            // Monthly values for HRA rules
            BigDecimal monthlyBasicForRule =
                    annualBasic.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

            BigDecimal monthlyHraForRule =
                    annualHraReceived.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

            // Financial year boundaries
            LocalDate fyStart = LocalDate.of(financialYear, 4, 1);
            LocalDate fyEnd   = LocalDate.of(financialYear + 1, 3, 31);

            for (EmployeeInvHouseRent rent : declaration.getHouseRents()) {

                BigDecimal rentPaid = rent.getAmountPerMonth();

                /* ---------------------------
                 * HRA MONTHLY RULES
                 * --------------------------- */

                // Rule 1️⃣: Actual HRA received
                BigDecimal rule1 = monthlyHraForRule;

                // Rule 2️⃣: Rent − % of Basic
                BigDecimal tenPercentBasic =
                        monthlyBasicForRule.multiply(
                                BigDecimal.valueOf(hraRule.getRentMinusBasicPercentage())
                                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                        );

                BigDecimal rule2 = rentPaid.subtract(tenPercentBasic);
                if (rule2.compareTo(BigDecimal.ZERO) < 0) {
                    rule2 = BigDecimal.ZERO;
                }

                // Rule 3️⃣: % of Basic (Metro / Non-Metro)
                BigDecimal applicablePercent =
                        Boolean.TRUE.equals(rent.getIsMetro())
                                ? BigDecimal.valueOf(hraRule.getMetroPercentageOfBasic())
                                : BigDecimal.valueOf(hraRule.getNonMetroPercentageOfBasic());

                BigDecimal rule3 =
                        monthlyBasicForRule.multiply(
                                applicablePercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                        );

                // Final MONTHLY exemption (statutory minimum)
                BigDecimal monthlyExemption =
                        rule1
                                .min(rule2)
                                .min(rule3)
                                .min(rentPaid); // mandatory cap

                /* ---------------------------
                 * MONTH CALCULATION (CRITICAL FIX)
                 * --------------------------- */

// Convert rent period (String yyyy-MM) → YearMonth
                YearMonth rentFromYm = YearMonth.parse(rent.getFromMonth()); // e.g. "2024-04"
                YearMonth rentToYm   = YearMonth.parse(rent.getToMonth());   // e.g. "2024-12"

// Rent period dates
                LocalDate rentStart = rentFromYm.atDay(1);
                LocalDate rentEnd   = rentToYm.atEndOfMonth();

// Effective start = max(joiningDate, rentStart, FY start)
                LocalDate effectiveStart = joiningDate;

                if (rentStart.isAfter(effectiveStart)) {
                    effectiveStart = rentStart;
                }
                if (fyStart.isAfter(effectiveStart)) {
                    effectiveStart = fyStart;
                }

// Effective end = min(rentEnd, FY end)
                LocalDate effectiveEnd =
                        rentEnd.isBefore(fyEnd) ? rentEnd : fyEnd;

                int months = 0;

                if (!effectiveStart.isAfter(effectiveEnd)) {
                    months =
                            (int) ChronoUnit.MONTHS.between(
                                    YearMonth.from(effectiveStart),
                                    YearMonth.from(effectiveEnd)
                            ) + 1;
                }

                if (months < 0) {
                    months = 0;
                }


                // Accumulate exemption
                hraExemption =
                        hraExemption.add(
                                monthlyExemption.multiply(BigDecimal.valueOf(months))
                        );

                log.info(
                        "🏠 HRA | rentPeriod={}–{} | months={} | monthlyExemption={} | total={}",
                        rent.getFromMonth(),
                        rent.getToMonth(),
                        months,
                        monthlyExemption,
                        monthlyExemption.multiply(BigDecimal.valueOf(months))
                );
            }
        }

        log.info("✅ HRA Exemption calculated (before cap) = {}", hraExemption);

// 🔐 Safety cap: cannot exceed actual HRA received
        if (hraExemption.compareTo(annualHraReceived) > 0) {
            log.warn(
                    "⚠️ HRA capped to actual received | calculated={} | received={}",
                    hraExemption,
                    annualHraReceived
            );
            hraExemption = annualHraReceived;
        }

        log.info("✅ Final HRA Exemption = {}", hraExemption);



        /* =====================================================
         * STEP 5: SALARY INCOME (EFFECTIVE DATE BASED)
         * ===================================================== */
        BigDecimal monthlyGrossSalary = BigDecimal.ZERO;

        if (ctc.getEarnings() != null) {

            monthlyGrossSalary =
                    ctc.getEarnings().stream()
                            .filter(EmployeeEarning::getEnabled)
                            .map(e ->
                                    BigDecimal.valueOf(
                                            e.getAmount() != null ? e.getAmount() : 0.0
                                    )
                            )
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal grossSalary =
                calculateGrossSalaryForFY(
                        monthlyGrossSalary,
                        joiningDate,
                        financialYear
                );

        log.info(
                "✅ Gross Salary calculated | monthly={} | annual={}",
                monthlyGrossSalary,
                grossSalary
        );



        /* ---------- 3.2 Previous Employment (Section 192 Govt Rule) ---------- */

        PreviousEmploymentSummary prevSummary =
                extractPreviousEmploymentSummary(declaration);

        BigDecimal netPreviousIncome = prevSummary.getNetPreviousIncome();
        BigDecimal prevTds = prevSummary.getPreviousEmployerTds();

        log.info(
                "✅ Previous Employment Applied | netIncome={} | previousTds={}",
                netPreviousIncome,
                prevTds
        );



        /* =====================================================
         * STEP-5: INCOME FROM HOUSE PROPERTY (LET-OUT)
         * ===================================================== */

// Fetch active Let-Out Property rule (OLD regime)
        LetOutPropertyRuleMaster letOutRule =
                letOutRuleRepo.findByIsActiveTrueAndTaxRegime("OLD")
                        .orElseThrow(() ->
                                new RuntimeException("Active Let-Out Property rule not found"));

// Final income from ALL house properties
        BigDecimal totalLetOutIncome = BigDecimal.ZERO;

        if (declaration != null
                && declaration.getLetOutProperties() != null
                && !declaration.getLetOutProperties().isEmpty()) {


            for (EmployeeInvLetOutProperty property : declaration.getLetOutProperties()) {

                BigDecimal propertyIncome;

                /*
                 * UI ALREADY SENDS netIncomeLoss (FINAL VALUE)
                 * If present, ALWAYS trust UI and skip recalculation
                 */
                if (property.getNetIncomeLoss() != null) {

                    propertyIncome = property.getNetIncomeLoss();

                } else {

                    // Manual calculation (ONLY when UI doesn't send it)
                    BigDecimal annualRent = BigDecimal.ZERO;
                    BigDecimal municipalTax = BigDecimal.ZERO;
                    BigDecimal loanInterest = BigDecimal.ZERO;

                    if (property.getPropertyDetails() != null) {
                        for (EmployeeInvLetOutPropertyDetail detail : property.getPropertyDetails()) {

                            if (detail.getAmount() == null) continue;

                            switch (detail.getType().toUpperCase()) {

                                case "ANNUAL_RENT":
                                    annualRent = annualRent.add(detail.getAmount());
                                    break;

                                case "MUNICIPAL_TAX":
                                    municipalTax = municipalTax.add(detail.getAmount());
                                    break;

                                case "INTEREST_ON_LOAN":
                                    if (Boolean.TRUE.equals(letOutRule.getHomeLoanInterestAllowed())) {
                                        loanInterest = loanInterest.add(detail.getAmount());
                                    }
                                    break;

                                default:
                                    break;
                            }
                        }
                    }

                    // Compute NAV
                    BigDecimal netAnnualValue = annualRent.subtract(municipalTax);
                    if (netAnnualValue.compareTo(BigDecimal.ZERO) < 0) {
                        netAnnualValue = BigDecimal.ZERO;
                    }

                    // Standard deduction @ 30%
                    BigDecimal stdDeduction = netAnnualValue
                            .multiply(BigDecimal.valueOf(30))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                    // Final Let-Out income
                    propertyIncome = netAnnualValue
                            .subtract(stdDeduction)
                            .subtract(loanInterest);
                }

                // Add to total
                totalLetOutIncome = totalLetOutIncome.add(propertyIncome);

                // Save back for API response
                property.setNetIncomeLoss(propertyIncome);

                log.info("🏠 Let-Out Property | Computed Income = {}", propertyIncome);
            }
        }

        /*
         * Combine SELF-OCCUPIED (24B interest) + LET-OUT
         */
        BigDecimal combinedHPIncome = incomeFromHouseProperty.add(totalLetOutIncome);

        /*
         * Apply OVERALL HOUSE PROPERTY LOSS LIMIT (₹2,00,000)
         */
        BigDecimal maxLossSetOff =
                BigDecimal.valueOf(letOutRule.getMaxLossSetOffAgainstSalary());

        if (combinedHPIncome.compareTo(BigDecimal.ZERO) < 0
                && combinedHPIncome.abs().compareTo(maxLossSetOff) > 0) {

            combinedHPIncome = maxLossSetOff.negate();
        }

        incomeFromHouseProperty = combinedHPIncome;

        log.info("✅ Final Income from House Property (post cap) = {}", incomeFromHouseProperty);


        /* =====================================================
         * STEP-3A: STANDARD DEDUCTION (SECTION 16)
         * Applicable ONLY for OLD Regime
         * ===================================================== */
        /* =====================================================
         * STEP-3A: STANDARD DEDUCTION (SECTION 16)
         * Applicable ONLY for OLD Regime
         * ===================================================== */

        BigDecimal standardDeduction = BigDecimal.ZERO;

        if (grossSalary != null && grossSalary.compareTo(BigDecimal.ZERO) > 0) {

            if (declaration == null
                    || "OLD".equalsIgnoreCase(declaration.getTaxRegime())) {


                standardDeduction = BigDecimal.valueOf(50000);

                if (standardDeduction.compareTo(grossSalary) > 0) {
                    standardDeduction = grossSalary;
                }

                log.info(
                        "✅ Standard Deduction applied | grossSalary={} | stdDeduction={}",
                        grossSalary,
                        standardDeduction
                );
            }
        }



        /* =====================================================
         * STEP-6: INCOME FROM OTHER SOURCES
         * ===================================================== */

        BigDecimal totalOtherIncome = BigDecimal.ZERO;



        if (declaration != null
                && declaration.getOtherIncomes() != null) {

            for (EmployeeInvOtherIncome income : declaration.getOtherIncomes()) {

                if (income.getAmount() != null) {
                    totalOtherIncome =
                            totalOtherIncome.add(income.getAmount());
                }
            }
        }


        log.info("✅ Income from Other Sources = {}", totalOtherIncome);



//        /* =====================================================
//         * STEP-6A: CHAPTER VI-A DEDUCTIONS (80C, 80D, OTHERS)
//         * ===================================================== */
//
//// Always reset before calculation
//
////  Always reset here
//        totalChapterVIA = BigDecimal.ZERO;
//
//// Fetch active Section 6A master items
//        List<Section6AItemMaster> section6AMasters =
//                section6AItemRepo.findByIsActiveTrue();
//
//// Map master by ID
//        Map<Long, Section6AItemMaster> section6AMasterMap =
//                section6AMasters.stream()
//                        .collect(Collectors.toMap(
//                                Section6AItemMaster::getId,
//                                m -> m
//                        ));
//
//        /* ---------- SECTION 80C (AGGREGATE CAP ₹1,50,000) ---------- */
//        BigDecimal total80CClaimed = BigDecimal.ZERO;
//
//        /* ---------- SECTION 80D (SEPARATE CAPS) ---------- */
//        BigDecimal selfFamily80D = BigDecimal.ZERO;
//        BigDecimal parents80D = BigDecimal.ZERO;
//
//        /* ---------- OTHER SECTIONS ---------- */
//        BigDecimal otherSectionTotal = BigDecimal.ZERO;
//
//        if (declaration.getSection6aDeclarations() != null) {
//
//            for (EmployeeInvSection6A inv : declaration.getSection6aDeclarations()) {
//
//                if (inv.getAmount() == null) continue;
//
//                Section6AItemMaster master =
//                        section6AMasterMap.get(inv.getSection6aItemId());
//
//                if (master == null) continue;
//
//                BigDecimal amount = inv.getAmount();
//                String type = master.getType() != null
//                        ? master.getType().toUpperCase()
//                        : "";
//
//                /* ---------- 80C ---------- */
//                if (Boolean.TRUE.equals(master.getIs80c())) {
//                    total80CClaimed = total80CClaimed.add(amount);
//                }
//
//                /* ---------- 80D ---------- */
//                else if (Boolean.TRUE.equals(master.getIs80d())) {
//
//                    if (type.contains("SELF")) {
//                        selfFamily80D = selfFamily80D.add(amount);
//                    }
//                    if (type.contains("PARENTS")) {
//                        parents80D = parents80D.add(amount);
//                    }
//                }
//
//                /* ---------- OTHER SECTIONS ---------- */
//                else if (Boolean.TRUE.equals(master.getIsOtherSection())) {
//
//                    BigDecimal allowed;
//
//                    // 80TTA – Savings Interest (Non-Senior)
//                    if ("SAVING_INTEREST".equals(type)) {
//                        allowed = amount.min(BigDecimal.valueOf(10000));
//                    }
//                    // 80TTB – Savings Interest (Senior Citizen)
//                    else if ("SAVING_INTEREST_SENIOR".equals(type)) {
//                        allowed = amount.min(BigDecimal.valueOf(50000));
//                    }
//                    // Other sections (80E, 80G, etc.)
//                    else {
//                        allowed =
//                                master.getMaxLimit() != null
//                                        ? amount.min(master.getMaxLimit())
//                                        : amount;
//                    }
//
//                    otherSectionTotal = otherSectionTotal.add(allowed);
//
//                    log.info(
//                            "✅ Other Section applied | type={} | claimed={} | allowed={}",
//                            master.getType(),
//                            amount,
//                            allowed
//                    );
//                }
//            }
//        }
//
//        /* ---------- APPLY CAPS ---------- */
//
//// 80C cap
//        BigDecimal allowed80C =
//                total80CClaimed.min(BigDecimal.valueOf(150000));
//
//// 80D caps
//        BigDecimal allowedSelf80D =
//                selfFamily80D.min(BigDecimal.valueOf(25000));
//
//        BigDecimal allowedParents80D =
//                parents80D.min(BigDecimal.valueOf(50000));
//
//        BigDecimal total80D =
//                allowedSelf80D.add(allowedParents80D);
//
//        /* ---------- TOTAL CHAPTER VI-A ---------- */
//        totalChapterVIA =
//                allowed80C
//                        .add(total80D)
//                        .add(otherSectionTotal);
//
//        log.info(
//                "🎯 Chapter VI-A Summary | 80C={} | 80D={} | other={} | total={}",
//                allowed80C,
//                total80D,
//                otherSectionTotal,
//                totalChapterVIA
//        );


        /* =====================================================
         * STEP-6A: CHAPTER VI-A DEDUCTIONS (80C, 80D, OTHERS)
         * ===================================================== */

// 🔁 Always reset before calculation
        totalChapterVIA = BigDecimal.ZERO;

// Fetch active Section 6A master items
        List<Section6AItemMaster> section6AMasters =
                section6AItemRepo.findByIsActiveTrue();

// Map master by ID
        Map<Long, Section6AItemMaster> section6AMasterMap =
                section6AMasters.stream()
                        .collect(Collectors.toMap(
                                Section6AItemMaster::getId,
                                m -> m
                        ));

        /* ---------- SECTION 80C (AGGREGATE CAP ₹1,50,000) ---------- */
        BigDecimal total80CClaimed = BigDecimal.ZERO;

        /* ---------- SECTION 80D (SEPARATE CAPS) ---------- */
        BigDecimal selfFamily80D = BigDecimal.ZERO;
        BigDecimal parents80D = BigDecimal.ZERO;

// To avoid duplicate 80D entries
        Set<Long> processed80DItems = new HashSet<>();

        /* ---------- 80CCD(1B) – ADDITIONAL NPS (SEPARATE ₹50,000) ---------- */
        BigDecimal additionalNps80CCD = BigDecimal.ZERO;

        /* ---------- OTHER SECTIONS ---------- */
        BigDecimal otherSectionTotal = BigDecimal.ZERO;

        if (declaration != null
                && declaration.getSection6aDeclarations() != null) {

            for (EmployeeInvSection6A inv : declaration.getSection6aDeclarations()) {


                if (inv.getAmount() == null) continue;

                Section6AItemMaster master =
                        section6AMasterMap.get(inv.getSection6aItemId());

                if (master == null) continue;

                BigDecimal amount = inv.getAmount();

                // ✅ USE DECLARATION TYPE (NOT MASTER TYPE)
                String type =
                        inv.getType() != null
                                ? inv.getType().toUpperCase()
                                : "";

                /* ---------- SECTION 80C ---------- */
                if (Boolean.TRUE.equals(master.getIs80c())) {

                    total80CClaimed = total80CClaimed.add(amount);
                }

                /* ---------- SECTION 80D ---------- */
                else if (Boolean.TRUE.equals(master.getIs80d())) {

                    // Prevent duplicate 80D entries
                    if (!processed80DItems.add(inv.getSection6aItemId())) {
                        log.warn("⚠️ Duplicate 80D entry ignored | itemId={}", inv.getSection6aItemId());
                        continue;
                    }

                    if (type.contains("SELF")) {
                        selfFamily80D = selfFamily80D.add(amount);
                    }
                    else if (type.contains("PARENTS")) {
                        parents80D = parents80D.add(amount);
                    }
                }

                /* ---------- 80CCD(1B) – ADDITIONAL NPS ---------- */
                else if ("NPS_ADDITIONAL".equals(type)) {

                    additionalNps80CCD =
                            additionalNps80CCD
                                    .add(amount)
                                    .min(BigDecimal.valueOf(50000));
                }

                /* ---------- OTHER SECTIONS ---------- */
                else if (Boolean.TRUE.equals(master.getIsOtherSection())) {

                    BigDecimal allowed;

                    // 80TTA – Savings Interest
                    if ("SAVING_INTEREST".equals(type)) {
                        allowed = amount.min(BigDecimal.valueOf(10000));
                    }
                    // 80TTB – Senior Citizen Savings Interest
                    else if ("SAVING_INTEREST_SENIOR".equals(type)) {
                        allowed = amount.min(BigDecimal.valueOf(50000));
                    }
                    // 80G – Donations
                    else if ("DONATION_50".equals(type)) {
                        allowed = amount.multiply(BigDecimal.valueOf(0.5));
                    }
                    else if ("DONATION_100".equals(type)) {
                        allowed = amount;
                    }
                    // Other sections (80E, etc.)
                    else {
                        allowed =
                                master.getMaxLimit() != null
                                        ? amount.min(master.getMaxLimit())
                                        : amount;
                    }

                    otherSectionTotal = otherSectionTotal.add(allowed);

                    log.info(
                            "✅ Other Section applied | type={} | claimed={} | allowed={}",
                            type,
                            amount,
                            allowed
                    );
                }
            }
        }

        /* ---------- APPLY CAPS ---------- */

// 80C cap
        BigDecimal allowed80C =
                total80CClaimed.min(BigDecimal.valueOf(150000));

// 80D caps
        BigDecimal allowedSelf80D =
                selfFamily80D.min(BigDecimal.valueOf(25000));

        BigDecimal allowedParents80D =
                parents80D.min(BigDecimal.valueOf(50000));

        BigDecimal total80D =
                allowedSelf80D.add(allowedParents80D);

        /* ---------- TOTAL CHAPTER VI-A ---------- */
        totalChapterVIA =
                allowed80C
                        .add(total80D)
                        .add(additionalNps80CCD)
                        .add(otherSectionTotal);

        log.info(
                "🎯 Chapter VI-A Summary | 80C={} | 80D={} | 80CCD(1B)={} | other={} | total={}",
                allowed80C,
                total80D,
                additionalNps80CCD,
                otherSectionTotal,
                totalChapterVIA
        );


        /* =====================================================
         * STEP-5A: HOME LOAN INTEREST (SECTION 24B – SELF OCCUPIED)
         * ===================================================== */

        BigDecimal homeLoanInterestDeduction = BigDecimal.ZERO;

// Fetch active Section 24B rule
        Optional<HomeLoanRuleMaster> homeLoanRuleOpt =
                homeLoanRuleRepo.findByIsActiveTrue()
                        .stream()
                        .filter(r ->
                                "24B".equalsIgnoreCase(r.getSectionCode()) &&
                                        "INTEREST".equalsIgnoreCase(r.getComponent())
                        )
                        .findFirst();

        if (homeLoanRuleOpt.isPresent()
                && declaration != null
                && Boolean.TRUE.equals(declaration.getIsRepayingSelfOccupiedLoan())
                && declaration.getHomeLoans() != null
                && !declaration.getHomeLoans().isEmpty()) {

            HomeLoanRuleMaster rule = homeLoanRuleOpt.get();

            BigDecimal totalInterestPaid =
                    declaration.getHomeLoans().stream()
                            .map(EmployeeInvHomeLoan::getInterestPaid)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalInterestPaid.compareTo(BigDecimal.ZERO) > 0) {

                homeLoanInterestDeduction =
                        rule.getMaxLimit() != null
                                ? totalInterestPaid.min(rule.getMaxLimit())
                                : totalInterestPaid;

                /*
                 * ✔ Section 24B applies ONLY to self-occupied property
                 * ✔ Income from House Property becomes NEGATIVE
                 */
                incomeFromHouseProperty =
                        incomeFromHouseProperty.subtract(homeLoanInterestDeduction);

                log.info(
                        "✅ Section 24B (Self Occupied) | interestPaid={} | allowed={} | housePropertyIncome={}",
                        totalInterestPaid,
                        homeLoanInterestDeduction,
                        incomeFromHouseProperty
                );
            }
        }





        /* =====================================================
         * HOUSE PROPERTY LOSS SAFETY CAP (₹2,00,000)
         * ===================================================== */

        BigDecimal MAX_HOUSE_PROPERTY_LOSS = BigDecimal.valueOf(200000);

        if (incomeFromHouseProperty.compareTo(BigDecimal.ZERO) < 0) {

            if (incomeFromHouseProperty.abs().compareTo(MAX_HOUSE_PROPERTY_LOSS) > 0) {

                log.warn(
                        "⚠️ House Property loss capped | original={} | cappedTo=-200000",
                        incomeFromHouseProperty
                );

                incomeFromHouseProperty = MAX_HOUSE_PROPERTY_LOSS.negate();
            }
        }

        log.info(
                "✅ Final Income from House Property (after statutory cap) = {}",
                incomeFromHouseProperty
        );


        /* =====================================================
         * STEP-6A (HOME LOAN): 80EE / 80EEA
         * ===================================================== */

// NOTE:
// EmployeeInvHomeLoan does NOT have
// - loanSanctionDate
// - isFirstTimeBuyer
// Hence 80EE / 80EEA CANNOT be applied safely.

        log.info(
                "⚠️ Skipping 80EE / 80EEA deduction — loan sanction date / first-time buyer info not available"
        );

// Do NOT add anything to totalChapterVIA here





        /* =====================================================
         * STEP-7: GROSS TOTAL INCOME → TAXABLE INCOME
         * ===================================================== */

        /* ---------- 7️⃣ Income from Salary ---------- */

// Salary after HRA exemption
        BigDecimal salaryAfterHra =
                grossSalary.subtract(hraExemption);

        if (salaryAfterHra.compareTo(BigDecimal.ZERO) < 0) {
            salaryAfterHra = BigDecimal.ZERO;
        }

// ✅ APPLY STANDARD DEDUCTION ONLY ONCE
        BigDecimal incomeFromSalary = salaryAfterHra;

        if (standardDeduction.compareTo(BigDecimal.ZERO) > 0) {
            incomeFromSalary = incomeFromSalary.subtract(standardDeduction);
        }

        if (incomeFromSalary.compareTo(BigDecimal.ZERO) < 0) {
            incomeFromSalary = BigDecimal.ZERO;
        }

        log.info(
                "✅ Income from Salary | gross={} | hraExemption={} | stdDeduction={} | finalSalary={}",
                grossSalary,
                hraExemption,
                standardDeduction,
                incomeFromSalary
        );


        /* ---------- 7️⃣A: Gross Total Income ---------- */

        grossTotalIncome =
                incomeFromSalary
                        .add(netPreviousIncome)
                        .add(incomeFromHouseProperty)
                        .add(totalOtherIncome);

        if (grossTotalIncome.compareTo(BigDecimal.ZERO) < 0) {
            grossTotalIncome = BigDecimal.ZERO;
        }

        log.info(
                "✅ Gross Total Income | salary={} | prevIncome={} | houseProperty={} | otherIncome={} | total={}",
                incomeFromSalary,
                netPreviousIncome,
                incomeFromHouseProperty,
                totalOtherIncome,
                grossTotalIncome
        );

        /* ---------- 7️⃣B: Chapter VI-A Safety Cap ---------- */

        if (totalChapterVIA.compareTo(grossTotalIncome) > 0) {
            log.warn(
                    "⚠️ Chapter VI-A capped | original={} | cappedTo={}",
                    totalChapterVIA,
                    grossTotalIncome
            );
            totalChapterVIA = grossTotalIncome;
        }

        /* ---------- 7️⃣C: Taxable Income ---------- */

        BigDecimal taxableIncome =
                grossTotalIncome.subtract(totalChapterVIA);

        if (taxableIncome.compareTo(BigDecimal.ZERO) < 0) {
            taxableIncome = BigDecimal.ZERO;
        }

        log.info(
                "✅ Taxable Income | grossTotalIncome={} | chapterVIA={} | taxableIncome={}",
                grossTotalIncome,
                totalChapterVIA,
                taxableIncome
        );


//


        /* =====================================================
         * STEP-8: OLD REGIME TAX SLAB CALCULATION
         * ===================================================== */

// Fetch active OLD regime slab
        TaxSlabMaster slabMaster =
                taxSlabRepo.findByTaxRegimeAndIsActiveTrue("OLD")
                        .stream()
                        .findFirst()
                        .orElseThrow(() ->
                                new RuntimeException("Active OLD tax slab not found")
                        );

// Parse slab JSON
        ObjectMapper objectMapper = new ObjectMapper();

        List<TaxSlab> slabs;
        try {
            slabs = objectMapper.readValue(
                    slabMaster.getSlabJson(),
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, TaxSlab.class)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse tax slab JSON", e);
        }

        BigDecimal totalTaxBeforeRebate = BigDecimal.ZERO;

        for (TaxSlab slab : slabs) {

            BigDecimal slabFrom = slab.getFrom();
            BigDecimal slabTo = slab.getTo(); // null = no upper limit
            BigDecimal slabRate = slab.getRate(); // %

            // Skip slab if income does not reach slab start
            if (taxableIncome.compareTo(slabFrom) <= 0) {
                continue;
            }

            // Effective upper limit
            BigDecimal effectiveUpper =
                    slabTo != null
                            ? slabTo.min(taxableIncome)
                            : taxableIncome;

            // Taxable amount in this slab
            BigDecimal taxableInSlab =
                    effectiveUpper.subtract(slabFrom);

            if (taxableInSlab.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal slabTax =
                    taxableInSlab
                            .multiply(slabRate)
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            totalTaxBeforeRebate =
                    totalTaxBeforeRebate.add(slabTax);

            log.info(
                    "🧮 Slab {}–{} @ {}% | taxable={} | tax={}",
                    slabFrom,
                    slabTo != null ? slabTo : "∞",
                    slabRate,
                    taxableInSlab,
                    slabTax
            );
        }

        log.info(
                "✅ Total Tax Before Rebate = {}",
                totalTaxBeforeRebate
        );



        /* =====================================================
         * STEP-9: SECTION 87A REBATE (OLD REGIME)
         * ===================================================== */

        BigDecimal rebateAmount = BigDecimal.ZERO;

// Fetch active 87A rule for OLD regime
        Section87ARebateRuleMaster rebateRule =
                rebateRuleRepo
                        .findByTaxRegimeAndIsActiveTrue("OLD")
                        .orElse(null);

        if (rebateRule != null
                && rebateRule.getIncomeThreshold() != null
                && rebateRule.getMaxRebateAmount() != null) {

            // Rebate applicable ONLY if tax exists
            if (totalTaxBeforeRebate.compareTo(BigDecimal.ZERO) > 0
                    && taxableIncome.compareTo(rebateRule.getIncomeThreshold()) <= 0) {

                // Rebate = lower of (tax before rebate, max rebate)
                rebateAmount =
                        totalTaxBeforeRebate.min(
                                rebateRule.getMaxRebateAmount()
                        );

                log.info(
                        "✅ Section 87A Rebate Applied | taxableIncome={} | taxBeforeRebate={} | rebate={}",
                        taxableIncome,
                        totalTaxBeforeRebate,
                        rebateAmount
                );

            } else {

                log.info(
                        "ℹ️ Section 87A Not Applicable | taxableIncome={} | threshold={} | taxBeforeRebate={}",
                        taxableIncome,
                        rebateRule.getIncomeThreshold(),
                        totalTaxBeforeRebate
                );
            }

        } else {
            log.warn("⚠️ Section 87A rule not configured properly for OLD regime");
        }

// Tax after rebate
        BigDecimal taxAfterRebate =
                totalTaxBeforeRebate.subtract(rebateAmount);

        if (taxAfterRebate.compareTo(BigDecimal.ZERO) < 0) {
            taxAfterRebate = BigDecimal.ZERO;
        }

        log.info(
                "✅ Tax After Section 87A Rebate = {}",
                taxAfterRebate
        );




        /* =====================================================
         * STEP-10: SURCHARGE CALCULATION (OLD REGIME)
         * ===================================================== */

        BigDecimal surchargeAmount = BigDecimal.ZERO;

// Surcharge applies ONLY if tax exists
        if (taxAfterRebate.compareTo(BigDecimal.ZERO) > 0) {

            List<CessSurchargeRuleMaster> activeRules =
                    cessSurchargeRepo.findByIsActiveTrue();

            for (CessSurchargeRuleMaster rule : activeRules) {

                // Only SURCHARGE rules
                if (!"SURCHARGE".equalsIgnoreCase(rule.getRuleType())) {
                    continue;
                }

                // Regime check (OLD or BOTH)
                if (rule.getTaxRegime() != null
                        && !"BOTH".equalsIgnoreCase(rule.getTaxRegime())
                        && !"OLD".equalsIgnoreCase(rule.getTaxRegime())) {
                    continue;
                }

                // Safety checks
                if (rule.getRate() == null) {
                    continue;
                }

                BigDecimal from = rule.getIncomeFrom();
                BigDecimal to = rule.getIncomeTo();

                boolean applicable = true;

                // Lower bound check (taxableIncome >= from)
                if (from != null && taxableIncome.compareTo(from) < 0) {
                    applicable = false;
                }

                // Upper bound check (taxableIncome <= to)
                if (to != null && taxableIncome.compareTo(to) > 0) {
                    applicable = false;
                }

                if (!applicable) {
                    continue;
                }

                // Apply surcharge on tax AFTER rebate
                surchargeAmount =
                        taxAfterRebate
                                .multiply(rule.getRate())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                log.info(
                        "✅ Surcharge Applied | taxableIncome={} | rate={} | surcharge={}",
                        taxableIncome,
                        rule.getRate(),
                        surchargeAmount
                );

                // IMPORTANT: Only one surcharge slab applies
                break;
            }
        } else {
            log.info("ℹ️ Surcharge skipped as taxAfterRebate = 0");
        }

        log.info("✅ Total Surcharge = {}", surchargeAmount);

// Tax after surcharge
        BigDecimal taxAfterSurcharge =
                taxAfterRebate.add(surchargeAmount);

        log.info("✅ Tax after Surcharge = {}", taxAfterSurcharge);




        /* =====================================================
         * STEP-11: HEALTH & EDUCATION CESS (4%)
         * ===================================================== */

        BigDecimal cessAmount = BigDecimal.ZERO;

// Apply cess ONLY if tax exists
        if (taxAfterSurcharge.compareTo(BigDecimal.ZERO) > 0) {

            List<CessSurchargeRuleMaster> cessRules =
                    cessSurchargeRepo.findByIsActiveTrue();

            for (CessSurchargeRuleMaster rule : cessRules) {

                // Only CESS rules
                if (!"CESS".equalsIgnoreCase(rule.getRuleType())) {
                    continue;
                }

                // Regime check (OLD / BOTH)
                if (rule.getTaxRegime() != null
                        && !"BOTH".equalsIgnoreCase(rule.getTaxRegime())
                        && !"OLD".equalsIgnoreCase(rule.getTaxRegime())) {
                    continue;
                }

                // Safety check
                if (rule.getRate() == null) {
                    continue;
                }

                // Apply cess on tax AFTER surcharge
                cessAmount =
                        taxAfterSurcharge
                                .multiply(rule.getRate())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                log.info(
                        "✅ Health & Education Cess applied | rate={} | cess={}",
                        rule.getRate(),
                        cessAmount
                );

                // Only one cess rule applies
                break;
            }
        } else {
            log.info("ℹ️ Cess skipped as taxAfterSurcharge = 0");
        }

        log.info("✅ Total Health & Education Cess = {}", cessAmount);

        /* ---------- FINAL TAX PAYABLE ---------- */
//        BigDecimal finalTaxPayable =
//                taxAfterSurcharge.add(cessAmount);
//
//        log.info("🎯 FINAL TAX PAYABLE (Old Regime) = {}", finalTaxPayable);


        /* ---------- FINAL TAX PAYABLE BEFORE PREVIOUS TDS ---------- */
        BigDecimal finalTaxBeforeAdjustment =
                taxAfterSurcharge.add(cessAmount);

        /* ---------- ADJUST PREVIOUS EMPLOYER TDS (Section 192) ---------- */
        BigDecimal finalTaxPayable =
                finalTaxBeforeAdjustment.subtract(prevTds != null ? prevTds : BigDecimal.ZERO);

        if (finalTaxPayable.compareTo(BigDecimal.ZERO) < 0) {
            finalTaxPayable = BigDecimal.ZERO; // cannot go negative
        }

        log.info(
                "🎯 FINAL TAX PAYABLE | beforeAdjustment={} | prevTds={} | final={}",
                finalTaxBeforeAdjustment,
                prevTds,
                finalTaxPayable
        );





        /* ---------- Payroll ---------- */
        Integer remainingMonths =
                calculateRemainingMonths(joiningDate, financialYear);


        /* =====================================================
         * STEP-12: BUILD OLD TAX CALCULATION RESULT
         * ===================================================== */

        OldTaxCalculationResult result = new OldTaxCalculationResult();

        /* ---------- Income ---------- */
        result.setGrossIncome(
                grossTotalIncome.compareTo(BigDecimal.ZERO) < 0
                        ? BigDecimal.ZERO
                        : grossTotalIncome
        );



        // Income from Salary = Salary after HRA (before standard deduction)
        BigDecimal salaryForDisplay =
                grossSalary.subtract(hraExemption);

        if (salaryForDisplay.compareTo(BigDecimal.ZERO) < 0) {
            salaryForDisplay = BigDecimal.ZERO;
        }

        result.setIncomeFromSalary(salaryForDisplay);

        // 👇 ADD THIS LINE (ONLY THIS)
        result.setStandardDeduction(standardDeduction);

        result.setIncomeFromHouseProperty(incomeFromHouseProperty);
        result.setIncomeFromOtherSources(totalOtherIncome);

        /* ---------- Previous Employment (Section 192) ---------- */

        result.setPreviousEmploymentIncome(
                netPreviousIncome.compareTo(BigDecimal.ZERO) < 0
                        ? BigDecimal.ZERO
                        : netPreviousIncome
        );

        result.setTdsByPreviousEmployer(
                prevTds != null && prevTds.compareTo(BigDecimal.ZERO) > 0
                        ? prevTds
                        : BigDecimal.ZERO
        );

        /* ---------- Exemptions ---------- */
        result.setHraExemption(hraExemption);

        /* ---------- Chapter VI-A ---------- */
        result.setTotalChapterVIA(totalChapterVIA);

        /* ---------- Section-wise breakup (for UI / audit) ---------- */
        if (hraExemption.compareTo(BigDecimal.ZERO) > 0) {
            result.addSectionDeduction("HRA", hraExemption);
        }

        if (allowed80C.compareTo(BigDecimal.ZERO) > 0) {
            result.addSectionDeduction("80C", allowed80C);
        }

        if (total80D.compareTo(BigDecimal.ZERO) > 0) {
            result.addSectionDeduction("80D", total80D);
        }

        if (homeLoanInterestDeduction.compareTo(BigDecimal.ZERO) > 0) {
            result.addSectionDeduction("24B", homeLoanInterestDeduction);
        }

        /* ---------- Tax Calculation ---------- */
        result.setTaxableIncome(
                taxableIncome.compareTo(BigDecimal.ZERO) < 0
                        ? BigDecimal.ZERO
                        : taxableIncome
        );

        /* Slab tax BEFORE rebate */
        result.setTaxBeforeRebate(totalTaxBeforeRebate);

        /* Rebate / Surcharge / Cess */
        result.setRebateAmount(rebateAmount);
        result.setSurcharge(surchargeAmount);
        result.setCess(cessAmount);

        /* Final tax payable */
        result.setTaxPayable(finalTaxPayable);

        /* ---------- Payroll ---------- */
        result.setRemainingMonths(remainingMonths);

        BigDecimal taxPerMonth =
                (remainingMonths != null && remainingMonths > 0)
                        ? finalTaxPayable.divide(
                        BigDecimal.valueOf(remainingMonths),
                        2,
                        RoundingMode.HALF_UP
                )
                        : BigDecimal.ZERO;

        result.setTaxPerMonth(taxPerMonth);

        /* ---------- Final Log ---------- */
        log.info(
                "✅ OLD TAX RESULT | grossIncome={} | chapterVIA={} | taxableIncome={} | slabTax={} | cess={} | finalTax={} | perMonth={}",
                result.getGrossIncome(),
                totalChapterVIA,
                result.getTaxableIncome(),
                totalTaxBeforeRebate,
                cessAmount,
                finalTaxPayable,
                taxPerMonth
        );

        return result;

//
//        /* =====================================================
//         * TEMP RETURN (NO CALCULATION YET)
//         * ===================================================== */
//        return new OldTaxCalculationResult();
    }

    private BigDecimal calculateGrossSalaryForFY(
            BigDecimal monthlyGrossSalary,
            LocalDate joiningDate,
            int financialYear
    ) {
        LocalDate fyStart = LocalDate.of(financialYear - 1, 4, 1);
        LocalDate fyEnd   = LocalDate.of(financialYear, 3, 31);


        // Joined after FY → no salary
        if (joiningDate.isAfter(fyEnd)) {
            return BigDecimal.ZERO;
        }

        // Salary starts from max(joiningDate, FY start)
        LocalDate effectiveStart =
                joiningDate.isAfter(fyStart) ? joiningDate : fyStart;

        BigDecimal totalSalary = BigDecimal.ZERO;

        // ---------------- FIRST (JOINING) MONTH ----------------
        YearMonth joinMonth = YearMonth.from(effectiveStart);
        int daysInMonth = joinMonth.lengthOfMonth();

        int payableDays =
                daysInMonth - effectiveStart.getDayOfMonth() + 1;

        BigDecimal perDaySalary =
                monthlyGrossSalary.divide(
                        BigDecimal.valueOf(daysInMonth),
                        2,
                        RoundingMode.HALF_UP
                );

        BigDecimal firstMonthSalary =
                perDaySalary.multiply(BigDecimal.valueOf(payableDays));

        totalSalary = totalSalary.add(firstMonthSalary);

        // ---------------- FULL MONTHS AFTER JOIN MONTH ----------------
        YearMonth startFullMonth = joinMonth.plusMonths(1);
        YearMonth endMonth = YearMonth.from(fyEnd);

        long fullMonths =
                ChronoUnit.MONTHS.between(startFullMonth, endMonth) + 1;

        if (fullMonths > 0) {
            totalSalary =
                    totalSalary.add(
                            monthlyGrossSalary.multiply(
                                    BigDecimal.valueOf(fullMonths)
                            )
                    );
        }

        return totalSalary.setScale(2, RoundingMode.HALF_UP);
    }



    private int calculateRemainingMonths(
            LocalDate joiningDate,
            int financialYear
    ) {

        LocalDate fyStart = LocalDate.of(financialYear, 4, 1);
        LocalDate fyEnd   = LocalDate.of(financialYear + 1, 3, 31);

        // If joined after FY end → no months
        if (joiningDate.isAfter(fyEnd)) {
            return 0;
        }

        // Salary starts from max(joiningDate, FY start)
        LocalDate effectiveStart =
                joiningDate.isAfter(fyStart)
                        ? joiningDate
                        : fyStart;

        if (effectiveStart.isAfter(fyEnd)) {
            return 0;
        }

        return (int) ChronoUnit.MONTHS.between(
                YearMonth.from(effectiveStart),
                YearMonth.from(fyEnd)
        ) + 1;
    }




    static class TaxSlab {
        private BigDecimal from;
        private BigDecimal to;
        private BigDecimal rate;

        public BigDecimal getFrom() { return from; }
        public BigDecimal getTo() { return to; }
        public BigDecimal getRate() { return rate; }
    }

    private int countMonths(String fromMonth, String toMonth) {
        YearMonth start = YearMonth.parse(fromMonth);
        YearMonth end = YearMonth.parse(toMonth);
        return (int) ChronoUnit.MONTHS.between(start, end) + 1;
    }


    @Transactional
    public OldTaxCalculationResult  calculateAndSaveOldTax(
            String organizationId,
            String employeeId,
            Integer financialYear
    ) {

        String method = "calculateAndSaveOldTax";

        log.info("[{}] 🚀 START | orgId={}, employeeId={}, financialYear={}",
                method, organizationId, employeeId, financialYear);

        /* ================= CALCULATION ================= */
        log.info("[{}] 🧮 Triggering OLD TAX calculation", method);

        OldTaxCalculationResult result =
                calculateOldTax(organizationId, employeeId, financialYear);

        log.info("[{}] 🧾 Calculation complete | grossIncome={}, taxableIncome={}, finalTax={}",
                method,
                result.getGrossIncome(),
                result.getTaxableIncome(),
                result.getTaxPayable());

        /* ================= ENTITY BUILD ================= */
        log.info("[{}] 🏗️ Building OldTaxCalculation entity", method);

        OldTaxCalculation entity = new OldTaxCalculation();

        entity.setOrganizationId(organizationId);
        entity.setEmployeeId(employeeId);
        entity.setFinancialYear(financialYear);

        entity.setGrossIncome(result.getGrossIncome());
        entity.setIncomeFromSalary(result.getIncomeFromSalary());
        entity.setStandardDeduction(result.getStandardDeduction());

        entity.setIncomeFromHouseProperty(result.getIncomeFromHouseProperty());
        entity.setIncomeFromOtherSources(result.getIncomeFromOtherSources());

        entity.setHraExemption(result.getHraExemption());
        entity.setTotalChapterVIA(result.getTotalChapterVIA());

        entity.setTaxableIncome(result.getTaxableIncome());
        entity.setTaxBeforeRebate(result.getTaxBeforeRebate());
        entity.setRebateAmount(result.getRebateAmount());
        entity.setSurcharge(result.getSurcharge());
        entity.setCess(result.getCess());
        entity.setTaxPayable(result.getTaxPayable());


        entity.setRemainingMonths(result.getRemainingMonths());
        entity.setTaxPerMonth(result.getTaxPerMonth());

        /* ================= SECTION-WISE ================= */
        log.info("[{}] 📑 Mapping section-wise deductions | count={}",
                method,
                result.getSectionWiseDeductions().size());

        List<OldTaxSectionDeduction> deductions = new ArrayList<>();

        result.getSectionWiseDeductions().forEach((code, amount) -> {
            log.info("[{}] ➕ Section Deduction | section={} | amount={}",
                    method, code, amount);

            OldTaxSectionDeduction d = new OldTaxSectionDeduction();
            d.setSectionCode(code);
            d.setAmount(amount);
            d.setOldTaxCalculation(entity);
            deductions.add(d);
        });

        entity.setSectionWiseDeductions(deductions);

        /* ================= UPSERT ================= */
        oldTaxCalculationRepository
                .findByOrganizationIdAndEmployeeIdAndFinancialYear(
                        organizationId, employeeId, financialYear)
                .ifPresent(existing -> {
                    entity.setId(existing.getId());
                    log.info("[{}] 🔁 Existing OLD TAX found | updating id={}",
                            method, existing.getId());
                });

        /* ================= SAVE ================= */
        OldTaxCalculation saved = oldTaxCalculationRepository.save(entity);

        log.info("[{}] ✅ OLD TAX SAVED SUCCESSFULLY | taxId={} | emp={} | year={}",
                method, saved.getId(), employeeId, financialYear);

        /* ================= TAX REGIME UPDATE ================= */
        log.info("[{}] 🔄 Updating declaration tax regime to OLD", method);

        log.info("[{}] 🏁 END", method);

        return result;

    }


//    @Override
//    @Transactional(readOnly = true)
//    public OldTaxCalculationResult calculateOldRegimeTaxUsingPOI(
//            String organizationId,
//            String employeeId,
//            Integer financialYear
//    ) {
//
//        final String method = "calculateOldRegimeTaxUsingPOI";
//
//        log.info("[{}] ▶ START | orgId={} empId={} fy={}",
//                method, organizationId, employeeId, financialYear);
//
//        /* =====================================================
//         * STEP 1: FETCH EMPLOYEE
//         * ===================================================== */
//        BasicDetails employee =
//                basicDetailsRepository.findByEmployeeId(employeeId)
//                        .orElseThrow(() -> {
//                            log.error("[{}] ❌ Employee not found | empId={}", method, employeeId);
//                            return new RuntimeException("Employee not found");
//                        });
//
//        Organization organization = employee.getOrganization();
//
//        if (organization == null) {
//            throw new RuntimeException("Organization not linked with employee");
//        }
//
//        /* =====================================================
//         * STEP 2: FETCH IT DECLARATION (CONTEXT ONLY)
//         * ===================================================== */
//        EmployeeInvestmentDeclaration declaration =
//                declarationRepo.findByOrganizationAndEmployeeAndFiscalYear(
//                                organization, employee, financialYear
//                        )
//                        .orElseThrow(() ->
//                                new RuntimeException("IT Declaration not found"));
//
//        /* =====================================================
//         * STEP 3: FETCH APPROVED POI (SOURCE OF TRUTH)
//         * ===================================================== */
//        EmployeeProofOfInvestment poi =
//                poiRepository
//                        .findByOrganization_IdAndEmployee_IdAndFiscalYear(
//                                organization.getId(),
//                                employee.getId(),
//                                financialYear
//                        )
//                        .orElseThrow(() -> {
//                            log.error(
//                                    "[{}] ❌ POI not found | orgId={} empId={} fy={}",
//                                    method,
//                                    organization.getId(),
//                                    employee.getId(),
//                                    financialYear
//                            );
//                            return new RuntimeException("Proof of Investment not found");
//                        });
//
//
//
//        log.info("[{}] ✅ POI fetched | poiId={}", method, poi.getId());
//
//        /* =====================================================
//         * STEP 4: SALARY (CTC BASED – SAME AS EARLIER)
//         * ===================================================== */
//        BigDecimal grossSalary = BigDecimal.ZERO;
//
//        List<CtcStructure> ctcList =
//                ctcStructureRepository
//                        .findByOrganization_OrganizationIdAndEmployee_EmployeeId(
//                                organization.getOrganizationId(),
//                                employee.getEmployeeId()
//                        );
//
//        if (!ctcList.isEmpty()) {
//            CtcStructure ctc = ctcList.get(0);
//
//            grossSalary =
//                    ctc.getEarnings().stream()
//                            .filter(EmployeeEarning::getEnabled)
//                            .map(e -> BigDecimal.valueOf(
//                                    e.getAmount() != null ? e.getAmount() : 0))
//                            .reduce(BigDecimal.ZERO, BigDecimal::add)
//                            .multiply(BigDecimal.valueOf(12));
//        }
//
//        log.info("[{}] ✅ Gross Salary = {}", method, grossSalary);
//
//        /* =====================================================
//         * STEP 5: CHAPTER VI-A FROM POI (🔥 MAIN CHANGE 🔥)
//         * ===================================================== */
//
//        BigDecimal totalChapterVIA = BigDecimal.ZERO;
//
//        // Fetch master
//        List<Section6AItemMaster> masters =
//                section6AItemRepo.findByIsActiveTrue();
//
//        Map<Long, Section6AItemMaster> masterMap =
//                masters.stream()
//                        .collect(Collectors.toMap(
//                                Section6AItemMaster::getId,
//                                m -> m
//                        ));
//
//        BigDecimal total80C = BigDecimal.ZERO;
//        BigDecimal self80D = BigDecimal.ZERO;
//        BigDecimal parents80D = BigDecimal.ZERO;
//        BigDecimal otherSections = BigDecimal.ZERO;
//
//        for (EmployeePOIItem item : poi.getPoiItems()) {
//
//            if (item.getApprovedAmount() == null
//                    || item.getApprovedAmount().compareTo(BigDecimal.ZERO) <= 0) {
//                continue;
//            }
//
//            Section6AItemMaster master =
//                    masterMap.get(item.getSection6aItemId());
//
//            if (master == null) continue;
//
//            BigDecimal amount = item.getApprovedAmount();
//
//            /* ---------- 80C ---------- */
//            if (Boolean.TRUE.equals(master.getIs80c())) {
//                total80C = total80C.add(amount);
//            }
//
//            /* ---------- 80D ---------- */
//            else if (Boolean.TRUE.equals(master.getIs80d())) {
//
//                String type =
//                        master.getType() != null
//                                ? master.getType().toUpperCase()
//                                : "";
//
//                if (type.contains("SELF")) {
//                    self80D = self80D.add(amount);
//                }
//                if (type.contains("PARENTS")) {
//                    parents80D = parents80D.add(amount);
//                }
//            }
//
//            /* ---------- OTHER SECTIONS ---------- */
//            else if (Boolean.TRUE.equals(master.getIsOtherSection())) {
//
//                BigDecimal allowed =
//                        master.getMaxLimit() != null
//                                ? amount.min(master.getMaxLimit())
//                                : amount;
//
//                otherSections = otherSections.add(allowed);
//            }
//        }
//
//        // Apply statutory caps
//        BigDecimal allowed80C = total80C.min(BigDecimal.valueOf(150000));
//        BigDecimal allowed80DSelf = self80D.min(BigDecimal.valueOf(25000));
//        BigDecimal allowed80DParents = parents80D.min(BigDecimal.valueOf(50000));
//
//        totalChapterVIA =
//                allowed80C
//                        .add(allowed80DSelf)
//                        .add(allowed80DParents)
//                        .add(otherSections);
//
//        log.info(
//                "[{}] 🎯 Chapter VI-A | 80C={} | 80D={} | other={} | total={}",
//                method,
//                allowed80C,
//                allowed80DSelf.add(allowed80DParents),
//                otherSections,
//                totalChapterVIA
//        );
//
//        /* =====================================================
//         * STEP 6: TAXABLE INCOME
//         * ===================================================== */
//
//        BigDecimal taxableIncome =
//                grossSalary.subtract(totalChapterVIA);
//
//        if (taxableIncome.compareTo(BigDecimal.ZERO) < 0) {
//            taxableIncome = BigDecimal.ZERO;
//        }
//
//        log.info("[{}] ✅ Taxable Income = {}", method, taxableIncome);
//
//        /* =====================================================
//         * STEP 7: SLAB TAX (OLD REGIME)
//         * ===================================================== */
//
//        TaxSlabMaster slabMaster =
//                taxSlabRepo.findByTaxRegimeAndIsActiveTrue("OLD")
//                        .stream()
//                        .findFirst()
//                        .orElseThrow(() ->
//                                new RuntimeException("Old tax slab not found"));
//
//        ObjectMapper mapper = new ObjectMapper();
//        List<TaxSlab> slabs;
//
//        try {
//            slabs = mapper.readValue(
//                    slabMaster.getSlabJson(),
//                    mapper.getTypeFactory()
//                            .constructCollectionType(List.class, TaxSlab.class)
//            );
//        } catch (Exception e) {
//            throw new RuntimeException("Invalid slab JSON", e);
//        }
//
//        BigDecimal taxBeforeRebate = BigDecimal.ZERO;
//
//        for (TaxSlab slab : slabs) {
//
//            if (taxableIncome.compareTo(slab.getFrom()) < 0) continue;
//
//            BigDecimal upper =
//                    slab.getTo() != null ? slab.getTo() : taxableIncome;
//
//            BigDecimal slabIncome =
//                    taxableIncome.min(upper)
//                            .subtract(slab.getFrom())
//                            .add(BigDecimal.ONE);
//
//            if (slabIncome.compareTo(BigDecimal.ZERO) <= 0) continue;
//
//            BigDecimal slabTax =
//                    slabIncome
//                            .multiply(slab.getRate())
//                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
//
//            taxBeforeRebate = taxBeforeRebate.add(slabTax);
//        }
//
//        /* =====================================================
//         * STEP 8: BUILD RESULT
//         * ===================================================== */
//
//        OldTaxCalculationResult result = new OldTaxCalculationResult();
//
//        result.setGrossIncome(grossSalary);
//        result.setTotalChapterVIA(totalChapterVIA);
//        result.setTaxableIncome(taxableIncome);
//        result.setTaxBeforeRebate(taxBeforeRebate);
//        result.setTaxPayable(taxBeforeRebate);
//
//        log.info(
//                "[{}] ✅ FINAL | gross={} | chapterVIA={} | taxable={} | tax={}",
//                method,
//                grossSalary,
//                totalChapterVIA,
//                taxableIncome,
//                taxBeforeRebate
//        );
//
//        return result;
//    }


//    @Override
//    @Transactional
//    public OldTaxCalculationResult calculateOldRegimeTaxUsingPOI(
//            String organizationId,
//            String employeeId,
//            Integer financialYear
//    ) {
//
//        String method = "calculateOldRegimeTaxUsingPOI";
//
//        log.info("[{}] 🚀 START | orgId={}, empId={}, fy={}",
//                method, organizationId, employeeId, financialYear);
//
//        /* =====================================================
//         * STEP 1: FETCH EMPLOYEE
//         * ===================================================== */
//        BasicDetails employee = basicDetailsRepository.findByEmployeeId(employeeId)
//                .orElseThrow(() -> new RuntimeException("Employee not found"));
//
//        Organization organization = employee.getOrganization();
//        if (organization == null) {
//            throw new RuntimeException("Organization not linked with employee");
//        }
//
//        /* =====================================================
//         * STEP 2: FETCH DECLARATION & CHECK TAX REGIME
//         * ===================================================== */
//        EmployeeInvestmentDeclaration declaration =
//                declarationRepo.findByOrganizationAndEmployeeAndFiscalYear(
//                                organization, employee, financialYear
//                        )
//                        .orElseThrow(() ->
//                                new RuntimeException("IT Declaration not found"));
//
//        if (!"OLD".equalsIgnoreCase(declaration.getTaxRegime())) {
//            throw new RuntimeException(
//                    "POI recalculation allowed only for OLD tax regime"
//            );
//        }
//
//        /* =====================================================
//         * STEP 3: FETCH APPROVED POI
//         * ===================================================== */
//        EmployeeProofOfInvestment poi =
//                poiRepository
//                        .findByOrganization_IdAndEmployee_IdAndFiscalYear(
//                                organization.getId(),
//                                employee.getId(),
//                                financialYear
//                        )
//                        .orElseThrow(() ->
//                                new RuntimeException("Approved POI not found"));
//
//        /* =====================================================
//         * STEP 4: CALCULATE GROSS SALARY (ANNUAL)
//         * ===================================================== */
//        BigDecimal grossSalary = BigDecimal.ZERO;
//
//        List<CtcStructure> ctcList =
//                ctcStructureRepository
//                        .findByOrganization_OrganizationIdAndEmployee_EmployeeId(
//                                organization.getOrganizationId(),
//                                employee.getEmployeeId()
//                        );
//
//        if (!ctcList.isEmpty()) {
//            CtcStructure ctc = ctcList.get(0);
//
//            grossSalary =
//                    ctc.getEarnings().stream()
//                            .filter(EmployeeEarning::getEnabled)
//                            .map(e -> BigDecimal.valueOf(
//                                    e.getAmount() != null ? e.getAmount() : 0))
//                            .reduce(BigDecimal.ZERO, BigDecimal::add)
//                            .multiply(BigDecimal.valueOf(12));
//        }
//
//        /* =====================================================
//         * STEP 5: CHAPTER VI-A FROM POI
//         * ===================================================== */
//        BigDecimal total80C = BigDecimal.ZERO;
//        BigDecimal self80D = BigDecimal.ZERO;
//        BigDecimal parents80D = BigDecimal.ZERO;
//        BigDecimal otherSections = BigDecimal.ZERO;
//
//        Map<Long, Section6AItemMaster> masterMap =
//                section6AItemRepo.findByIsActiveTrue()
//                        .stream()
//                        .collect(Collectors.toMap(
//                                Section6AItemMaster::getId,
//                                m -> m
//                        ));
//
//        for (EmployeePOIItem item : poi.getPoiItems()) {
//
//            if (item.getApprovedAmount() == null
//                    || item.getApprovedAmount().compareTo(BigDecimal.ZERO) <= 0)
//                continue;
//
//            Section6AItemMaster master =
//                    masterMap.get(item.getSection6aItemId());
//            if (master == null) continue;
//
//            BigDecimal amount = item.getApprovedAmount();
//
//            if (Boolean.TRUE.equals(master.getIs80c())) {
//                total80C = total80C.add(amount);
//            } else if (Boolean.TRUE.equals(master.getIs80d())) {
//
//                String type =
//                        master.getType() != null
//                                ? master.getType().toUpperCase()
//                                : "";
//
//                if (type.contains("SELF")) self80D = self80D.add(amount);
//                if (type.contains("PARENTS")) parents80D = parents80D.add(amount);
//
//            } else if (Boolean.TRUE.equals(master.getIsOtherSection())) {
//
//                BigDecimal allowed =
//                        master.getMaxLimit() != null
//                                ? amount.min(master.getMaxLimit())
//                                : amount;
//
//                otherSections = otherSections.add(allowed);
//            }
//        }
//
//        BigDecimal totalChapterVIA =
//                total80C.min(BigDecimal.valueOf(150000))
//                        .add(self80D.min(BigDecimal.valueOf(25000)))
//                        .add(parents80D.min(BigDecimal.valueOf(50000)))
//                        .add(otherSections);
//
//        /* =====================================================
//         * STEP 6: TAXABLE INCOME
//         * ===================================================== */
//        BigDecimal taxableIncome =
//                grossSalary.subtract(totalChapterVIA);
//
//        if (taxableIncome.compareTo(BigDecimal.ZERO) < 0) {
//            taxableIncome = BigDecimal.ZERO;
//        }
//
//        /* =====================================================
//         * STEP 7: OLD REGIME SLAB TAX
//         * ===================================================== */
//        TaxSlabMaster slabMaster =
//                taxSlabRepo.findByTaxRegimeAndIsActiveTrue("OLD")
//                        .stream()
//                        .findFirst()
//                        .orElseThrow(() ->
//                                new RuntimeException("Old tax slab not found"));
//
//        ObjectMapper mapper = new ObjectMapper();
//        List<TaxSlab> slabs;
//
//        try {
//            slabs = mapper.readValue(
//                    slabMaster.getSlabJson(),
//                    mapper.getTypeFactory()
//                            .constructCollectionType(List.class, TaxSlab.class)
//            );
//        } catch (Exception e) {
//            throw new RuntimeException("Invalid slab JSON", e);
//        }
//
//        BigDecimal taxBeforeRebate = BigDecimal.ZERO;
//
//        for (TaxSlab slab : slabs) {
//
//            if (taxableIncome.compareTo(slab.getFrom()) < 0) continue;
//
//            BigDecimal upper =
//                    slab.getTo() != null ? slab.getTo() : taxableIncome;
//
//            BigDecimal slabIncome =
//                    taxableIncome.min(upper)
//                            .subtract(slab.getFrom())
//                            .add(BigDecimal.ONE);
//
//            if (slabIncome.compareTo(BigDecimal.ZERO) <= 0) continue;
//
//            taxBeforeRebate =
//                    taxBeforeRebate.add(
//                            slabIncome
//                                    .multiply(slab.getRate())
//                                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
//                    );
//        }
//
//        /* =====================================================
//         * STEP 8: REMAINING MONTHS & TAX PER MONTH
//         * ===================================================== */
//      //  int remainingMonths = taxMonthUtil.getRemainingMonths(financialYear);
//
//        LocalDate today = LocalDate.now();
//
//        /*
//         * Financial Year format assumed:
//         * 2025 means FY 2025-26 (Apr 2025 – Mar 2026)
//         */
//        LocalDate fyEndDate = LocalDate.of(financialYear + 1, Month.MARCH, 31);
//
//// Remaining months INCLUDING current month
//        int remainingMonths = 0;
//
//        if (!today.isAfter(fyEndDate)) {
//            remainingMonths =
//                    (int) ChronoUnit.MONTHS.between(
//                            YearMonth.from(today),
//                            YearMonth.from(fyEndDate)
//                    ) + 1;
//        }
//
//        if (remainingMonths < 0) {
//            remainingMonths = 0;
//        }
//
//
//        BigDecimal taxPerMonth = BigDecimal.ZERO;
//        if (remainingMonths > 0) {
//            taxPerMonth =
//                    taxBeforeRebate.divide(
//                            BigDecimal.valueOf(remainingMonths),
//                            2,
//                            RoundingMode.HALF_UP
//                    );
//        }
//
//        /* =====================================================
//         * STEP 9: BUILD & UPSERT OLD TAX TABLE
//         * ===================================================== */
//        OldTaxCalculation entity = new OldTaxCalculation();
//
//        entity.setOrganizationId(organizationId);
//        entity.setEmployeeId(employeeId);
//        entity.setFinancialYear(financialYear);
//
//        entity.setGrossIncome(grossSalary);
//        entity.setTotalChapterVIA(totalChapterVIA);
//        entity.setTaxableIncome(taxableIncome);
//        entity.setTaxBeforeRebate(taxBeforeRebate);
//        entity.setTaxPayable(taxBeforeRebate);
//
//        entity.setRemainingMonths(remainingMonths);
//        entity.setTaxPerMonth(taxPerMonth);
//
//        oldTaxCalculationRepository
//                .findByOrganizationIdAndEmployeeIdAndFinancialYear(
//                        organizationId, employeeId, financialYear)
//                .ifPresent(existing -> entity.setId(existing.getId()));
//
//        oldTaxCalculationRepository.save(entity);
//
//        /* =====================================================
//         * STEP 10: RETURN RESULT DTO (NEW VALUES)
//         * ===================================================== */
//        OldTaxCalculationResult result = new OldTaxCalculationResult();
//        result.setGrossIncome(grossSalary);
//        result.setTotalChapterVIA(totalChapterVIA);
//        result.setTaxableIncome(taxableIncome);
//        result.setTaxBeforeRebate(taxBeforeRebate);
//        result.setTaxPayable(taxBeforeRebate);
//        result.setRemainingMonths(remainingMonths);
//        result.setTaxPerMonth(taxPerMonth);
//
//        log.info("[{}] ✅ POI TAX RECALC OVERRIDDEN | empId={} | fy={} | taxPerMonth={}",
//                method, employeeId, financialYear, taxPerMonth);
//
//        return result;
//    }


@Override
@Transactional
public OldTaxCalculationResult calculateOldRegimeTaxUsingPOI(
        String organizationId,
        String employeeId,
        Integer financialYear
) {

    log.info("▶ Starting OLD TAX RE-CALCULATION | empId={} | FY={}",
            employeeId, financialYear);

    BigDecimal incomeFromHouseProperty = BigDecimal.ZERO;
    BigDecimal totalChapterVIA = BigDecimal.ZERO;

    /* =====================================================
     * STEP 0: FETCH IT DECLARATION & VALIDATE TAX REGIME
     * ===================================================== */

// Fetch employee entity first
    BasicDetails employeeEntity =
            basicDetailsRepository
                    .findByEmployeeId(employeeId)
                    .orElseThrow(() ->
                            new RuntimeException("Employee not found | empId=" + employeeId)
                    );

// Fetch organization from employee (single source of truth)
    Organization organizationEntity = employeeEntity.getOrganization();

    if (organizationEntity == null) {
        throw new RuntimeException("Organization not found for employee | empId=" + employeeId);
    }

// Fetch IT Declaration using ENTITY references (NOT IDs)
    EmployeeInvestmentDeclaration declaration =
            declarationRepo
                    .findByOrganizationAndEmployeeAndFiscalYear(
                            organizationEntity,
                            employeeEntity,
                            financialYear
                    )
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "IT Declaration not found | empId=" + employeeId + " | FY=" + financialYear
                            )
                    );

// 🔴 HARD STOP if tax regime is NOT OLD
    if (!"OLD".equalsIgnoreCase(declaration.getTaxRegime())) {

        log.warn(
                "⛔ OLD TAX RE-CALCULATION SKIPPED | empId={} | FY={} | taxRegime={}",
                employeeId,
                financialYear,
                declaration.getTaxRegime()
        );

        throw new IllegalStateException(
                "Old tax re-calculation is allowed only when tax regime is OLD"
        );
    }

    log.info(
            "✅ Tax regime validated for OLD re-calculation | empId={} | FY={}",
            employeeId,
            financialYear
    );

    /* =====================================================
     * STEP 1: FETCH EMPLOYEE
     * ===================================================== */
    BasicDetails employee =
            basicDetailsRepository
                    .findByEmployeeIdAndOrganization_OrganizationIdAndIsDeletedFalse(
                            employeeId,
                            organizationId
                    )
                    .orElseThrow(() ->
                            new RuntimeException("Employee not found"));

    Organization organization = employee.getOrganization();
    if (organization == null) {
        throw new RuntimeException("Organization not found for employee");
    }



    /* =====================================================
     * STEP 2: VALIDATE CTC EXISTS
     * ===================================================== */

    validateCtcExistsForFy(
            organizationId,
            employeeId,
            financialYear
    );


    /* =====================================================
     * STEP 3: CHECK REVISION
     * ===================================================== */

    boolean hasRevision =
            hasSalaryRevisionInCurrentFY(
                    organizationId,
                    employeeId,
                    financialYear
            );


    if (hasRevision) {

        log.info(
                "🔁 Revision detected → Redirecting to revised POI tax calculation"
        );

        return calculateOldTaxWithRevisedSalaryAndApprovedPOI(
                organizationId,
                employeeId,
                financialYear
        );
    }




    /* =====================================================
     * STEP 2: FETCH APPROVED & CONSIDERED POI (SOURCE OF TRUTH)
     * ===================================================== */
    EmployeeProofOfInvestment poi =
            poiRepository
                    .findByOrganization_IdAndEmployee_IdAndFiscalYearAndStatus(
                            organization.getId(),
                            employee.getId(),
                            financialYear,
                            PayRunStatus.APPROVED
                    )
                    .orElseThrow(() ->
                            new RuntimeException("Approved POI not found"));

    /* =====================================================
     * STEP 3: FETCH CTC & CALCULATE GROSS SALARY
     * ===================================================== */
    List<CtcStructure> ctcList =
            ctcStructureRepository
                    .findByOrganization_OrganizationIdAndEmployee_EmployeeId(
                            organization.getOrganizationId(),
                            employee.getEmployeeId()
                    );

    if (ctcList == null || ctcList.isEmpty()) {
        throw new RuntimeException("CTC structure not found");
    }

    CtcStructure ctc = ctcList.get(0);

    /* ---- Monthly & Annual Gross Salary ---- */


    /* ---- Determine Joining Date ---- */
    LocalDate joiningDate =
            LocalDate.parse(employee.getDateOfJoining());

    /* ---- Monthly Gross Salary ---- */
    BigDecimal monthlyGrossSalary =
            ctc.getEarnings().stream()
                    .filter(EmployeeEarning::getEnabled)
                    .map(e -> BigDecimal.valueOf(
                            e.getAmount() != null ? e.getAmount() : 0))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

    /* ---- Proper FY Gross Calculation (JOINING DATE AWARE) ---- */
    BigDecimal grossSalary =
            calculateGrossSalaryForFY(
                    monthlyGrossSalary,
                    joiningDate,
                    financialYear
            );

    log.info(
            "✅ Gross Salary Derived (Joining Aware) | monthly={} | annual={}",
            monthlyGrossSalary,
            grossSalary
    );


    /* =====================================================
     * STEP 4: STANDARD DEDUCTION (OLD REGIME)
     * ===================================================== */
    BigDecimal standardDeduction = BigDecimal.ZERO;

    if (grossSalary.compareTo(BigDecimal.ZERO) > 0) {
        standardDeduction = BigDecimal.valueOf(50000);
        if (standardDeduction.compareTo(grossSalary) > 0) {
            standardDeduction = grossSalary;
        }
    }

    BigDecimal salaryAfterStandardDeduction =
            grossSalary.subtract(standardDeduction);



    if (salaryAfterStandardDeduction.compareTo(BigDecimal.ZERO) < 0) {
        salaryAfterStandardDeduction = BigDecimal.ZERO;
    }

    log.info(
            "✅ Standard Deduction Applied | grossSalary={} | stdDeduction={} | salaryAfterStd={}",
            grossSalary,
            standardDeduction,
            salaryAfterStandardDeduction
    );



    /* =====================================================
     * STEP 5: INCOME FROM HOUSE PROPERTY (APPROVED POI)
     * ===================================================== */

    /* ---------- 5A: LET-OUT PROPERTY (APPROVED) ---------- */
    LetOutPropertyRuleMaster letOutRule =
            letOutRuleRepo.findByIsActiveTrueAndTaxRegime("OLD")
                    .orElseThrow(() ->
                            new RuntimeException("Let-Out rule not found"));

    BigDecimal letOutIncome = BigDecimal.ZERO;

    for (EmployeePOIItem item : poi.getPoiItems()) {

        if (item.getStatus() != PayRunStatus.APPROVED) continue;

        if (!"LET_OUT_PROPERTY".equalsIgnoreCase(item.getInvestmentType())) continue;

        BigDecimal propertyIncome =
                item.getApprovedAmount() != null
                        ? item.getApprovedAmount()
                        : BigDecimal.ZERO;

        letOutIncome = letOutIncome.add(propertyIncome);
    }

    log.info(
            "🏠 Let-Out Property Income (Approved) = {}",
            letOutIncome
    );

    /* ---------- 5B: SELF-OCCUPIED HOME LOAN (SECTION 24B) ---------- */
    BigDecimal selfOccupiedLoss = BigDecimal.ZERO;

    Optional<HomeLoanRuleMaster> homeLoanRuleOpt =
            homeLoanRuleRepo.findByIsActiveTrue()
                    .stream()
                    .filter(r ->
                            "24B".equalsIgnoreCase(r.getSectionCode())
                                    && "INTEREST".equalsIgnoreCase(r.getComponent()))
                    .findFirst();

    if (homeLoanRuleOpt.isPresent()) {

        BigDecimal totalApprovedInterest =
                poi.getPoiItems().stream()
                        .filter(i ->
                                i.getStatus() == PayRunStatus.APPROVED
                                        && "HOME_LOAN_INTEREST".equalsIgnoreCase(i.getInvestmentType()))
                        .map(EmployeePOIItem::getApprovedAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalApprovedInterest.compareTo(BigDecimal.ZERO) > 0) {

            BigDecimal allowedInterest =
                    homeLoanRuleOpt.get().getMaxLimit() != null
                            ? totalApprovedInterest.min(homeLoanRuleOpt.get().getMaxLimit())
                            : totalApprovedInterest;

            selfOccupiedLoss = allowedInterest.negate();

            log.info(
                    "🏠 Self-Occupied Property | interestPaid={} | allowed={} | loss={}",
                    totalApprovedInterest,
                    allowedInterest,
                    selfOccupiedLoss
            );
        }
    }

    /* ---------- 5C: COMBINE & APPLY STATUTORY CAP ---------- */
    BigDecimal combinedHousePropertyIncome =
            letOutIncome.add(selfOccupiedLoss);

    /*
     * Section 71(3A):
     * Max loss from House Property that can be set off
     * against salary = ₹2,00,000
     */
    BigDecimal MAX_HP_LOSS = BigDecimal.valueOf(200000);

    if (combinedHousePropertyIncome.compareTo(BigDecimal.ZERO) < 0
            && combinedHousePropertyIncome.abs().compareTo(MAX_HP_LOSS) > 0) {

        log.warn(
                "⚠️ House Property loss capped | original={} | cappedTo=-200000",
                combinedHousePropertyIncome
        );

        combinedHousePropertyIncome = MAX_HP_LOSS.negate();
    }

    incomeFromHouseProperty = combinedHousePropertyIncome;

    log.info(
            "✅ Final Income from House Property (after cap) = {}",
            incomeFromHouseProperty
    );

    /* =====================================================
     * STEP 6: OTHER INCOME (APPROVED POI)
     * ===================================================== */
    BigDecimal totalOtherIncome =
            poi.getPoiItems().stream()
                    .filter(i ->
                            i.getStatus() == PayRunStatus.APPROVED
                                    && "OTHER_INCOME".equalsIgnoreCase(i.getInvestmentType()))
                    .map(EmployeePOIItem::getApprovedAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

    log.info("✅ Other Income (Approved) = {}", totalOtherIncome);

    /* =====================================================
     * STEP 7: CHAPTER VI-A DEDUCTIONS (APPROVED POI)
     * ===================================================== */
    BigDecimal total80C = BigDecimal.ZERO;
    BigDecimal self80D = BigDecimal.ZERO;
    BigDecimal parents80D = BigDecimal.ZERO;
    BigDecimal additionalNps80CCD = BigDecimal.ZERO;
    BigDecimal otherSections = BigDecimal.ZERO;

    Map<Long, Section6AItemMaster> masterMap =
            section6AItemRepo.findByIsActiveTrue()
                    .stream()
                    .collect(Collectors.toMap(
                            Section6AItemMaster::getId,
                            m -> m
                    ));

    for (EmployeePOIItem item : poi.getPoiItems()) {

        if (item.getStatus() != PayRunStatus.APPROVED) continue;
        if (item.getApprovedAmount() == null) continue;

        // 🔴 HRA IS INTENTIONALLY IGNORED IN POI RE-CALCULATION
        if ("HRA".equalsIgnoreCase(item.getInvestmentType())) {

            log.info(
                    "ℹ️ HRA POI item present but ignored in re-calculation | empId={} | FY={} | approvedAmount={}",
                    employeeId,
                    financialYear,
                    item.getApprovedAmount()
            );

            continue;
        }


        Section6AItemMaster master =
                masterMap.get(item.getSection6aItemId());
        if (master == null) continue;

        BigDecimal amount = item.getApprovedAmount();

        // 🔑 IMPORTANT: Use POI item type, NOT master type
        String type =
                item.getInvestmentType() != null
                        ? item.getInvestmentType().toUpperCase()
                        : "";

        /* ---------- 80C ---------- */
        if (Boolean.TRUE.equals(master.getIs80c())) {
            total80C = total80C.add(amount);
        }

        /* ---------- 80D ---------- */
        else if (Boolean.TRUE.equals(master.getIs80d())) {

            if (type.contains("SELF")) {
                self80D = self80D.add(amount);
            } else if (type.contains("PARENTS")) {
                parents80D = parents80D.add(amount);
            }
        }

        /* ---------- 80CCD(1B) – ADDITIONAL NPS ---------- */
        else if ("NPS_ADDITIONAL".equalsIgnoreCase(type)) {

            additionalNps80CCD =
                    additionalNps80CCD
                            .add(amount)
                            .min(BigDecimal.valueOf(50000));
        }

        /* ---------- OTHER SECTIONS ---------- */
        else if (Boolean.TRUE.equals(master.getIsOtherSection())) {

            BigDecimal allowed;

            // 80TTA – Savings Interest
            if ("SAVING_INTEREST".equalsIgnoreCase(type)) {
                allowed = amount.min(BigDecimal.valueOf(10000));
            }
            // 80TTB – Senior Citizen
            else if ("SAVING_INTEREST_SENIOR".equalsIgnoreCase(type)) {
                allowed = amount.min(BigDecimal.valueOf(50000));
            }
            // 80G – Donations
            else if ("DONATION_50".equalsIgnoreCase(type)) {
                allowed = amount.multiply(BigDecimal.valueOf(0.5));
            } else if ("DONATION_100".equalsIgnoreCase(type)) {
                allowed = amount;
            }
            // Default
            else {
                allowed =
                        master.getMaxLimit() != null
                                ? amount.min(master.getMaxLimit())
                                : amount;
            }

            otherSections = otherSections.add(allowed);
        }
    }

    /* ---------- APPLY STATUTORY CAPS ---------- */
    BigDecimal allowed80C =
            total80C.min(BigDecimal.valueOf(150000));

    BigDecimal allowedSelf80D =
            self80D.min(BigDecimal.valueOf(25000));

    BigDecimal allowedParents80D =
            parents80D.min(BigDecimal.valueOf(50000));

    BigDecimal total80D =
            allowedSelf80D.add(allowedParents80D);

    /* ---------- TOTAL CHAPTER VI-A ---------- */
    totalChapterVIA =
            allowed80C
                    .add(total80D)
                    .add(additionalNps80CCD)
                    .add(otherSections);

    log.info(
            "🎯 Chapter VI-A (Approved) | 80C={} | 80D={} | 80CCD(1B)={} | other={} | total={}",
            allowed80C,
            total80D,
            additionalNps80CCD,
            otherSections,
            totalChapterVIA
    );

    /* =====================================================
     * STEP 8: TAXABLE INCOME
     * ===================================================== */
//    BigDecimal grossTotalIncome =
//            grossSalary
//                    .add(incomeFromHouseProperty)
//                    .add(totalOtherIncome);


    /* =====================================================
     * STEP 7A: PREVIOUS EMPLOYMENT (SECTION 192)
     * ===================================================== */

    PreviousEmploymentSummary prevSummary =
            extractPreviousEmploymentSummary(declaration);

    BigDecimal netPreviousIncome =
            prevSummary.getNetPreviousIncome();

    BigDecimal previousEmployerTds =
            prevSummary.getPreviousEmployerTds();

    log.info(
            "✅ Previous Employment Summary | netIncome={} | prevTds={}",
            netPreviousIncome,
            previousEmployerTds
    );




    BigDecimal grossTotalIncome =
            salaryAfterStandardDeduction
                    .add(netPreviousIncome)   // ✅ ADDED
                    .add(incomeFromHouseProperty)
                    .add(totalOtherIncome);



    if (totalChapterVIA.compareTo(grossTotalIncome) > 0) {
        totalChapterVIA = grossTotalIncome;
    }

    BigDecimal taxableIncome =
            grossTotalIncome.subtract(totalChapterVIA);

    if (taxableIncome.compareTo(BigDecimal.ZERO) < 0) {
        taxableIncome = BigDecimal.ZERO;
    }

    log.info(
            "✅ Taxable Income | grossTotalIncome={} | chapterVIA={} | taxableIncome={}",
            grossTotalIncome,
            totalChapterVIA,
            taxableIncome
    );


    /* =====================================================
     * STEP 9: SLAB TAX (OLD REGIME) – CORRECT & AUDIT SAFE
     * ===================================================== */

    TaxSlabMaster slabMaster =
            taxSlabRepo.findByTaxRegimeAndIsActiveTrue("OLD")
                    .stream()
                    .findFirst()
                    .orElseThrow(() ->
                            new RuntimeException("Old tax slab not found"));

    ObjectMapper mapper = new ObjectMapper();
    List<TaxSlab> slabs;

    try {
        slabs = mapper.readValue(
                slabMaster.getSlabJson(),
                mapper.getTypeFactory()
                        .constructCollectionType(List.class, TaxSlab.class));
    } catch (Exception e) {
        throw new RuntimeException("Invalid slab JSON", e);
    }

    BigDecimal taxBeforeRebate = BigDecimal.ZERO;

    for (TaxSlab slab : slabs) {

        BigDecimal slabFrom = slab.getFrom();
        BigDecimal slabTo = slab.getTo(); // null = no upper limit
        BigDecimal slabRate = slab.getRate();

        // Skip slab if income does not cross slab start
        if (taxableIncome.compareTo(slabFrom) <= 0) {
            continue;
        }

        // Effective upper limit
        BigDecimal effectiveUpper =
                slabTo != null
                        ? taxableIncome.min(slabTo)
                        : taxableIncome;

        // Income taxable in this slab
        BigDecimal slabIncome =
                effectiveUpper.subtract(slabFrom);

        if (slabIncome.compareTo(BigDecimal.ZERO) <= 0) {
            continue;
        }

        BigDecimal slabTax =
                slabIncome
                        .multiply(slabRate)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        taxBeforeRebate = taxBeforeRebate.add(slabTax);

        log.info(
                "🧮 Slab {}–{} @ {}% | slabIncome={} | slabTax={}",
                slabFrom,
                slabTo != null ? slabTo : "∞",
                slabRate,
                slabIncome,
                slabTax
        );
    }


    /* =====================================================
     * STEP 9: SECTION 87A REBATE (OLD REGIME ONLY)
     * ===================================================== */

    BigDecimal rebateAmount = BigDecimal.ZERO;

    Section87ARebateRuleMaster rebateRule =
            rebateRuleRepo.findByTaxRegimeAndIsActiveTrue("OLD")
                    .orElse(null);

    if (rebateRule != null
            && rebateRule.getIncomeThreshold() != null
            && rebateRule.getMaxRebateAmount() != null) {

        // Rebate applies ONLY if:
        // 1️⃣ Tax exists
        // 2️⃣ Taxable income <= threshold
        if (taxBeforeRebate.compareTo(BigDecimal.ZERO) > 0
                && taxableIncome.compareTo(rebateRule.getIncomeThreshold()) <= 0) {

            rebateAmount =
                    taxBeforeRebate.min(rebateRule.getMaxRebateAmount());

            log.info(
                    "✅ Section 87A Rebate Applied | taxableIncome={} | taxBeforeRebate={} | rebate={}",
                    taxableIncome,
                    taxBeforeRebate,
                    rebateAmount
            );

        } else {
            log.info(
                    "ℹ️ Section 87A Not Applicable | taxableIncome={} | threshold={} | taxBeforeRebate={}",
                    taxableIncome,
                    rebateRule.getIncomeThreshold(),
                    taxBeforeRebate
            );
        }
    }

// Tax after rebate
    BigDecimal taxAfterRebate =
            taxBeforeRebate.subtract(rebateAmount);

    if (taxAfterRebate.compareTo(BigDecimal.ZERO) < 0) {
        taxAfterRebate = BigDecimal.ZERO;
    }

    log.info("✅ Tax After Rebate = {}", taxAfterRebate);

    /* =====================================================
     * STEP 10: SURCHARGE (OLD REGIME)
     * ===================================================== */

    BigDecimal surchargeAmount = BigDecimal.ZERO;

    if (taxAfterRebate.compareTo(BigDecimal.ZERO) > 0) {

        List<CessSurchargeRuleMaster> rules =
                cessSurchargeRepo.findByIsActiveTrue();

        for (CessSurchargeRuleMaster rule : rules) {

            if (!"SURCHARGE".equalsIgnoreCase(rule.getRuleType())) continue;

            if (rule.getTaxRegime() != null
                    && !"BOTH".equalsIgnoreCase(rule.getTaxRegime())
                    && !"OLD".equalsIgnoreCase(rule.getTaxRegime())) continue;

            BigDecimal from = rule.getIncomeFrom();
            BigDecimal to = rule.getIncomeTo();

            // Apply slab only if taxableIncome fits
            if (from != null && taxableIncome.compareTo(from) < 0) continue;
            if (to != null && taxableIncome.compareTo(to) > 0) continue;

            surchargeAmount =
                    taxAfterRebate
                            .multiply(rule.getRate())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            log.info(
                    "✅ Surcharge Applied | taxableIncome={} | rate={} | surcharge={}",
                    taxableIncome,
                    rule.getRate(),
                    surchargeAmount
            );

            break; // only one surcharge slab applies
        }
    }

    BigDecimal taxAfterSurcharge =
            taxAfterRebate.add(surchargeAmount);

    log.info("✅ Tax After Surcharge = {}", taxAfterSurcharge);

    /* =====================================================
     * STEP 11: HEALTH & EDUCATION CESS (4%)
     * ===================================================== */

    BigDecimal cessAmount = BigDecimal.ZERO;

    if (taxAfterSurcharge.compareTo(BigDecimal.ZERO) > 0) {

        List<CessSurchargeRuleMaster> rules =
                cessSurchargeRepo.findByIsActiveTrue();

        for (CessSurchargeRuleMaster rule : rules) {

            if (!"CESS".equalsIgnoreCase(rule.getRuleType())) continue;

            if (rule.getTaxRegime() != null
                    && !"BOTH".equalsIgnoreCase(rule.getTaxRegime())
                    && !"OLD".equalsIgnoreCase(rule.getTaxRegime())) continue;

            cessAmount =
                    taxAfterSurcharge
                            .multiply(rule.getRate())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            log.info(
                    "✅ Health & Education Cess Applied | rate={} | cess={}",
                    rule.getRate(),
                    cessAmount
            );

            break;
        }
    }

    BigDecimal finalTaxPayable =
            taxAfterSurcharge.add(cessAmount);

    /* =====================================================
     * STEP 12: ADJUST PREVIOUS EMPLOYER TDS (SECTION 192)
     * ===================================================== */

    if (previousEmployerTds != null
            && previousEmployerTds.compareTo(BigDecimal.ZERO) > 0) {

        finalTaxPayable =
                finalTaxPayable.subtract(previousEmployerTds);

        if (finalTaxPayable.compareTo(BigDecimal.ZERO) < 0) {
            finalTaxPayable = BigDecimal.ZERO;
        }

        log.info(
                "✅ Previous Employer TDS Adjusted | prevTds={} | finalTaxAfterAdjustment={}",
                previousEmployerTds,
                finalTaxPayable
        );
    }

    log.info("🎯 FINAL TAX PAYABLE (Old Regime) = {}", finalTaxPayable);


    /* =====================================================
     * STEP 12: BUILD OLD TAX RE-CALCULATION RESULT (POI)
     * ===================================================== */

    OldTaxCalculationResult result = new OldTaxCalculationResult();

    /* ---------- Income ---------- */
    result.setGrossIncome(
            grossTotalIncome.compareTo(BigDecimal.ZERO) < 0
                    ? BigDecimal.ZERO
                    : grossTotalIncome
    );

    /*
     * IMPORTANT FIX:
     * incomeFromSalary shown in output must be GROSS SALARY
     * Standard deduction is shown separately.
     */
    BigDecimal incomeFromSalary = grossSalary;

    result.setIncomeFromSalary(
            incomeFromSalary.compareTo(BigDecimal.ZERO) < 0
                    ? BigDecimal.ZERO
                    : incomeFromSalary
    );

    result.setIncomeFromHouseProperty(incomeFromHouseProperty);



    result.setIncomeFromOtherSources(totalOtherIncome);

    /* ---------- Previous Employment (Section 192) ---------- */
    result.setPreviousEmploymentIncome(
            netPreviousIncome != null ? netPreviousIncome : BigDecimal.ZERO
    );

    result.setTdsByPreviousEmployer(
            previousEmployerTds != null ? previousEmployerTds : BigDecimal.ZERO
    );




    result.setIncomeFromOtherSources(totalOtherIncome);

    /* ---------- Exemptions ---------- */
    /*
     * HRA exemption is NOT recalculated during POI re-calculation.
     */
    BigDecimal hraExemption = BigDecimal.ZERO;
    result.setHraExemption(hraExemption);

    /* ---------- Standard Deduction (EXPLICITLY EXPOSED) ---------- */
    result.setStandardDeduction(standardDeduction);

    /* ---------- Chapter VI-A ---------- */
    result.setTotalChapterVIA(totalChapterVIA);

    /* ---------- Section-wise breakup (UI / Audit) ---------- */

    if (total80C.compareTo(BigDecimal.ZERO) > 0) {
        result.addSectionDeduction(
                "80C",
                total80C.min(BigDecimal.valueOf(150000))
        );
    }

    BigDecimal total80DApproved =
            self80D.min(BigDecimal.valueOf(25000))
                    .add(parents80D.min(BigDecimal.valueOf(50000)));

    if (total80DApproved.compareTo(BigDecimal.ZERO) > 0) {
        result.addSectionDeduction("80D", total80DApproved);
    }

    /* 80CCD(1B) – Additional NPS */
    if (additionalNps80CCD.compareTo(BigDecimal.ZERO) > 0) {
        result.addSectionDeduction("80CCD(1B)", additionalNps80CCD);
    }

    if (otherSections.compareTo(BigDecimal.ZERO) > 0) {
        result.addSectionDeduction("OTHER", otherSections);
    }

    /* Home Loan – Section 24B */
    BigDecimal homeLoanInterestApproved =
            poi.getPoiItems().stream()
                    .filter(i ->
                            i.getStatus() == PayRunStatus.APPROVED
                                    && "HOME_LOAN_INTEREST".equalsIgnoreCase(i.getInvestmentType()))
                    .map(EmployeePOIItem::getApprovedAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (homeLoanInterestApproved.compareTo(BigDecimal.ZERO) > 0) {
        result.addSectionDeduction("24B", homeLoanInterestApproved);
    }

    /* ---------- Tax Calculation ---------- */
    result.setTaxableIncome(
            taxableIncome.compareTo(BigDecimal.ZERO) < 0
                    ? BigDecimal.ZERO
                    : taxableIncome
    );

    /* Slab tax BEFORE rebate */
    result.setTaxBeforeRebate(taxBeforeRebate);

    /* Rebate / Surcharge / Cess */
    result.setRebateAmount(rebateAmount);
    result.setSurcharge(surchargeAmount);
    result.setCess(cessAmount);

    /* Final tax payable */
    result.setTaxPayable(finalTaxPayable);

    /* ---------- Payroll ---------- */
    Integer remainingMonths = 12;
    result.setRemainingMonths(remainingMonths);

    BigDecimal taxPerMonth =
            (remainingMonths != null && remainingMonths > 0)
                    ? finalTaxPayable.divide(
                    BigDecimal.valueOf(remainingMonths),
                    2,
                    RoundingMode.HALF_UP
            )
                    : BigDecimal.ZERO;

    result.setTaxPerMonth(taxPerMonth);

    /* ---------- Final Log ---------- */
    log.info(
            "✅ OLD TAX RE-CAL RESULT | grossIncome={} | chapterVIA={} | taxableIncome={} | slabTax={} | cess={} | finalTax={} | perMonth={}",
            result.getGrossIncome(),
            totalChapterVIA,
            result.getTaxableIncome(),
            taxBeforeRebate,
            cessAmount,
            finalTaxPayable,
            taxPerMonth
    );

    return result;
}


    @Transactional(readOnly = true)
    public OldTaxCalculationResult calculateOldTaxWithRevisedSalary(
            String organizationId,
            String employeeId,
            Integer financialYear
    ) {

        final String method = "calculateOldTaxWithRevisedSalary";
        log.info("[{}] ▶ START | empId={} | FY={}", method, employeeId, financialYear);

        /* =====================================================
         * STEP 2: FETCH BASE DATA
         * ===================================================== */

        // Employee
        BasicDetails employee =
                basicDetailsRepository.findByEmployeeIdAndOrganization_OrganizationIdAndIsDeletedFalse(
                employeeId,
                organizationId
        )
                        .orElseThrow(() -> {
                            log.error("[{}] ❌ Employee not found | empId={}", method, employeeId);
                            return new RuntimeException("Employee not found");
                        });

        // Organization
        Organization organization = employee.getOrganization();
        if (organization == null) {
            log.error("[{}] ❌ Organization not linked | empId={}", method, employeeId);
            throw new RuntimeException("Organization not linked with employee");
        }


        validateCtcExistsForFy(organizationId, employeeId, financialYear);


        // IT Declaration (OLD regime)
        EmployeeInvestmentDeclaration declaration =
                declarationRepo
                        .findByOrganizationAndEmployeeAndFiscalYear(
                                organization,
                                employee,
                                financialYear
                        )
                        .orElse(null);

        if (declaration == null) {
            log.info(
                    "[{}] ℹ️ No IT Declaration found | empId={} | FY={} | Proceeding with DEFAULT OLD tax calculation",
                    method, employeeId, financialYear
            );
        } else {
            log.info(
                    "[{}] ✅ IT Declaration found | declarationId={}",
                    method, declaration.getId()
            );

            declaration.setTaxRegime("OLD");
            declaration.setTaxRegimeFormatted("Old Tax Regime");
        }


        // Fetch all active CTCs (non-deleted, valid effective date)
        List<CtcStructure> ctcList =
                ctcStructureRepository
                        .findByOrganization_OrganizationIdAndEmployee_EmployeeId(
                                organizationId,
                                employeeId
                        )
                        .stream()
                        .filter(ctc -> !Boolean.TRUE.equals(ctc.getDeleted())) // exclude deleted
                        .filter(ctc -> ctc.getEffectiveDate() != null)          // safety
                        .filter(ctc ->
                                !ctc.getEffectiveDate().isAfter(
                                        LocalDate.of(financialYear, 3, 31)
                                )
                        ) // must affect this FY
                        .sorted(Comparator.comparing(CtcStructure::getEffectiveDate))
                        .toList();

        if (ctcList.isEmpty()) {
            log.error("[{}] ❌ No valid CTC found | empId={}", method, employeeId);
            throw new RuntimeException("No valid CTC found for employee");
        }

        /* =====================================================
         * STEP 3: SPLIT FY INTO CTC PERIODS
         * ===================================================== */

        LocalDate fyStart = LocalDate.of(financialYear - 1, 4, 1);
        LocalDate fyEnd = LocalDate.of(financialYear, 3, 31);

        // Holder for CTC periods (we will use this later)
        List<CtcPeriod> ctcPeriods = new ArrayList<>();

        for (int i = 0; i < ctcList.size(); i++) {

            CtcStructure currentCtc = ctcList.get(i);

            // Period start = CTC effective date (month aligned)
            LocalDate periodStart =
                    currentCtc.getEffectiveDate().withDayOfMonth(1);

            // Period end = day before next CTC OR FY end
            LocalDate periodEnd =
                    (i + 1 < ctcList.size())
                            ? ctcList.get(i + 1)
                            .getEffectiveDate()
                            .withDayOfMonth(1)
                            .minusDays(1)
                            : fyEnd;

            // Clamp to FY boundaries
            LocalDate applicableStart =
                    periodStart.isBefore(fyStart) ? fyStart : periodStart;

            LocalDate applicableEnd =
                    periodEnd.isAfter(fyEnd) ? fyEnd : periodEnd;

            // Skip if no overlap
            if (applicableStart.isAfter(applicableEnd)) {
                continue;
            }

            // Calculate months (inclusive)
            int months =
                    (applicableEnd.getYear() - applicableStart.getYear()) * 12
                            + applicableEnd.getMonthValue()
                            - applicableStart.getMonthValue()
                            + 1;

            if (months <= 0) {
                continue;
            }

            // Store period (simple holder)
            CtcPeriod period = new CtcPeriod();
            period.setCtc(currentCtc);
            period.setStartDate(applicableStart);
            period.setEndDate(applicableEnd);
            period.setMonths(months);

            ctcPeriods.add(period);

            log.info(
                    "[{}] 📅 CTC PERIOD | ctcId={} | {} → {} | months={}",
                    method,
                    currentCtc.getId(),
                    applicableStart,
                    applicableEnd,
                    months
            );
        }

        if (ctcPeriods.isEmpty()) {
            throw new RuntimeException("No applicable CTC periods found for FY");
        }


        /* =====================================================
         * STEP 4: DERIVE SALARY COMPONENTS PER CTC PERIOD
         * ===================================================== */

        BigDecimal totalAnnualBasic = BigDecimal.ZERO;
        BigDecimal totalAnnualHraReceived = BigDecimal.ZERO;
        BigDecimal totalAnnualGross = BigDecimal.ZERO;

        for (CtcPeriod period : ctcPeriods) {

            CtcStructure ctc = period.getCtc();
            int months = period.getMonths();

            BigDecimal monthlyBasic = BigDecimal.ZERO;
            BigDecimal monthlyHra = BigDecimal.ZERO;
            BigDecimal monthlyGross = BigDecimal.ZERO;

            if (ctc.getEarnings() != null) {

                for (EmployeeEarning e : ctc.getEarnings()) {

                    if (!Boolean.TRUE.equals(e.getEnabled())
                            || e.getEarning() == null
                            || e.getAmount() == null) {
                        continue;
                    }

                    String earningName =
                            e.getEarning().getEarningName().toUpperCase();

                    BigDecimal amount =
                            BigDecimal.valueOf(e.getAmount());

                    // BASIC
                    if (earningName.contains("BASIC")) {
                        monthlyBasic = monthlyBasic.add(amount);
                    }

                    // HRA
                    if (earningName.contains("HRA")
                            || earningName.contains("HOUSE RENT")) {
                        monthlyHra = monthlyHra.add(amount);
                    }

                    // GROSS (all enabled earnings)
                    monthlyGross = monthlyGross.add(amount);
                }
            }

            // Convert MONTHLY → PERIOD totals
            BigDecimal periodBasic =
                    monthlyBasic.multiply(BigDecimal.valueOf(months));

            BigDecimal periodHra =
                    monthlyHra.multiply(BigDecimal.valueOf(months));

            BigDecimal periodGross =
                    monthlyGross.multiply(BigDecimal.valueOf(months));

            // Accumulate FY totals
            totalAnnualBasic =
                    totalAnnualBasic.add(periodBasic);

            totalAnnualHraReceived =
                    totalAnnualHraReceived.add(periodHra);

            totalAnnualGross =
                    totalAnnualGross.add(periodGross);

            log.info(
                    "[{}] 💰 PERIOD SALARY | ctcId={} | months={} | basic={} | hra={} | gross={}",
                    method,
                    ctc.getId(),
                    months,
                    periodBasic,
                    periodHra,
                    periodGross
            );
        }

        log.info(
                "[{}] ✅ ANNUAL SALARY SUMMARY | basic={} | hra={} | gross={}",
                method,
                totalAnnualBasic,
                totalAnnualHraReceived,
                totalAnnualGross
        );

        /* =====================================================
         * STEP 5A: HRA EXEMPTION (REVISED SALARY)
         * ===================================================== */

        BigDecimal hraExemption = BigDecimal.ZERO;

// Fetch active HRA rule
        HraRuleMaster hraRule =
                hraRuleRepo.findByIsActiveTrue()
                        .orElseThrow(() -> new RuntimeException("Active HRA rule not found"));

        /* ---------------------------------------------------------
         * Apply HRA only if declaration exists
         * --------------------------------------------------------- */
        if (declaration != null
                && Boolean.TRUE.equals(declaration.getIsStayingInRentedHouse())
                && declaration.getHouseRents() != null
                && !declaration.getHouseRents().isEmpty()
                && totalAnnualHraReceived.compareTo(BigDecimal.ZERO) > 0) {

            log.info("[{}] 🏠 Applying HRA exemption (Revised Salary)", method);

            BigDecimal monthlyBasicForRule =
                    totalAnnualBasic.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

            BigDecimal monthlyHraForRule =
                    totalAnnualHraReceived.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

             fyStart = LocalDate.of(financialYear - 1, 4, 1);
             fyEnd   = LocalDate.of(financialYear, 3, 31);

            for (EmployeeInvHouseRent rent : declaration.getHouseRents()) {

                BigDecimal rentPaid = rent.getAmountPerMonth();

                // Rule 1: Actual HRA received
                BigDecimal rule1 = monthlyHraForRule;

                // Rule 2: Rent – % of Basic
                BigDecimal tenPercentBasic =
                        monthlyBasicForRule.multiply(
                                BigDecimal.valueOf(hraRule.getRentMinusBasicPercentage())
                                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                        );

                BigDecimal rule2 = rentPaid.subtract(tenPercentBasic);
                if (rule2.compareTo(BigDecimal.ZERO) < 0) {
                    rule2 = BigDecimal.ZERO;
                }

                // Rule 3: % of Basic
                BigDecimal applicablePercent =
                        Boolean.TRUE.equals(rent.getIsMetro())
                                ? BigDecimal.valueOf(hraRule.getMetroPercentageOfBasic())
                                : BigDecimal.valueOf(hraRule.getNonMetroPercentageOfBasic());

                BigDecimal rule3 =
                        monthlyBasicForRule.multiply(
                                applicablePercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                        );

                BigDecimal monthlyExemption =
                        rule1.min(rule2).min(rule3).min(rentPaid);

                // Month calculation
                YearMonth fromYm = YearMonth.parse(rent.getFromMonth());
                YearMonth toYm   = YearMonth.parse(rent.getToMonth());

                LocalDate rentStart = fromYm.atDay(1);
                LocalDate rentEnd   = toYm.atEndOfMonth();

                LocalDate effectiveStart =
                        rentStart.isBefore(fyStart) ? fyStart : rentStart;

                LocalDate effectiveEnd =
                        rentEnd.isAfter(fyEnd) ? fyEnd : rentEnd;

                int months = 0;
                if (!effectiveStart.isAfter(effectiveEnd)) {
                    months =
                            (int) ChronoUnit.MONTHS.between(
                                    YearMonth.from(effectiveStart),
                                    YearMonth.from(effectiveEnd)
                            ) + 1;
                }

                if (months > 0) {
                    hraExemption =
                            hraExemption.add(
                                    monthlyExemption.multiply(BigDecimal.valueOf(months))
                            );
                }
            }
        } else {
            log.info(
                    "[{}] ℹ️ Skipping HRA — No IT declaration or rent data present",
                    method
            );
        }


// Safety cap — cannot exceed actual HRA received
        if (hraExemption.compareTo(totalAnnualHraReceived) > 0) {
            hraExemption = totalAnnualHraReceived;
        }

        log.info(
                "[{}] ✅ HRA Exemption (Revised Salary) = {}",
                method,
                hraExemption
        );

        /* =====================================================
         * STEP 5B: STANDARD DEDUCTION (OLD REGIME)
         * ===================================================== */

        BigDecimal standardDeduction = BigDecimal.ZERO;

        if (totalAnnualGross.compareTo(BigDecimal.ZERO) > 0) {
            standardDeduction = BigDecimal.valueOf(50000);

            if (standardDeduction.compareTo(totalAnnualGross) > 0) {
                standardDeduction = totalAnnualGross;
            }
        }

        log.info(
                "[{}] ✅ Standard Deduction applied = {}",
                method,
                standardDeduction
        );


        /* =====================================================
         * STEP 5C: CHAPTER VI-A DEDUCTIONS (80C, 80D, OTHERS)
         * ===================================================== */

// 🔁 Always reset before calculation
        BigDecimal totalChapterVIA = BigDecimal.ZERO;

// Fetch active Section 6A master items
        List<Section6AItemMaster> section6AMasters =
                section6AItemRepo.findByIsActiveTrue();

// Map master by ID
        Map<Long, Section6AItemMaster> section6AMasterMap =
                section6AMasters.stream()
                        .collect(Collectors.toMap(
                                Section6AItemMaster::getId,
                                m -> m
                        ));

        /* ---------- SECTION 80C (AGGREGATE CAP ₹1,50,000) ---------- */
        BigDecimal total80CClaimed = BigDecimal.ZERO;

        /* ---------- SECTION 80D (SEPARATE CAPS) ---------- */
        BigDecimal selfFamily80D = BigDecimal.ZERO;
        BigDecimal parents80D = BigDecimal.ZERO;

// To avoid duplicate 80D entries
        Set<Long> processed80DItems = new HashSet<>();

        /* ---------- 80CCD(1B) – ADDITIONAL NPS (SEPARATE ₹50,000) ---------- */
        BigDecimal additionalNps80CCD = BigDecimal.ZERO;

        /* ---------- OTHER SECTIONS ---------- */
        BigDecimal otherSectionTotal = BigDecimal.ZERO;

        if (declaration != null
                && declaration.getSection6aDeclarations() != null
                && !declaration.getSection6aDeclarations().isEmpty()) {


            for (EmployeeInvSection6A inv : declaration.getSection6aDeclarations()) {

                if (inv.getAmount() == null) continue;

                Section6AItemMaster master =
                        section6AMasterMap.get(inv.getSection6aItemId());

                if (master == null) continue;

                BigDecimal amount = inv.getAmount();

                // ✅ USE DECLARATION TYPE (NOT MASTER TYPE)
                String type =
                        inv.getType() != null
                                ? inv.getType().toUpperCase()
                                : "";

                /* ---------- SECTION 80C ---------- */
                if (Boolean.TRUE.equals(master.getIs80c())) {
                    total80CClaimed = total80CClaimed.add(amount);
                }

                /* ---------- SECTION 80D ---------- */
                else if (Boolean.TRUE.equals(master.getIs80d())) {

                    // Prevent duplicate 80D entries
                    if (!processed80DItems.add(inv.getSection6aItemId())) {
                        log.warn(
                                "[{}] ⚠️ Duplicate 80D entry ignored | itemId={}",
                                method,
                                inv.getSection6aItemId()
                        );
                        continue;
                    }

                    if (type.contains("SELF")) {
                        selfFamily80D = selfFamily80D.add(amount);
                    }
                    else if (type.contains("PARENTS")) {
                        parents80D = parents80D.add(amount);
                    }
                }

                /* ---------- 80CCD(1B) – ADDITIONAL NPS ---------- */
                else if ("NPS_ADDITIONAL".equals(type)) {

                    additionalNps80CCD =
                            additionalNps80CCD
                                    .add(amount)
                                    .min(BigDecimal.valueOf(50000));
                }

                /* ---------- OTHER SECTIONS ---------- */
                else if (Boolean.TRUE.equals(master.getIsOtherSection())) {

                    BigDecimal allowed;

                    // 80TTA – Savings Interest
                    if ("SAVING_INTEREST".equals(type)) {
                        allowed = amount.min(BigDecimal.valueOf(10000));
                    }
                    // 80TTB – Senior Citizen Savings Interest
                    else if ("SAVING_INTEREST_SENIOR".equals(type)) {
                        allowed = amount.min(BigDecimal.valueOf(50000));
                    }
                    // 80G – Donations
                    else if ("DONATION_50".equals(type)) {
                        allowed = amount.multiply(BigDecimal.valueOf(0.5));
                    }
                    else if ("DONATION_100".equals(type)) {
                        allowed = amount;
                    }
                    // Other sections (80E, etc.)
                    else {
                        allowed =
                                master.getMaxLimit() != null
                                        ? amount.min(master.getMaxLimit())
                                        : amount;
                    }

                    otherSectionTotal = otherSectionTotal.add(allowed);

                    log.info(
                            "[{}] ✅ Other Section applied | type={} | claimed={} | allowed={}",
                            method,
                            type,
                            amount,
                            allowed
                    );

                }

            }


        }
        else {
            log.info(
                    "[{}] ℹ️ No IT declaration present — Chapter VI-A deductions skipped",
                    method
            );
        }



        /* ---------- APPLY CAPS ---------- */

// 80C cap
        BigDecimal allowed80C =
                total80CClaimed.min(BigDecimal.valueOf(150000));

// 80D caps
        BigDecimal allowedSelf80D =
                selfFamily80D.min(BigDecimal.valueOf(25000));

        BigDecimal allowedParents80D =
                parents80D.min(BigDecimal.valueOf(50000));

        BigDecimal total80D =
                allowedSelf80D.add(allowedParents80D);

        /* ---------- TOTAL CHAPTER VI-A ---------- */
        totalChapterVIA =
                allowed80C
                        .add(total80D)
                        .add(additionalNps80CCD)
                        .add(otherSectionTotal);

        log.info(
                "[{}] 🎯 Chapter VI-A Summary | 80C={} | 80D={} | 80CCD(1B)={} | other={} | total={}",
                method,
                allowed80C,
                total80D,
                additionalNps80CCD,
                otherSectionTotal,
                totalChapterVIA
        );

        /* =====================================================
         * STEP 5D: INCOME FROM SALARY (POST EXEMPTIONS)
         * ===================================================== */

// Salary after HRA
        BigDecimal salaryAfterHra =
                totalAnnualGross.subtract(hraExemption);

        if (salaryAfterHra.compareTo(BigDecimal.ZERO) < 0) {
            salaryAfterHra = BigDecimal.ZERO;
        }

// Apply standard deduction
        BigDecimal incomeFromSalary =
                salaryAfterHra.subtract(standardDeduction);

        if (incomeFromSalary.compareTo(BigDecimal.ZERO) < 0) {
            incomeFromSalary = BigDecimal.ZERO;
        }

        log.info(
                "[{}] ✅ Income From Salary | gross={} | hra={} | stdDeduction={} | final={}",
                method,
                totalAnnualGross,
                hraExemption,
                standardDeduction,
                incomeFromSalary
        );


        /* =====================================================
         * STEP 6A: GROSS TOTAL INCOME
         * ===================================================== */

        BigDecimal incomeFromHouseProperty = BigDecimal.ZERO;
        BigDecimal totalOtherIncome = BigDecimal.ZERO;


        /* =====================================================
         * STEP-5: INCOME FROM HOUSE PROPERTY (LET-OUT)
         * ===================================================== */

// Fetch active Let-Out Property rule (OLD regime)
        LetOutPropertyRuleMaster letOutRule =
                letOutRuleRepo.findByIsActiveTrueAndTaxRegime("OLD")
                        .orElseThrow(() ->
                                new RuntimeException("Active Let-Out Property rule not found"));

// Final income from ALL house properties
        BigDecimal totalLetOutIncome = BigDecimal.ZERO;

        if (declaration != null
                && declaration.getLetOutProperties() != null
                && !declaration.getLetOutProperties().isEmpty()) {


            for (EmployeeInvLetOutProperty property : declaration.getLetOutProperties()) {

                BigDecimal propertyIncome;

                /*
                 * UI ALREADY SENDS netIncomeLoss (FINAL VALUE)
                 * If present, ALWAYS trust UI and skip recalculation
                 */
                if (property.getNetIncomeLoss() != null) {

                    propertyIncome = property.getNetIncomeLoss();

                } else {

                    // Manual calculation (ONLY when UI doesn't send it)
                    BigDecimal annualRent = BigDecimal.ZERO;
                    BigDecimal municipalTax = BigDecimal.ZERO;
                    BigDecimal loanInterest = BigDecimal.ZERO;

                    if (property.getPropertyDetails() != null) {
                        for (EmployeeInvLetOutPropertyDetail detail : property.getPropertyDetails()) {

                            if (detail.getAmount() == null) continue;

                            switch (detail.getType().toUpperCase()) {

                                case "ANNUAL_RENT":
                                    annualRent = annualRent.add(detail.getAmount());
                                    break;

                                case "MUNICIPAL_TAX":
                                    municipalTax = municipalTax.add(detail.getAmount());
                                    break;

                                case "INTEREST_ON_LOAN":
                                    if (Boolean.TRUE.equals(letOutRule.getHomeLoanInterestAllowed())) {
                                        loanInterest = loanInterest.add(detail.getAmount());
                                    }
                                    break;

                                default:
                                    break;
                            }
                        }
                    }

                    // Compute NAV
                    BigDecimal netAnnualValue = annualRent.subtract(municipalTax);
                    if (netAnnualValue.compareTo(BigDecimal.ZERO) < 0) {
                        netAnnualValue = BigDecimal.ZERO;
                    }

                    // Standard deduction @ 30%
                    BigDecimal stdDeduction = netAnnualValue
                            .multiply(BigDecimal.valueOf(30))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                    // Final Let-Out income
                    propertyIncome = netAnnualValue
                            .subtract(stdDeduction)
                            .subtract(loanInterest);
                }

                // Add to total
                totalLetOutIncome = totalLetOutIncome.add(propertyIncome);

                // Save back for API response
                property.setNetIncomeLoss(propertyIncome);

                log.info("🏠 Let-Out Property | Computed Income = {}", propertyIncome);
            }
        }

        /*
         * Combine SELF-OCCUPIED (24B interest) + LET-OUT
         */
        BigDecimal combinedHPIncome = incomeFromHouseProperty.add(totalLetOutIncome);

        /*
         * Apply OVERALL HOUSE PROPERTY LOSS LIMIT (₹2,00,000)
         */
        BigDecimal maxLossSetOff =
                BigDecimal.valueOf(letOutRule.getMaxLossSetOffAgainstSalary());

        if (combinedHPIncome.compareTo(BigDecimal.ZERO) < 0
                && combinedHPIncome.abs().compareTo(maxLossSetOff) > 0) {

            combinedHPIncome = maxLossSetOff.negate();
        }

        incomeFromHouseProperty = combinedHPIncome;

        log.info("✅ Final Income from House Property (post cap) = {}", incomeFromHouseProperty);





        /* =====================================================
         * STEP-5A: HOME LOAN INTEREST (SECTION 24B – SELF OCCUPIED)
         * ===================================================== */

        BigDecimal homeLoanInterestDeduction = BigDecimal.ZERO;

// Fetch active Section 24B rule
        Optional<HomeLoanRuleMaster> homeLoanRuleOpt =
                homeLoanRuleRepo.findByIsActiveTrue()
                        .stream()
                        .filter(r ->
                                "24B".equalsIgnoreCase(r.getSectionCode()) &&
                                        "INTEREST".equalsIgnoreCase(r.getComponent())
                        )
                        .findFirst();

        if (homeLoanRuleOpt.isPresent()
                && declaration != null
                && Boolean.TRUE.equals(declaration.getIsRepayingSelfOccupiedLoan())
                && declaration.getHomeLoans() != null
                && !declaration.getHomeLoans().isEmpty()) {

            HomeLoanRuleMaster rule = homeLoanRuleOpt.get();

            BigDecimal totalInterestPaid =
                    declaration.getHomeLoans().stream()
                            .map(EmployeeInvHomeLoan::getInterestPaid)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalInterestPaid.compareTo(BigDecimal.ZERO) > 0) {

                homeLoanInterestDeduction =
                        rule.getMaxLimit() != null
                                ? totalInterestPaid.min(rule.getMaxLimit())
                                : totalInterestPaid;

                /*
                 * ✔ Section 24B applies ONLY to self-occupied property
                 * ✔ Income from House Property becomes NEGATIVE
                 */
                incomeFromHouseProperty =
                        incomeFromHouseProperty.subtract(homeLoanInterestDeduction);

                log.info(
                        "✅ Section 24B (Self Occupied) | interestPaid={} | allowed={} | housePropertyIncome={}",
                        totalInterestPaid,
                        homeLoanInterestDeduction,
                        incomeFromHouseProperty
                );
            }
        }





        /* =====================================================
         * HOUSE PROPERTY LOSS SAFETY CAP (₹2,00,000)
         * ===================================================== */

        BigDecimal MAX_HOUSE_PROPERTY_LOSS = BigDecimal.valueOf(200000);

        if (incomeFromHouseProperty.compareTo(BigDecimal.ZERO) < 0) {

            if (incomeFromHouseProperty.abs().compareTo(MAX_HOUSE_PROPERTY_LOSS) > 0) {

                log.warn(
                        "⚠️ House Property loss capped | original={} | cappedTo=-200000",
                        incomeFromHouseProperty
                );

                incomeFromHouseProperty = MAX_HOUSE_PROPERTY_LOSS.negate();
            }
        }

        log.info(
                "✅ Final Income from House Property (after statutory cap) = {}",
                incomeFromHouseProperty
        );


        /* =====================================================
         * STEP-6A (HOME LOAN): 80EE / 80EEA
         * ===================================================== */

// NOTE:
// EmployeeInvHomeLoan does NOT have
// - loanSanctionDate
// - isFirstTimeBuyer
// Hence 80EE / 80EEA CANNOT be applied safely.

        log.info(
                "⚠️ Skipping 80EE / 80EEA deduction — loan sanction date / first-time buyer info not available"
        );


        /* =====================================================
         * STEP 6A-1: PREVIOUS EMPLOYMENT (SECTION 192)
         * ===================================================== */

        PreviousEmploymentSummary prevSummary =
                extractPreviousEmploymentSummary(declaration);

        BigDecimal netPreviousIncome =
                prevSummary.getNetPreviousIncome();

        BigDecimal previousEmployerTds =
                prevSummary.getPreviousEmployerTds();

        log.info(
                "[{}] ✅ Previous Employment | netIncome={} | prevTds={}",
                method,
                netPreviousIncome,
                previousEmployerTds
        );



        BigDecimal grossTotalIncome =
                incomeFromSalary
                        .add(netPreviousIncome != null ? netPreviousIncome : BigDecimal.ZERO)
                        .add(incomeFromHouseProperty != null ? incomeFromHouseProperty : BigDecimal.ZERO)
                        .add(totalOtherIncome != null ? totalOtherIncome : BigDecimal.ZERO);


        if (grossTotalIncome.compareTo(BigDecimal.ZERO) < 0) {
            grossTotalIncome = BigDecimal.ZERO;
        }

        log.info(
                "[{}] ✅ Gross Total Income | salary={} | prevIncome={} | houseProperty={} | otherIncome={} | total={}",
                method,
                incomeFromSalary,
                netPreviousIncome,
                incomeFromHouseProperty,
                totalOtherIncome,
                grossTotalIncome
        );



        /* =====================================================
         * STEP 6B: CHAPTER VI-A SAFETY CAP
         * ===================================================== */

        if (totalChapterVIA.compareTo(grossTotalIncome) > 0) {
            log.warn(
                    "[{}] ⚠️ Chapter VI-A capped | original={} | cappedTo={}",
                    method,
                    totalChapterVIA,
                    grossTotalIncome
            );
            totalChapterVIA = grossTotalIncome;
        }


        /* =====================================================
         * STEP 6C: TAXABLE INCOME
         * ===================================================== */

        BigDecimal taxableIncome =
                grossTotalIncome.subtract(totalChapterVIA);

        if (taxableIncome.compareTo(BigDecimal.ZERO) < 0) {
            taxableIncome = BigDecimal.ZERO;
        }

        log.info(
                "[{}] ✅ Taxable Income | gross={} | chapterVIA={} | taxable={}",
                method,
                grossTotalIncome,
                totalChapterVIA,
                taxableIncome
        );


        /* =====================================================
         * STEP 6D: OLD REGIME TAX SLAB CALCULATION
         * ===================================================== */

// Fetch active OLD regime slab
        TaxSlabMaster slabMaster =
                taxSlabRepo.findByTaxRegimeAndIsActiveTrue("OLD")
                        .stream()
                        .findFirst()
                        .orElseThrow(() ->
                                new RuntimeException("Active OLD tax slab not found")
                        );

// Parse slab JSON
        ObjectMapper objectMapper = new ObjectMapper();
        List<TaxSlab> slabs;

        try {
            slabs = objectMapper.readValue(
                    slabMaster.getSlabJson(),
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, TaxSlab.class)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse tax slab JSON", e);
        }

        BigDecimal totalTaxBeforeRebate = BigDecimal.ZERO;

        for (TaxSlab slab : slabs) {

            BigDecimal slabFrom = slab.getFrom();
            BigDecimal slabTo = slab.getTo(); // null = no upper limit
            BigDecimal slabRate = slab.getRate();

            if (taxableIncome.compareTo(slabFrom) <= 0) {
                continue;
            }

            BigDecimal effectiveUpper =
                    slabTo != null
                            ? slabTo.min(taxableIncome)
                            : taxableIncome;

            BigDecimal taxableInSlab =
                    effectiveUpper.subtract(slabFrom);

            if (taxableInSlab.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal slabTax =
                    taxableInSlab
                            .multiply(slabRate)
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            totalTaxBeforeRebate =
                    totalTaxBeforeRebate.add(slabTax);

            log.info(
                    "[{}] 🧮 Slab {}–{} @ {}% | taxable={} | tax={}",
                    method,
                    slabFrom,
                    slabTo != null ? slabTo : "∞",
                    slabRate,
                    taxableInSlab,
                    slabTax
            );
        }

        log.info(
                "[{}] ✅ Total Tax Before Rebate = {}",
                method,
                totalTaxBeforeRebate
        );


        /* =====================================================
         * STEP 6E: SECTION 87A REBATE
         * ===================================================== */

        BigDecimal rebateAmount = BigDecimal.ZERO;

        Section87ARebateRuleMaster rebateRule =
                rebateRuleRepo
                        .findByTaxRegimeAndIsActiveTrue("OLD")
                        .orElse(null);

        if (rebateRule != null
                && rebateRule.getIncomeThreshold() != null
                && rebateRule.getMaxRebateAmount() != null) {

            if (totalTaxBeforeRebate.compareTo(BigDecimal.ZERO) > 0
                    && taxableIncome.compareTo(rebateRule.getIncomeThreshold()) <= 0) {

                rebateAmount =
                        totalTaxBeforeRebate.min(
                                rebateRule.getMaxRebateAmount()
                        );

                log.info(
                        "[{}] ✅ Section 87A Rebate Applied | rebate={}",
                        method,
                        rebateAmount
                );
            }
        }

        BigDecimal taxAfterRebate =
                totalTaxBeforeRebate.subtract(rebateAmount);

        if (taxAfterRebate.compareTo(BigDecimal.ZERO) < 0) {
            taxAfterRebate = BigDecimal.ZERO;
        }


        /* =====================================================
         * STEP 6F: SURCHARGE
         * ===================================================== */

        BigDecimal surchargeAmount = BigDecimal.ZERO;

        if (taxAfterRebate.compareTo(BigDecimal.ZERO) > 0) {

            for (CessSurchargeRuleMaster rule : cessSurchargeRepo.findByIsActiveTrue()) {

                if (!"SURCHARGE".equalsIgnoreCase(rule.getRuleType())) {
                    continue;
                }

                if (rule.getTaxRegime() != null
                        && !"BOTH".equalsIgnoreCase(rule.getTaxRegime())
                        && !"OLD".equalsIgnoreCase(rule.getTaxRegime())) {
                    continue;
                }

                if (rule.getRate() == null) continue;

                boolean applicable = true;

                if (rule.getIncomeFrom() != null
                        && taxableIncome.compareTo(rule.getIncomeFrom()) < 0) {
                    applicable = false;
                }

                if (rule.getIncomeTo() != null
                        && taxableIncome.compareTo(rule.getIncomeTo()) > 0) {
                    applicable = false;
                }

                if (!applicable) continue;

                surchargeAmount =
                        taxAfterRebate
                                .multiply(rule.getRate())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                break;
            }
        }

        BigDecimal taxAfterSurcharge =
                taxAfterRebate.add(surchargeAmount);



        /* =====================================================
         * STEP 6G: HEALTH & EDUCATION CESS
         * ===================================================== */

        BigDecimal cessAmount = BigDecimal.ZERO;

        if (taxAfterSurcharge.compareTo(BigDecimal.ZERO) > 0) {

            for (CessSurchargeRuleMaster rule : cessSurchargeRepo.findByIsActiveTrue()) {

                if (!"CESS".equalsIgnoreCase(rule.getRuleType())) {
                    continue;
                }

                if (rule.getTaxRegime() != null
                        && !"BOTH".equalsIgnoreCase(rule.getTaxRegime())
                        && !"OLD".equalsIgnoreCase(rule.getTaxRegime())) {
                    continue;
                }

                cessAmount =
                        taxAfterSurcharge
                                .multiply(rule.getRate())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                break;
            }
        }

        BigDecimal taxBeforeAdjustment =
                taxAfterSurcharge.add(cessAmount);

        /* =====================================================
         * STEP 6H: ADJUST PREVIOUS EMPLOYER TDS (SECTION 192)
         * ===================================================== */

        BigDecimal finalTaxPayable =
                taxBeforeAdjustment.subtract(previousEmployerTds);

        if (finalTaxPayable.compareTo(BigDecimal.ZERO) < 0) {
            finalTaxPayable = BigDecimal.ZERO;
        }

        log.info(
                "[{}] 🎯 FINAL OLD TAX PAYABLE | taxBeforeAdjustment={} | prevTds={} | finalTax={}",
                method,
                taxBeforeAdjustment,
                previousEmployerTds,
                finalTaxPayable
        );



        /* =====================================================
         * STEP 7E: BUILD RESULT
         * ===================================================== */

        OldTaxCalculationResult result = new OldTaxCalculationResult();

        result.setGrossIncome(grossTotalIncome);
        result.setIncomeFromSalary(incomeFromSalary);

        /* 🔹 Previous Employment Exposure */
        result.setPreviousEmploymentIncome(netPreviousIncome);
        result.setTdsByPreviousEmployer(previousEmployerTds);

        result.setHraExemption(hraExemption);
        result.setStandardDeduction(standardDeduction);
        result.setTotalChapterVIA(totalChapterVIA);

        result.setTaxableIncome(taxableIncome);
        result.setTaxBeforeRebate(totalTaxBeforeRebate);
        result.setRebateAmount(rebateAmount);
        result.setSurcharge(surchargeAmount);
        result.setCess(cessAmount);

        result.setTaxPayable(finalTaxPayable);

        result.setIncomeFromHouseProperty(
                incomeFromHouseProperty != null ? incomeFromHouseProperty : BigDecimal.ZERO
        );

        result.setIncomeFromOtherSources(
                totalOtherIncome != null ? totalOtherIncome : BigDecimal.ZERO
        );


        log.info(
                "[{}] ✅ FINAL OLD TAX RESULT | gross={} | prevIncome={} | prevTds={} | finalTax={}",
                method,
                grossTotalIncome,
                netPreviousIncome,
                previousEmployerTds,
                finalTaxPayable
        );

        return result;

    }






//    @Transactional
//    public OldTaxCalculationResult calculateAndSaveOldTaxWithRevisedSalary(
//            String organizationId,
//            String employeeId,
//            Integer financialYear
//    ) {
//
//        final String method = "calculateAndSaveOldTaxWithRevisedSalary";
//
//        log.info(
//                "[{}] 🚀 START | orgId={} | empId={} | FY={}",
//                method, organizationId, employeeId, financialYear
//        );
//
//        /* =====================================================
//         * STEP 1: CALCULATE REVISED OLD TAX (ANNUAL ONLY)
//         * ===================================================== */
//
//        OldTaxCalculationResult result =
//                calculateOldTaxWithRevisedSalary(
//                        organizationId,
//                        employeeId,
//                        financialYear
//                );
//
//        log.info(
//                "[{}] 🧾 Revised OLD Tax calculated | gross={} | taxable={} | finalTax={}",
//                method,
//                result.getGrossIncome(),
//                result.getTaxableIncome(),
//                result.getTaxPayable()
//        );
//
//        /* =====================================================
//         * STEP 2: BUILD REVISION ENTITY
//         * ===================================================== */
//
//        OldTaxCalculationRevision entity =
//                new OldTaxCalculationRevision();
//
//        entity.setOrganizationId(organizationId);
//        entity.setEmployeeId(employeeId);
//        entity.setFinancialYear(financialYear);
//
//        // Salary & deductions
//        entity.setGrossIncome(result.getGrossIncome());
//        entity.setIncomeFromSalary(result.getIncomeFromSalary());
//        entity.setStandardDeduction(result.getStandardDeduction());
//        entity.setHraExemption(result.getHraExemption());
//        entity.setTotalChapterVIA(result.getTotalChapterVIA());
//
//        // Tax values
//        entity.setTaxableIncome(result.getTaxableIncome());
//        entity.setTaxBeforeRebate(result.getTaxBeforeRebate());
//        entity.setRebateAmount(result.getRebateAmount());
//        entity.setSurcharge(result.getSurcharge());
//        entity.setCess(result.getCess());
//        entity.setTaxPayable(result.getTaxPayable());
//
//        entity.setRevisionReason("SALARY_REVISION");
//
//        /* =====================================================
//         * STEP 3: UPSERT (ONE REVISION PER FY)
//         * ===================================================== */
//
//        oldTaxCalculationRevisionRepository
//                .findByOrganizationIdAndEmployeeIdAndFinancialYear(
//                        organizationId,
//                        employeeId,
//                        financialYear
//                )
//                .ifPresent(existing -> {
//                    entity.setId(existing.getId());
//                    log.info(
//                            "[{}] 🔁 Existing revised OLD tax found | updating id={}",
//                            method, existing.getId()
//                    );
//                });
//
//        /* =====================================================
//         * STEP 4: SAVE
//         * ===================================================== */
//
//        OldTaxCalculationRevision saved =
//                oldTaxCalculationRevisionRepository.save(entity);
//
//        log.info(
//                "[{}] ✅ Revised OLD Tax saved | revisionId={} | empId={} | FY={}",
//                method,
//                saved.getId(),
//                employeeId,
//                financialYear
//        );
//
//        log.info("[{}] 🏁 END", method);
//
//        return result;
//    }


    @Transactional(readOnly = true)
    public OldTaxCalculationResult calculateOldTaxWithRevisedSalaryAndApprovedPOI(
            String organizationId,
            String employeeId,
            Integer financialYear
    ) {

        final String method = "calculateOldTaxWithRevisedSalaryAndApprovedPOI";
        log.info("[{}] ▶ START | empId={} | FY={}", method, employeeId, financialYear);

        /* =====================================================
         * STEP 1: FETCH EMPLOYEE + ORG
         * ===================================================== */
        BasicDetails employee =
                basicDetailsRepository.findByEmployeeIdAndOrganization_OrganizationIdAndIsDeletedFalse(
                                employeeId,
                                organizationId
                        )
                        .orElseThrow(() ->
                                new RuntimeException("Employee not found"));

        Organization organization = employee.getOrganization();
        if (organization == null) {
            throw new RuntimeException("Organization not linked with employee");
        }

        LocalDate joiningDate =
                employee.getDateOfJoining() != null
                        ? LocalDate.parse(employee.getDateOfJoining())
                        : null;

        /* =====================================================
         * STEP 2: FETCH IT DECLARATION (OLD)
         * ===================================================== */
        EmployeeInvestmentDeclaration declaration =
                declarationRepo
                        .findByOrganizationAndEmployeeAndFiscalYear(
                                organization,
                                employee,
                                financialYear
                        )
                        .orElseThrow(() ->
                                new RuntimeException("IT Declaration not found"));

        if (!"OLD".equalsIgnoreCase(declaration.getTaxRegime())) {
            throw new IllegalStateException("Tax regime is not OLD");
        }

        /* =====================================================
         * STEP 3: FETCH APPROVED POI (SOURCE OF TRUTH)
         * ===================================================== */
        EmployeeProofOfInvestment poi =
                poiRepository
                        .findByOrganization_IdAndEmployee_IdAndFiscalYearAndStatus(
                                organization.getId(),
                                employee.getId(),
                                financialYear,
                                PayRunStatus.APPROVED
                        )
                        .orElseThrow(() ->
                                new RuntimeException("Approved POI not found"));

        /* =====================================================
         * STEP 4: CALCULATE ACTUAL FY GROSS SALARY (REVISED)
         * ===================================================== */
        BigDecimal grossSalary =
                calculateTotalFySalary(
                        organizationId,
                        employeeId,
                        financialYear,
                        joiningDate
                );


        if (grossSalary.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException(
                    "Computed FY gross salary is zero | empId=" + employeeId
            );
        }

        log.info(
                "[{}] ✅ FY Gross Salary (Revised) = {}",
                method,
                grossSalary
        );

        /* =====================================================
         * STEP 5A: STANDARD DEDUCTION (OLD REGIME)
         * ===================================================== */
        BigDecimal standardDeduction = BigDecimal.ZERO;

        if (grossSalary.compareTo(BigDecimal.ZERO) > 0) {
            standardDeduction = BigDecimal.valueOf(50_000);

            if (standardDeduction.compareTo(grossSalary) > 0) {
                standardDeduction = grossSalary;
            }
        }

        BigDecimal salaryAfterStandardDeduction =
                grossSalary.subtract(standardDeduction);

        if (salaryAfterStandardDeduction.compareTo(BigDecimal.ZERO) < 0) {
            salaryAfterStandardDeduction = BigDecimal.ZERO;
        }

        log.info(
                "[{}] ✅ Standard Deduction | gross={} | stdDeduction={} | afterStd={}",
                method,
                grossSalary,
                standardDeduction,
                salaryAfterStandardDeduction
        );

        /* =====================================================
         * STEP 5B: INCOME FROM HOUSE PROPERTY (APPROVED POI)
         * ===================================================== */
        BigDecimal incomeFromHouseProperty = BigDecimal.ZERO;

        /* ---------- Let-Out Property ---------- */
        BigDecimal letOutIncome = BigDecimal.ZERO;

        for (EmployeePOIItem item : poi.getPoiItems()) {

            if (item.getStatus() != PayRunStatus.APPROVED) continue;
            if (!"LET_OUT_PROPERTY".equalsIgnoreCase(item.getInvestmentType())) continue;

            if (item.getApprovedAmount() != null) {
                letOutIncome = letOutIncome.add(item.getApprovedAmount());
            }
        }

        /* ---------- Self-Occupied Home Loan (24B) ---------- */
        BigDecimal selfOccupiedLoss = BigDecimal.ZERO;

        Optional<HomeLoanRuleMaster> homeLoanRuleOpt =
                homeLoanRuleRepo.findByIsActiveTrue()
                        .stream()
                        .filter(r ->
                                "24B".equalsIgnoreCase(r.getSectionCode())
                                        && "INTEREST".equalsIgnoreCase(r.getComponent()))
                        .findFirst();

        if (homeLoanRuleOpt.isPresent()) {

            BigDecimal totalApprovedInterest =
                    poi.getPoiItems().stream()
                            .filter(i ->
                                    i.getStatus() == PayRunStatus.APPROVED
                                            && "HOME_LOAN_INTEREST".equalsIgnoreCase(i.getInvestmentType()))
                            .map(EmployeePOIItem::getApprovedAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalApprovedInterest.compareTo(BigDecimal.ZERO) > 0) {

                BigDecimal allowedInterest =
                        homeLoanRuleOpt.get().getMaxLimit() != null
                                ? totalApprovedInterest.min(homeLoanRuleOpt.get().getMaxLimit())
                                : totalApprovedInterest;

                selfOccupiedLoss = allowedInterest.negate();
            }
        }

        /* ---------- Section 71(3A) cap ---------- */
        BigDecimal combinedHouseProperty =
                letOutIncome.add(selfOccupiedLoss);

        BigDecimal MAX_HP_LOSS = BigDecimal.valueOf(200_000);

        if (combinedHouseProperty.compareTo(BigDecimal.ZERO) < 0
                && combinedHouseProperty.abs().compareTo(MAX_HP_LOSS) > 0) {

            combinedHouseProperty = MAX_HP_LOSS.negate();
        }

        incomeFromHouseProperty = combinedHouseProperty;

        log.info(
                "[{}] 🏠 Income From House Property = {}",
                method,
                incomeFromHouseProperty
        );


        /* =====================================================
         * STEP 5C: OTHER INCOME (APPROVED POI)
         * ===================================================== */
        BigDecimal totalOtherIncome =
                poi.getPoiItems().stream()
                        .filter(i ->
                                i.getStatus() == PayRunStatus.APPROVED
                                        && "OTHER_INCOME".equalsIgnoreCase(i.getInvestmentType()))
                        .map(EmployeePOIItem::getApprovedAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info(
                "[{}] 💰 Other Income (Approved) = {}",
                method,
                totalOtherIncome
        );


        // ⛔ STOP HERE (next steps later)


        /* =====================================================
         * STEP 6: GROSS TOTAL INCOME
         * ===================================================== */
        /* =====================================================
         * STEP 6: GROSS TOTAL INCOME (INCLUDING PREVIOUS EMPLOYMENT)
         * ===================================================== */

        /* 🔹 Extract Previous Employment Summary */
        PreviousEmploymentSummary prevSummary =
                extractPreviousEmploymentSummary(declaration);

        BigDecimal netPreviousIncome =
                prevSummary.getNetPreviousIncome();

        BigDecimal previousEmployerTds =
                prevSummary.getPreviousEmployerTds();

        /* 🔹 Gross Total Income = Salary + Previous + Other */
        BigDecimal grossTotalIncome =
                salaryAfterStandardDeduction
                        .add(netPreviousIncome != null ? netPreviousIncome : BigDecimal.ZERO)
                        .add(incomeFromHouseProperty != null ? incomeFromHouseProperty : BigDecimal.ZERO)
                        .add(totalOtherIncome != null ? totalOtherIncome : BigDecimal.ZERO);

        if (grossTotalIncome.compareTo(BigDecimal.ZERO) < 0) {
            grossTotalIncome = BigDecimal.ZERO;
        }

        log.info(
                "[{}] ✅ Gross Total Income | salary={} | prevIncome={} | houseProperty={} | other={} | total={}",
                method,
                salaryAfterStandardDeduction,
                netPreviousIncome,
                incomeFromHouseProperty,
                totalOtherIncome,
                grossTotalIncome
        );


        if (grossTotalIncome.compareTo(BigDecimal.ZERO) < 0) {
            grossTotalIncome = BigDecimal.ZERO;
        }





        /* =====================================================
         * STEP 7: CHAPTER VI-A DEDUCTIONS (APPROVED POI)
         * ===================================================== */

        BigDecimal totalChapterVIA = BigDecimal.ZERO;

        BigDecimal total80C = BigDecimal.ZERO;
        BigDecimal self80D = BigDecimal.ZERO;
        BigDecimal parents80D = BigDecimal.ZERO;
        BigDecimal additionalNps80CCD = BigDecimal.ZERO;
        BigDecimal otherSections = BigDecimal.ZERO;

        /* ---------- Load Section 6A Masters ---------- */
        Map<Long, Section6AItemMaster> masterMap =
                section6AItemRepo.findByIsActiveTrue()
                        .stream()
                        .collect(Collectors.toMap(
                                Section6AItemMaster::getId,
                                m -> m
                        ));

        /* ---------- Process Approved POI ---------- */
        for (EmployeePOIItem item : poi.getPoiItems()) {

            if (item.getStatus() != PayRunStatus.APPROVED) continue;
            if (item.getApprovedAmount() == null) continue;

            // ❌ HRA ignored in POI recalculation
            if ("HRA".equalsIgnoreCase(item.getInvestmentType())) continue;

            Section6AItemMaster master =
                    masterMap.get(item.getSection6aItemId());

            if (master == null) continue;

            BigDecimal amount = item.getApprovedAmount();
            String type =
                    item.getInvestmentType() != null
                            ? item.getInvestmentType().toUpperCase()
                            : "";

            /* ---------- 80C ---------- */
            if (Boolean.TRUE.equals(master.getIs80c())) {
                total80C = total80C.add(amount);
            }

            /* ---------- 80D ---------- */
            else if (Boolean.TRUE.equals(master.getIs80d())) {
                if (type.contains("SELF")) {
                    self80D = self80D.add(amount);
                } else if (type.contains("PARENTS")) {
                    parents80D = parents80D.add(amount);
                }
            }

            /* ---------- 80CCD(1B) ---------- */
            else if ("NPS_ADDITIONAL".equalsIgnoreCase(type)) {
                additionalNps80CCD =
                        additionalNps80CCD
                                .add(amount)
                                .min(BigDecimal.valueOf(50_000));
            }

            /* ---------- OTHER SECTIONS ---------- */
            else if (Boolean.TRUE.equals(master.getIsOtherSection())) {

                BigDecimal allowed;

                if ("SAVING_INTEREST".equalsIgnoreCase(type)) {
                    allowed = amount.min(BigDecimal.valueOf(10_000));
                } else if ("SAVING_INTEREST_SENIOR".equalsIgnoreCase(type)) {
                    allowed = amount.min(BigDecimal.valueOf(50_000));
                } else if ("DONATION_50".equalsIgnoreCase(type)) {
                    allowed = amount.multiply(BigDecimal.valueOf(0.5));
                } else if ("DONATION_100".equalsIgnoreCase(type)) {
                    allowed = amount;
                } else {
                    allowed =
                            master.getMaxLimit() != null
                                    ? amount.min(master.getMaxLimit())
                                    : amount;
                }

                otherSections = otherSections.add(allowed);
            }
        }

        /* ---------- APPLY CAPS ---------- */
        BigDecimal allowed80C =
                total80C.min(BigDecimal.valueOf(150_000));

        BigDecimal allowedSelf80D =
                self80D.min(BigDecimal.valueOf(25_000));

        BigDecimal allowedParents80D =
                parents80D.min(BigDecimal.valueOf(50_000));

        BigDecimal total80D =
                allowedSelf80D.add(allowedParents80D);

        /* ---------- FINAL CHAPTER VI-A ---------- */
        totalChapterVIA =
                allowed80C
                        .add(total80D)
                        .add(additionalNps80CCD)
                        .add(otherSections);

        log.info(
                "[{}] 🎯 Chapter VI-A | 80C={} | 80D={} | 80CCD(1B)={} | other={} | total={}",
                method,
                allowed80C,
                total80D,
                additionalNps80CCD,
                otherSections,
                totalChapterVIA
        );


        /* =====================================================
         * STEP 8: TAXABLE INCOME
         * ===================================================== */

        if (totalChapterVIA.compareTo(grossTotalIncome) > 0) {
            totalChapterVIA = grossTotalIncome;
        }

        BigDecimal taxableIncome =
                grossTotalIncome.subtract(totalChapterVIA);

        if (taxableIncome.compareTo(BigDecimal.ZERO) < 0) {
            taxableIncome = BigDecimal.ZERO;
        }

        log.info(
                "[{}] ✅ Taxable Income | gross={} | chapterVIA={} | taxable={}",
                method,
                grossTotalIncome,
                totalChapterVIA,
                taxableIncome
        );


        /* =====================================================
         * STEP 9: OLD REGIME SLAB TAX
         * ===================================================== */

        TaxSlabMaster slabMaster =
                taxSlabRepo.findByTaxRegimeAndIsActiveTrue("OLD")
                        .stream()
                        .findFirst()
                        .orElseThrow(() ->
                                new RuntimeException("Active OLD tax slab not found")
                        );

        ObjectMapper mapper = new ObjectMapper();
        List<TaxSlab> slabs;

        try {
            slabs = mapper.readValue(
                    slabMaster.getSlabJson(),
                    mapper.getTypeFactory()
                            .constructCollectionType(List.class, TaxSlab.class)
            );
        } catch (Exception e) {
            throw new RuntimeException("Invalid OLD slab JSON", e);
        }

        BigDecimal taxBeforeRebate = BigDecimal.ZERO;

        for (TaxSlab slab : slabs) {

            BigDecimal slabFrom = slab.getFrom();
            BigDecimal slabTo = slab.getTo(); // null = no upper limit
            BigDecimal rate = slab.getRate();

            if (taxableIncome.compareTo(slabFrom) <= 0) continue;

            BigDecimal upper =
                    slabTo != null
                            ? taxableIncome.min(slabTo)
                            : taxableIncome;

            BigDecimal slabIncome =
                    upper.subtract(slabFrom);

            if (slabIncome.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal slabTax =
                    slabIncome
                            .multiply(rate)
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            taxBeforeRebate = taxBeforeRebate.add(slabTax);

            log.info(
                    "[{}] 🧮 Slab {}–{} @ {}% | income={} | tax={}",
                    method,
                    slabFrom,
                    slabTo != null ? slabTo : "∞",
                    rate,
                    slabIncome,
                    slabTax
            );
        }

        log.info(
                "[{}] ✅ Tax Before Rebate = {}",
                method,
                taxBeforeRebate
        );


        /* =====================================================
         * STEP 10A: SECTION 87A REBATE
         * ===================================================== */

        BigDecimal rebateAmount = BigDecimal.ZERO;

        Section87ARebateRuleMaster rebateRule =
                rebateRuleRepo
                        .findByTaxRegimeAndIsActiveTrue("OLD")
                        .orElse(null);

        if (rebateRule != null
                && rebateRule.getIncomeThreshold() != null
                && rebateRule.getMaxRebateAmount() != null) {

            if (taxBeforeRebate.compareTo(BigDecimal.ZERO) > 0
                    && taxableIncome.compareTo(rebateRule.getIncomeThreshold()) <= 0) {

                rebateAmount =
                        taxBeforeRebate.min(
                                rebateRule.getMaxRebateAmount()
                        );

                log.info(
                        "[{}] ✅ Section 87A Rebate Applied | rebate={}",
                        method,
                        rebateAmount
                );
            }
        }

        BigDecimal taxAfterRebate =
                taxBeforeRebate.subtract(rebateAmount);

        if (taxAfterRebate.compareTo(BigDecimal.ZERO) < 0) {
            taxAfterRebate = BigDecimal.ZERO;
        }


        /* =====================================================
         * STEP 10B: SURCHARGE
         * ===================================================== */

        BigDecimal surchargeAmount = BigDecimal.ZERO;

        if (taxAfterRebate.compareTo(BigDecimal.ZERO) > 0) {

            for (CessSurchargeRuleMaster rule : cessSurchargeRepo.findByIsActiveTrue()) {

                if (!"SURCHARGE".equalsIgnoreCase(rule.getRuleType())) continue;

                if (rule.getTaxRegime() != null
                        && !"BOTH".equalsIgnoreCase(rule.getTaxRegime())
                        && !"OLD".equalsIgnoreCase(rule.getTaxRegime())) continue;

                boolean applicable = true;

                if (rule.getIncomeFrom() != null
                        && taxableIncome.compareTo(rule.getIncomeFrom()) < 0) {
                    applicable = false;
                }

                if (rule.getIncomeTo() != null
                        && taxableIncome.compareTo(rule.getIncomeTo()) > 0) {
                    applicable = false;
                }

                if (!applicable) continue;

                surchargeAmount =
                        taxAfterRebate
                                .multiply(rule.getRate())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                log.info(
                        "[{}] ✅ Surcharge Applied | rate={} | amount={}",
                        method,
                        rule.getRate(),
                        surchargeAmount
                );

                break; // only one slab applies
            }
        }

        BigDecimal taxAfterSurcharge =
                taxAfterRebate.add(surchargeAmount);


        /* =====================================================
         * STEP 10C: HEALTH & EDUCATION CESS
         * ===================================================== */

        BigDecimal cessAmount = BigDecimal.ZERO;

        if (taxAfterSurcharge.compareTo(BigDecimal.ZERO) > 0) {

            for (CessSurchargeRuleMaster rule : cessSurchargeRepo.findByIsActiveTrue()) {

                if (!"CESS".equalsIgnoreCase(rule.getRuleType())) continue;

                if (rule.getTaxRegime() != null
                        && !"BOTH".equalsIgnoreCase(rule.getTaxRegime())
                        && !"OLD".equalsIgnoreCase(rule.getTaxRegime())) continue;

                cessAmount =
                        taxAfterSurcharge
                                .multiply(rule.getRate())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                log.info(
                        "[{}] ✅ Cess Applied | rate={} | amount={}",
                        method,
                        rule.getRate(),
                        cessAmount
                );

                break;
            }
        }

        /* =====================================================
         * FINAL TAX BEFORE PREVIOUS TDS ADJUSTMENT
         * ===================================================== */

        BigDecimal taxBeforeAdjustment =
                taxAfterSurcharge.add(cessAmount);

        /* =====================================================
         * SECTION 192 ADJUSTMENT
         * Deduct TDS already paid by previous employer
         * ===================================================== */

        BigDecimal finalTaxPayable =
                taxBeforeAdjustment.subtract(
                        previousEmployerTds != null
                                ? previousEmployerTds
                                : BigDecimal.ZERO
                );

        if (finalTaxPayable.compareTo(BigDecimal.ZERO) < 0) {
            finalTaxPayable = BigDecimal.ZERO;
        }

        log.info(
                "[{}] 🎯 FINAL OLD TAX (REVISED + POI + PREV TDS ADJUSTED) | beforeAdj={} | prevTds={} | final={}",
                method,
                taxBeforeAdjustment,
                previousEmployerTds,
                finalTaxPayable
        );




        /* =====================================================
         * STEP 10D: BUILD RESULT
         * ===================================================== */

        OldTaxCalculationResult result = new OldTaxCalculationResult();



        /* Income */
        result.setGrossIncome(grossTotalIncome);
        result.setIncomeFromSalary(grossSalary);
        result.setIncomeFromHouseProperty(incomeFromHouseProperty);
        result.setIncomeFromOtherSources(totalOtherIncome);

        /* Exemptions & Deductions */
        result.setHraExemption(BigDecimal.ZERO); // intentionally ignored for POI
        result.setStandardDeduction(standardDeduction);
        result.setTotalChapterVIA(totalChapterVIA);


        /* Previous Employment */
        result.setPreviousEmploymentIncome(netPreviousIncome);
        result.setTdsByPreviousEmployer(previousEmployerTds);

        /* Tax */
        result.setTaxableIncome(taxableIncome);
        result.setTaxBeforeRebate(taxBeforeRebate);
        result.setRebateAmount(rebateAmount);
        result.setSurcharge(surchargeAmount);
        result.setCess(cessAmount);
        result.setTaxPayable(finalTaxPayable);

        log.info(
                "[{}] ✅ OLD TAX RESULT READY | taxable={} | finalTax={}",
                method,
                taxableIncome,
                finalTaxPayable
        );

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
            LocalDate endDate,
            Integer financialYear
    ) {

        if (monthlyAmount == null || monthlyAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        if (financialYear == null) {
            throw new IllegalArgumentException("financialYear cannot be null");
        }

        /* ---------- FY RANGE ---------- */
        LocalDate fyStart = LocalDate.of(financialYear - 1, 4, 1);
        LocalDate fyEnd   = LocalDate.of(financialYear, 3, 31);

        /* ---------- START DATE ---------- */
        LocalDate salaryStart = fyStart;

        if (joiningDate != null && joiningDate.isAfter(salaryStart)) {
            salaryStart = joiningDate;
        }

        if (effectiveDate != null && effectiveDate.isAfter(salaryStart)) {
            salaryStart = effectiveDate;
        }

        /* ---------- END DATE ---------- */
        LocalDate salaryEnd = (endDate != null && endDate.isBefore(fyEnd))
                ? endDate
                : fyEnd;

        if (salaryStart.isAfter(salaryEnd)) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalSalary = BigDecimal.ZERO;

    /* =====================================================
       1️⃣ HANDLE FIRST (JOINING) MONTH — DAY-WISE
       ===================================================== */
        YearMonth startYm = YearMonth.from(salaryStart);
        YearMonth endYm   = YearMonth.from(salaryEnd);

        int daysInStartMonth = startYm.lengthOfMonth();

        // If joining happened mid-month
        if (joiningDate != null
                && salaryStart.equals(joiningDate)
                && salaryStart.getDayOfMonth() > 1) {

            int payableDays = daysInStartMonth - salaryStart.getDayOfMonth() + 1;

            BigDecimal perDay =
                    monthlyAmount.divide(
                            BigDecimal.valueOf(daysInStartMonth),
                            10,
                            RoundingMode.HALF_UP
                    );

            BigDecimal firstMonthAmount =
                    perDay.multiply(BigDecimal.valueOf(payableDays));

            totalSalary = totalSalary.add(firstMonthAmount);

            // Move to next full month
            startYm = startYm.plusMonths(1);
        }

    /* =====================================================
       2️⃣ HANDLE FULL MONTHS AFTER FIRST MONTH
       ===================================================== */
        long fullMonths =
                ChronoUnit.MONTHS.between(startYm, endYm) + 1;

        if (fullMonths > 0) {
            totalSalary =
                    totalSalary.add(
                            monthlyAmount.multiply(
                                    BigDecimal.valueOf(fullMonths)
                            )
                    );
        }

        return totalSalary.setScale(2, RoundingMode.HALF_UP);
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

            log.info("[{}] CTC ID={} | effectiveDate={} | endDate={} | monthlyGross={} | earned={}");

            totalSalary = totalSalary.add(earned);
        }

        log.info("[{}] ✅ TOTAL FY EARNED SALARY = {}", method, totalSalary);
        log.info("[{}] ◀ END", method);

        return totalSalary;
    }




    private PreviousEmploymentSummary extractPreviousEmploymentSummary(
            EmployeeInvestmentDeclaration declaration
    ) {

        PreviousEmploymentSummary summary = new PreviousEmploymentSummary();

        // 🔹 Safety check
        if (declaration == null
                || declaration.getPrevEmploymentDeclarations() == null
                || declaration.getPrevEmploymentDeclarations().isEmpty()) {

            return summary; // return ZERO values safely
        }

        BigDecimal previousIncome = BigDecimal.ZERO;
        BigDecimal professionalTax = BigDecimal.ZERO;
        BigDecimal previousTds = BigDecimal.ZERO;

        for (EmployeeInvPrevEmployment item
                : declaration.getPrevEmploymentDeclarations()) {

            if (item == null || item.getAmount() == null) {
                continue;
            }

            String type = item.getType() != null
                    ? item.getType().toUpperCase()
                    : "";

            switch (type) {

                case "INCOME":
                case "PREVIOUS_EMPLOYER_SALARY":
                    previousIncome = previousIncome.add(item.getAmount());
                    break;

                case "PROFESSIONAL_TAX":
                    professionalTax = professionalTax.add(item.getAmount());
                    break;

                case "INCOME_TAX":
                case "PREVIOUS_EMPLOYER_TDS":
                    previousTds = previousTds.add(item.getAmount());
                    break;

                default:
                    // Ignore EPF, leave encashment etc.
                    break;
            }
        }

        // 📜 Govt Rule → Salary - Professional Tax
        BigDecimal netPreviousIncome =
                previousIncome.subtract(professionalTax);

        if (netPreviousIncome.compareTo(BigDecimal.ZERO) < 0) {
            netPreviousIncome = BigDecimal.ZERO;
        }

        summary.setNetPreviousIncome(netPreviousIncome);
        summary.setPreviousEmployerTds(previousTds);

        return summary;
    }


    private boolean hasSalaryRevisionInCurrentFY(
            String organizationId,
            String employeeId,
            Integer financialYear
    ) {

        LocalDate fyStart = LocalDate.of(financialYear - 1, 4, 1);
        LocalDate fyEnd   = LocalDate.of(financialYear, 3, 31);

        List<CtcStructure> ctcList =
                ctcStructureRepository
                        .findByOrganization_OrganizationIdAndEmployee_EmployeeId(
                                organizationId,
                                employeeId
                        );

        if (ctcList == null || ctcList.isEmpty()) {
            return false;
        }

        long countInFy = ctcList.stream()
                .filter(ctc -> !Boolean.TRUE.equals(ctc.getDeleted()))
                .filter(ctc -> ctc.getEffectiveDate() != null)
                .filter(ctc -> {
                    LocalDate effectiveDate = ctc.getEffectiveDate();
                    return !effectiveDate.isBefore(fyStart)
                            && !effectiveDate.isAfter(fyEnd);
                })
                .count();

        return countInFy > 1;
    }


    private void validateCtcExistsForFy(
            String organizationId,
            String employeeId,
            Integer financialYear
    ) {

        LocalDate fyStart = LocalDate.of(financialYear - 1, 4, 1);
        LocalDate fyEnd   = LocalDate.of(financialYear, 3, 31);

        List<CtcStructure> ctcs =
                ctcStructureRepository
                        .findByOrganization_OrganizationIdAndEmployee_EmployeeId(
                                organizationId,
                                employeeId
                        )
                        .stream()
                        .filter(ctc -> !Boolean.TRUE.equals(ctc.getDeleted()))
                        .filter(ctc -> ctc.getEffectiveDate() != null)
                        .filter(ctc -> !ctc.getEffectiveDate().isAfter(fyEnd))
                        .toList();

        if (ctcs.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No CTC configured for selected financial year"
            );
        }
    }



}




