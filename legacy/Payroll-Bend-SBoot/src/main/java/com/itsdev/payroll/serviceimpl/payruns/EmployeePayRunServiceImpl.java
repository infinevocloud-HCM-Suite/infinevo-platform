package com.itsdev.payroll.serviceimpl.payruns;

import com.itsdev.payroll.dto.employee.EpfComponentDTO;
import com.itsdev.payroll.dto.employee.EsiComponentDTO;
import com.itsdev.payroll.dto.organization.WorkLocationDTO;
import com.itsdev.payroll.dto.payruns.EarningComponentDTO;
import com.itsdev.payroll.dto.payruns.EmployeePayRunDTO;
import com.itsdev.payroll.dto.payruns.EmployeePayslipDTO;
import com.itsdev.payroll.dto.payruns.EmployeeSummaryDTO;
import com.itsdev.payroll.dto.payruns.PayrollSummaryDTO;
import com.itsdev.payroll.dto.payruns.PayslipResponseDTO;
import com.itsdev.payroll.dto.statutorycomponents.ProfessionalTaxDTO;
import com.itsdev.payroll.dto.statutorycomponents.SlabDetailDTO;
import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeeProofOfInvestment;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.employee.CtcEpfComponent;
import com.itsdev.payroll.entity.employee.CtcStructure;
import com.itsdev.payroll.entity.employee.EmployeeBankDetail;
import com.itsdev.payroll.entity.employee.EmployeeBenefit;
import com.itsdev.payroll.entity.employee.EmployeeEarning;
import com.itsdev.payroll.entity.employee.EmployeeReimbursement;
import com.itsdev.payroll.entity.employeeTDS.EmployeeTds;
import com.itsdev.payroll.entity.SalaryDeduction;
import com.itsdev.payroll.entity.employeereimbursement.EmployeeReimbursementRequest;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.organization.WorkLocation;
import com.itsdev.payroll.entity.payruns.EmployeePayRun;
import com.itsdev.payroll.entity.payruns.PayRun;
import com.itsdev.payroll.repository.employee.CtcStructureRepository;
import com.itsdev.payroll.repository.employeeTDS.EmployeeTdsRepository;
import com.itsdev.payroll.entity.statutorycomponents.ProfessionalTax;
import com.itsdev.payroll.entity.statutorycomponents.SlabDetail;
import com.itsdev.payroll.mapper.payruns.EmployeePayRunMapper;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employee.SalaryDeductionRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.EmployeeInvestmentDeclarationRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.NewTaxCalculationRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.OldTaxCalculationRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.payruns.EmployeePayRunRepository;
import com.itsdev.payroll.repository.payruns.PayRunRepository;
import com.itsdev.payroll.repository.employeereimbursement.EmployeeReimbursementRequestRepository;
import com.itsdev.payroll.service.IntegrateWithHrmsService;
import com.itsdev.payroll.service.employeeTDS.DefaultTdsCreationService;
import com.itsdev.payroll.service.payruns.EmployeePayRunService;

import com.itsdev.payroll.entity.claimsanddeclarations.ProofOfInvestment;
import com.itsdev.payroll.repository.claimsanddeclarations.ProofOfInvestmentRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.poi.EmployeeProofOfInvestmentRepository;

import com.itsdev.payroll.service.statutorycomponents.ProfessionalTaxService;
import com.itsdev.payroll.enumeration.DeductionStatus;
import com.itsdev.payroll.enumeration.employeereimbursement.ReimbursementPaymentStatus;
import com.itsdev.payroll.enumeration.employeereimbursement.ReimbursementStatus;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmployeePayRunServiceImpl implements EmployeePayRunService {

    private static final Logger log = LoggerFactory.getLogger(EmployeePayRunServiceImpl.class);

    private static final DateTimeFormatter PROCESSING_PERIOD_FORMATTER =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
private static final DateTimeFormatter YEAR_MONTH_FORMATTER =
    DateTimeFormatter.ofPattern("yyyy-MM", Locale.ENGLISH);

    private final BasicDetailsRepository basicDetailsRepository;
    private final EmployeePayRunRepository employeePayRunRepository;
    private final PayRunRepository payRunRepository;
    private final OrganizationRepository organizationRepository;
    private final IntegrateWithHrmsService integrateWithHrmsService;
    private final ProfessionalTaxService professionalTaxService;
    private final ProofOfInvestmentRepository proofOfInvestmentRepository;
    private final EmployeeProofOfInvestmentRepository employeePoiRepository;
    private final CtcStructureRepository ctcStructureRepository;
    private final EmployeeTdsRepository employeeTdsRepository;
    private final DefaultTdsCreationService defaultTdsCreationService;
    private final SalaryDeductionRepository salaryDeductionRepository;
    private final EmployeeReimbursementRequestRepository employeeReimbursementRequestRepository;



    public EmployeePayRunServiceImpl(
            BasicDetailsRepository basicDetailsRepository,
            EmployeePayRunRepository employeePayRunRepository,
            PayRunRepository payRunRepository,
            OrganizationRepository organizationRepository,
            IntegrateWithHrmsService integrateWithHrmsService,
            ProfessionalTaxService professionalTaxService ,  // ✅ comma + inside params,
            ProofOfInvestmentRepository proofOfInvestmentRepository,
            EmployeeProofOfInvestmentRepository employeePoiRepository,
            CtcStructureRepository ctcStructureRepository,
            EmployeeTdsRepository employeeTdsRepository,
            DefaultTdsCreationService defaultTdsCreationService,
            SalaryDeductionRepository salaryDeductionRepository,
            EmployeeReimbursementRequestRepository employeeReimbursementRequestRepository
    )

    { // <-- added
        // <-- add this
        this.basicDetailsRepository = basicDetailsRepository;
        this.employeePayRunRepository = employeePayRunRepository;
        this.payRunRepository = payRunRepository;
        this.organizationRepository = organizationRepository; // <-- added
        this.integrateWithHrmsService = integrateWithHrmsService;
        this.professionalTaxService = professionalTaxService;  // <-- assign
        this.proofOfInvestmentRepository = proofOfInvestmentRepository;
        this.employeePoiRepository = employeePoiRepository;
        this.ctcStructureRepository = ctcStructureRepository;
        this.employeeTdsRepository = employeeTdsRepository;
        this.defaultTdsCreationService = defaultTdsCreationService;
        this.salaryDeductionRepository = salaryDeductionRepository;
        this.employeeReimbursementRequestRepository = employeeReimbursementRequestRepository;

    }

    private Organization getOrganizationOrThrow(String organizationId) {
        return organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));
    }

    @Override
    public List<EmployeePayRunDTO> getEmployeePayRunList(String organizationId, String processingPeriod) {
        String method = "getEmployeePayRunList";
        log.info("[{}] 📥 Incoming request to fetch employee pay run list for organizationId={}, processingPeriod={}", method, organizationId);



        String status = "ACTIVE";
        log.debug("[{}] 🔍 Fetching employees from DB with status='{}'", method, status);

        List<BasicDetails> employees = basicDetailsRepository
                .findByOrganization_OrganizationIdAndEmployeeStatus(organizationId, status);

        log.info("[{}] ✅ Retrieved {} active employees for organizationId={}", method, employees.size(), organizationId);

//        LocalDate periodDate = YearMonth.parse(
//                processingPeriod.trim(),
//                DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
//        ).atDay(1);

        // Only New Logic added for effective date checks
        YearMonth yearMonth = YearMonth.parse(
                processingPeriod.trim(),
                DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
        );

        LocalDate periodDate = yearMonth.atEndOfMonth();


        // Filter only employees with completed setup (ACTIVE CTC, bank, personal details)
        List<BasicDetails> completedEmployees = employees.stream()
                .filter(emp -> {

                    boolean hasApplicableCtc = ctcStructureRepository
                            .findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                                    organizationId,
                                    emp.getId(),
                                    periodDate
                            )
                            .isPresent();

                    return hasApplicableCtc
                            && emp.getBankDetail() != null
                            && emp.getPersonalDetail() != null;
                })
                .collect(Collectors.toList());



        log.info("[{}] ✅ Filtered {} employees with completed setup", method, completedEmployees.size());

        List<EmployeePayRunDTO> result = completedEmployees.stream()
                .peek(emp -> log.info("[{}] ⚙️ Mapping employeeId={} | employeeNumber={}",
                        method, emp.getEmployeeId(), emp.getEmployeeNumber()))
                .map(emp -> mapToPayRunDTO(emp, processingPeriod))  // Pass processingPeriod
                .collect(Collectors.toList());

        log.info("[{}] 🏁 Completed building pay run list (size={}) for organizationId={}",
                method, result.size(), organizationId);

        return result;
    }

    private EmployeePayRunDTO mapToPayRunDTO(BasicDetails emp, String processingPeriod) {
        String method = "mapToPayRunDTO";
        log.info("[{}] ➡️ Mapping started for employeeId={} | employeeNumber={} | processingPeriod={}",
                method, emp.getEmployeeId(), emp.getEmployeeNumber(), processingPeriod);

//        LocalDate periodDate = YearMonth.parse(
//                processingPeriod.trim(),
//                DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
//        ).atDay(1);

        // Only New Logic added for effective date checks
        YearMonth yearMonth = YearMonth.parse(
                processingPeriod.trim(),
                DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
        );

        LocalDate periodDate = yearMonth.atEndOfMonth();

        CtcStructure ctc = ctcStructureRepository
                .findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        emp.getOrganization().getOrganizationId(),
                        emp.getId(),
                        periodDate
                )
                .orElse(null);


        EmployeeBankDetail bank = emp.getBankDetail();

        BigDecimal totalEarnings = BigDecimal.ZERO;
        BigDecimal totalBenefits = BigDecimal.ZERO;
        BigDecimal totalReimbursements = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalTaxes = BigDecimal.ZERO;
        BigDecimal monthlySalary = BigDecimal.ZERO;


        if (ctc != null) {
            // Earnings (exclude variable earnings)
            if (ctc.getEarnings() != null) {
                totalEarnings = ctc.getEarnings().stream()
                        .filter(EmployeeEarning::getEnabled)
                        .filter(e -> !Boolean.TRUE.equals(e.getIsVariable()))
                        .map(e -> BigDecimal.valueOf(e.getAmount() != null ? e.getAmount() : 0.0))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                log.info("[{}] 💵 Earnings summed: {}", method, totalEarnings);
            } else {
                log.info("[{}] ⚠️ No earnings found for employee number={}", method, emp.getEmployeeNumber());
            }

            // Benefits
            if (ctc.getBenefits() != null) {
                totalBenefits = ctc.getBenefits().stream()
                        .filter(EmployeeBenefit::getEnabled)
                        .map(b -> BigDecimal.valueOf(b.getAmount() != null ? b.getAmount() : 0.0))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                log.info("[{}] 🎁 Benefits summed: {}", method, totalBenefits);
            } else {
                log.info("[{}] ⚠️ No benefits found for employee number={}", method, emp.getEmployeeNumber());
            }

            // Reimbursements
            if (ctc.getReimbursements() != null) {
                totalReimbursements = ctc.getReimbursements().stream()
                        .filter(EmployeeReimbursement::getEnabled)
                        .map(r -> BigDecimal.valueOf(r.getAmount() != null ? r.getAmount() : 0.0))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                log.info("[{}] 💳 Reimbursements summed: {}", method, totalReimbursements);
            } else {
                log.info("[{}] ⚠️ No reimbursements found for employee number={}", method, emp.getEmployeeNumber());
            }

            // Monthly Salary
            if (ctc.getMonthlySalary() != null) {
                monthlySalary = ctc.getMonthlySalary().setScale(2, RoundingMode.HALF_UP);
                log.info("[{}] 🏦 Monthly salary: {}", method, monthlySalary);
            } else {
                log.warn("[{}] ⚠️ No monthly salary found for employee number={}", method, emp.getEmployeeNumber());
            }

        } else {
            log.warn("[{}] ⚠️ No CTC structure found for employee number={}", method, emp.getEmployeeNumber());
        }

        totalDeductions = calculateEpfEmployer(emp, periodDate);


        log.info("[{}] 💰 Employer EPF calculated → {}", method, totalDeductions);

        if (emp.getEligibleForPt()) {

            log.info("[{}] 🧮 Employee {} is eligible for PT. Proceeding to calculate...",
                     method, emp.getEmployeeNumber());

            totalTaxes = calculateProfessionalTax(emp, monthlySalary);

            log.info("[{}] ✔ PT Calculated for employee {} → PT Amount = {}",
                     method, emp.getEmployeeNumber(), totalTaxes);

            // ✅ Add PT to total deductions
            totalDeductions = totalDeductions.add(totalTaxes);

            log.info("[{}] ➕ Added PT to total deductions. New Total Deductions = {}",
                     method, totalDeductions);

        } else {

            log.info("[{}] ⛔ Employee {} is NOT eligible for Professional Tax. Skipping PT calculation.",
                     method, emp.getEmployeeNumber());
        }

        // ✅ Use the passed processingPeriod
        BigDecimal monthlyTds = resolveMonthlyTds(emp, processingPeriod);

        log.info("Monthly TDS calculated = {}",
                monthlyTds);

        // ➕ Add TDS to total deductions
        totalDeductions = totalDeductions.add(monthlyTds);

        log.info("[{}] ➕ Monthly TDS {} added to deductions. Total Deductions={}",
                method, monthlyTds, totalDeductions);

        // ✅ Fetch claim deductions (from employee deduction module) for this processing period
        String organizationId = emp.getOrganization().getOrganizationId();
        LocalDate deductionMonth = yearMonth.atDay(1); // deductionMonth stored as first of month
        List<SalaryDeduction> claimDeductions = salaryDeductionRepository
                .findByEmployeeIdAndOrganizationIdAndDeductionMonthAndStatus(
                        emp.getEmployeeId(), organizationId, deductionMonth, DeductionStatus.ACTIVE);

        BigDecimal claimDeductionAmount = claimDeductions.stream()
                .map(SalaryDeduction::getDeductionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (claimDeductionAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalDeductions = totalDeductions.add(claimDeductionAmount);
            log.info("[{}] ➕ Claim deduction {} added to total deductions. New Total Deductions={}",
                    method, claimDeductionAmount, totalDeductions);
        }

        // ✅ Fetch claim reimbursements (from employee reimbursement module) for this processing period
        // Convert processingPeriod "August 2026" to "2026-08" format to match DB storage
        String reimbursementMonthForQuery = yearMonth.getYear() + "-" + String.format("%02d", yearMonth.getMonthValue());
        List<EmployeeReimbursementRequest> claimReimbursements = employeeReimbursementRequestRepository
                .findByEmployeeIdAndOrganizationIdAndReimbursementMonthAndStatusAndPaymentStatus(
                        emp.getEmployeeId(), organizationId, reimbursementMonthForQuery,
                        ReimbursementStatus.APPROVED, ReimbursementPaymentStatus.UNPAID);

        BigDecimal claimReimbursementAmount = claimReimbursements.stream()
                .map(EmployeeReimbursementRequest::getApprovedAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (claimReimbursementAmount.compareTo(BigDecimal.ZERO) > 0) {
            log.info("[{}] ➕ Claim reimbursement {} will be added to net pay",
                    method, claimReimbursementAmount);
        }




        boolean bonusEarningExist = ctc != null && ctc.getEarnings() != null && ctc.getEarnings().stream()
                .filter(EmployeeEarning::getEnabled)
                .anyMatch(e -> Boolean.TRUE.equals(e.getIsVariable()));
        
        log.info("[{}] 🔍 Bonus earning exist for employee number={} → {}",method, emp.getEmployeeNumber(), bonusEarningExist);

        BigDecimal bonusAmount = BigDecimal.ZERO;
        if (bonusEarningExist) {
            YearMonth periodYearMonth = YearMonth.parse(processingPeriod.trim(), DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
            String dojString = emp.getDateOfJoining();
            final LocalDate doj = (dojString != null && !dojString.isBlank())
                    ? LocalDate.parse(dojString)
                    : null;
            bonusAmount = ctc.getEarnings().stream()
                    .filter(EmployeeEarning::getEnabled)
                    .filter(EmployeeEarning::getIsVariable)
                    .map(e -> getPeriodicBonusAmount(e, periodYearMonth, doj))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            log.info("[{}] 🎯 Calculated bonusAmount={} for processingPeriod={}", method, bonusAmount, processingPeriod);
        }

        BigDecimal netPay = totalEarnings.add(totalBenefits).add(totalReimbursements)
                .add(claimReimbursementAmount)
                .subtract(totalDeductions)
                .setScale(2, RoundingMode.HALF_UP);
        log.info("[{}] ➕ Calculated netPay={}", method, netPay);

        String paymentMode = (bank != null && bank.getPaymentMode() != null) ? bank.getPaymentMode() : "cash";
        log.info("[{}] 💳 Payment mode for employee number={} is {}", method, emp.getEmployeeNumber(), paymentMode);

        EmployeePayRunDTO dto = new EmployeePayRunDTO();
        dto.setEmployeeId(emp.getEmployeeId());
        dto.setEmployeeNumber(emp.getEmployeeNumber());
        dto.setEmployeeName(emp.getFirstName() + " " + emp.getLastName());
        dto.setFullName(emp.getFirstName() + " " + emp.getLastName());
        dto.setPaymentStatus("yet_to_pay");
        dto.setPaymentMode(paymentMode);
        dto.setTotalDeductions(totalDeductions.setScale(2, RoundingMode.HALF_UP).doubleValue());
        dto.setTotalBenefits(totalBenefits.setScale(2, RoundingMode.HALF_UP).doubleValue());
        dto.setTotalDonations(0.0);
        dto.setTotalTaxes(totalTaxes.setScale(2, RoundingMode.HALF_UP).doubleValue());
        dto.setTotalEarnings(totalEarnings.setScale(2, RoundingMode.HALF_UP).doubleValue());
        dto.setTotalReimbursements(totalReimbursements.setScale(2, RoundingMode.HALF_UP).doubleValue());
        dto.setNetPay(netPay.doubleValue());
        dto.setBonusEarningExistForEmployee(bonusEarningExist);
        dto.setBonus(bonusAmount.setScale(2, RoundingMode.HALF_UP).doubleValue());
        dto.setGrossDeductions(totalDeductions.setScale(2, RoundingMode.HALF_UP).doubleValue());
        dto.setPaidDays(30.0);
        dto.setTaxOverridden(false);
        dto.setEmployeeHavingHoldSalary(false);
        dto.setEmployeeStatus(emp.getEmployeeStatus());
        dto.setCanSkipWithJoineeArrear(false);
        dto.setMonthlyTds(monthlyTds.doubleValue());
        dto.setClaimDeduction(claimDeductionAmount.setScale(2, RoundingMode.HALF_UP).doubleValue());
        dto.setClaimReimbursement(claimReimbursementAmount.setScale(2, RoundingMode.HALF_UP).doubleValue());
        dto.setClaimDeductionStatus(claimDeductions.isEmpty() ? null : "INPAYRUN");
        dto.setClaimReimbursementStatus(claimReimbursements.isEmpty() ? null : "INPAYRUN");

        // ✅ Set monthly salary
        dto.setMonthlySalary(monthlySalary.doubleValue());

        log.info("[{}] ✅ Mapping complete for employee number={} | netPay={} | monthlySalary={}",
                method, emp.getEmployeeNumber(), dto.getNetPay(), dto.getMonthlySalary());

        return dto;
    }

    // Helper method to derive fiscal year from a given YearMonth

    private LocalDate getFyStartDate(int fiscalYear) {
        return LocalDate.of(fiscalYear - 1, 4, 1); // 1 Apr previous year
    }

    private LocalDate getFyEndDate(int fiscalYear) {
        return LocalDate.of(fiscalYear, 3, 31); // 31 Mar FY year
    }

    private BigDecimal getPeriodicBonusAmount(EmployeeEarning earning, YearMonth periodYearMonth, LocalDate doj) {

        final String method = "getPeriodicBonusAmount";

        log.info("[{}] ▶ Starting bonus calculation", method);

        if (earning == null) {
            log.warn("[{}] ❌ EmployeeEarning is null", method);
            return BigDecimal.ZERO;
        }

        if (earning.getAmount() == null) {
            log.warn("[{}] ❌ Bonus amount is null", method);
            return BigDecimal.ZERO;
        }

        if (earning.getEarningFrequency() == null) {
            log.warn("[{}] ❌ Earning frequency is null", method);
            return BigDecimal.ZERO;
        }

        String frequency = earning.getEarningFrequency().trim().toLowerCase(Locale.ENGLISH);
        int currentMonth = periodYearMonth.getMonthValue();
        BigDecimal amount = BigDecimal.valueOf(earning.getAmount());
        log.info("[{}] 🔍 Processing bonus | frequency={} | annualAmount={} | processingMonth={} | doj={}",
                method, frequency, amount, currentMonth, doj);

        YearMonth dojYearMonth = doj != null ? YearMonth.from(doj) : null;
        long monthsCompleted = dojYearMonth != null ? ChronoUnit.MONTHS.between(dojYearMonth, periodYearMonth) : -1;

        if (frequency.contains("half")) {
            log.info("[{}] 📆 Half-Yearly bonus detected | monthsCompleted={}", method, monthsCompleted);
            if (currentMonth == 7 || currentMonth == 1) {
                if (dojYearMonth != null && monthsCompleted < 6) {
                    log.info("[{}] ⏭ Half-Yearly bonus skipped because DOJ={} is too recent for processingMonth={}", method, doj, currentMonth);
                    return BigDecimal.ZERO;
                }
                BigDecimal bonus = amount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                log.info("[{}] ✅ Half-Yearly bonus applicable for month={} | bonus={}", method, currentMonth, bonus);
                return bonus;
            }
            log.info("[{}] ⏭ Half-Yearly bonus not applicable for month={}", method, currentMonth);
            return BigDecimal.ZERO;
        }

        if (frequency.contains("quart")) {
            log.info("[{}] 📆 Quarterly bonus detected | monthsCompleted={}", method, monthsCompleted);
            if (currentMonth == 4 || currentMonth == 7 || currentMonth == 10 || currentMonth == 1) {
                if (dojYearMonth != null && monthsCompleted < 3) {
                    log.info("[{}] ⏭ Quarterly bonus skipped because DOJ={} is too recent for processingMonth={}", method, doj, currentMonth);
                    return BigDecimal.ZERO;
                }
                BigDecimal bonus = amount.divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
                log.info("[{}] ✅ Quarterly bonus applicable for month={} | bonus={}", method, currentMonth, bonus);
                return bonus;
            }
            log.info("[{}] ⏭ Quarterly bonus not applicable for month={}", method, currentMonth);
            return BigDecimal.ZERO;
        }

        if (frequency.contains("year") || frequency.contains("annual")) {
            log.info("[{}] 📆 Annual bonus detected | monthsCompleted={}", method, monthsCompleted);
            if (currentMonth == 1) {
                if (dojYearMonth != null && monthsCompleted < 12) {
                    log.info("[{}] ⏭ Annual bonus skipped because DOJ={} is too recent for processingMonth={}", method, doj, currentMonth);
                    return BigDecimal.ZERO;
                }
                BigDecimal bonus = amount.setScale(2, RoundingMode.HALF_UP);
                log.info("[{}] ✅ Annual bonus applicable for month={} | bonus={}", method, currentMonth, bonus);
                return bonus;
            }
            log.info("[{}] ⏭ Annual bonus not applicable for month={}", method, currentMonth);
            return BigDecimal.ZERO;
        }

        log.warn("[{}] ⚠ Unsupported earning frequency='{}' | Returning 0", method, frequency);
        return BigDecimal.ZERO;
    }


    //Method to resolve monthly TDS based on remaining tax and remaining months in FY

    private BigDecimal resolveMonthlyTds(BasicDetails emp, String processingPeriod) {

        final String method = "resolveMonthlyTds";

        // 1️⃣ Parse processing period
        YearMonth payYm = parseProcessingPeriodToYearMonth(processingPeriod, method);
        if (payYm == null) return BigDecimal.ZERO;

        Integer fiscalYear = deriveFiscalYear(payYm);

        log.info("[{}] empId={} payYm={} fiscalYear={}",
                method, emp.getEmployeeId(), payYm, fiscalYear);

        // 2️⃣ Fetch ACTIVE EmployeeTds
        EmployeeTds empTds =
                employeeTdsRepository
                        .findActiveByOrganizationAndEmployeeAndFiscalYear(
        emp.getOrganization().getOrganizationId(),
        emp.getEmployeeId(),
        fiscalYear
)

                        .orElseGet(() -> {
                            log.warn("[{}] ⚠ No active EmployeeTds → invoking DefaultTdsCreationService", method);
                            return defaultTdsCreationService.createDefaultTdsIfNotExists(
                                    emp.getOrganization().getOrganizationId(),
                                    emp.getEmployeeId(),
                                    fiscalYear
                            );
                        });

        if (empTds == null || empTds.getFinalAnnualTax() == null) {
            log.warn("[{}] ❌ EmployeeTds missing tax → TDS=0", method);
            return BigDecimal.ZERO;
        }

        BigDecimal finalAnnualTax = empTds.getFinalAnnualTax();

        /*
        * ============================================================
        * 3️⃣ Already deducted TDS in this FY (CORRECT FY RANGE)
        * ============================================================
        */
        LocalDate fyStart = getFyStartDate(fiscalYear);
        LocalDate fyEnd   = getFyEndDate(fiscalYear);

        BigDecimal alreadyPaidTds =
                employeePayRunRepository
                        .sumMonthlyTdsByOrgEmployeeAndFyRange(
        emp.getOrganization().getOrganizationId(),
        emp.getEmployeeId(),
        fyStart,
        fyEnd
)

                        .orElse(BigDecimal.ZERO);

        log.info("[{}] finalAnnualTax={} alreadyPaidTds={}",
                method, finalAnnualTax, alreadyPaidTds);

        BigDecimal remainingTax = finalAnnualTax.subtract(alreadyPaidTds);

        if (remainingTax.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("[{}] ✅ Tax already fully recovered → TDS=0", method);
            return BigDecimal.ZERO;
        }

        /*
        * ============================================================
        * 4️⃣ Effective month handling
        * ============================================================
        */
        Month effectiveMonth = Month.valueOf(empTds.getEffectiveFromMonth().toUpperCase());
        YearMonth effectiveYm = getPoiStartYearMonth(fiscalYear, effectiveMonth);

        if (payYm.isBefore(effectiveYm)) {
            log.info("[{}] ⏭ Before effective month → TDS=0", method);
            return BigDecimal.ZERO;
        }

        // Start counting from max(effectiveYm, payYm)
        YearMonth startYm = payYm.isAfter(effectiveYm) ? payYm : effectiveYm;

        /*
        * ============================================================
        * 5️⃣ Remaining months till March
        * ============================================================
        */
        YearMonth marchYm = YearMonth.of(fiscalYear, Month.MARCH);

        int remainingMonths =
                (marchYm.getYear() - startYm.getYear()) * 12
                + (marchYm.getMonthValue() - startYm.getMonthValue())
                + 1;

        if (remainingMonths <= 0) {
            log.warn("[{}] ❌ Invalid remainingMonths → TDS=0", method);
            return BigDecimal.ZERO;
        }

        /*
        * ============================================================
        * 6️⃣ FINAL MONTHLY TDS
        * ============================================================
        */
        BigDecimal monthlyTds =
                remainingTax.divide(
                        BigDecimal.valueOf(remainingMonths),
                        2,
                        RoundingMode.HALF_UP
                );

        log.info("[{}] ✅ FINAL | remainingTax={} remainingMonths={} monthlyTds={}",
                method, remainingTax, remainingMonths, monthlyTds);

        return monthlyTds;
    }



//    public BigDecimal calculateProfessionalTax(BasicDetails emp, BigDecimal monthlySalary) {
//
//        final String method = "calculateProfessionalTax";
//        log.info("[{}] ▶ Starting professional tax calculation", method);
//
//        // 1️⃣ Validate inputs
//        if (emp == null) {
//            log.warn("[{}] ❌ Employee is null", method);
//            return BigDecimal.ZERO;
//        }
//        if (monthlySalary == null) {
//            log.warn("[{}] ❌ Monthly salary is null for employee {}", method, emp.getEmployeeNumber());
//            return BigDecimal.ZERO;
//        }
//
////        if ("female".equalsIgnoreCase(emp.getGender())) {
////            log.info("[{}] 🚺 Employee {} is Female → PT = 0 as per rule",
////                     method, emp.getEmployeeNumber());
////            return BigDecimal.ZERO;
////        }
//
//        if ("female".equalsIgnoreCase(emp.getGender())
//                && monthlySalary.compareTo(BigDecimal.valueOf(25000)) < 0) {
//
//            log.info("[{}] 🚺 Female employee {} with salary {} < 25000 → PT = 0",
//                    method, emp.getEmployeeNumber(), monthlySalary);
//
//            return BigDecimal.ZERO;
//        }
//
//        // 2️⃣ Get employee state
//        WorkLocation wl = emp.getWorkLocation();
//        if (wl == null || wl.getState() == null) {
//            log.warn("[{}] ⚠ No work location/state found for employee {}", method, emp.getEmployeeNumber());
//            return BigDecimal.ZERO;
//        }
//
//        String employeeState = wl.getState();
//        log.info("[{}] 🌍 Employee State = {}", method, employeeState);
//
//        // 3️⃣ Get PT list
//        List<ProfessionalTax> ptList = emp.getOrganization().getProfessionalTaxes();
//        if (ptList == null || ptList.isEmpty()) {
//            log.warn("[{}] ⚠ No Professional Tax data found for organization {}",
//                     method, emp.getOrganization().getOrganizationName());
//            return BigDecimal.ZERO;
//        }
//
//        // 4️⃣ Find PT for this state
//        ProfessionalTax statePT = null;
//
//        for (ProfessionalTax pt : ptList) {
//            log.info("[{}] 🔍 Checking PT State={} for employeeState={}",
//                     method, pt.getState(), employeeState);
//
//            if (employeeState.equalsIgnoreCase(pt.getState())) {
//                statePT = pt;
//                log.info("[{}] ✅ Matched Professional Tax for state {}", method, employeeState);
//                break;
//            }
//        }
//
//        if (statePT == null) {
//            log.warn("[{}] ⚠ No Professional Tax found for state {}", method, employeeState);
//            return BigDecimal.ZERO;
//        }
//
//        // 5️⃣ Effective-from validation
//        try {
//            LocalDate effectiveFromDate = LocalDate.parse(statePT.getEffectiveFrom());
//            LocalDate today = LocalDate.now();
//
//            if (today.isBefore(effectiveFromDate)) {
//                log.warn("[{}] ❌ PT not effective yet. EffectiveFrom={}, Today={}",
//                         method, effectiveFromDate, today);
//                return BigDecimal.ZERO;
//            }
//
//            log.info("[{}] 📅 EffectiveFrom={} is valid. PT applies.", method, effectiveFromDate);
//
//        } catch (Exception e) {
//            log.error("[{}] ❌ Invalid effectiveFrom format: {}", method, statePT.getEffectiveFrom());
//            return BigDecimal.ZERO;
//        }
//
//     // Special rule: If February and salary > 10000, PT = 300
//        Month currentMonth = LocalDate.now().getMonth();
//        if (currentMonth == Month.FEBRUARY && monthlySalary.compareTo(BigDecimal.valueOf(10000)) > 0) {
//            log.info("[{}] 🟣 February Special Rule Applied → Salary={} > 10000 → PT = 300",
//                     method, monthlySalary);
//            return BigDecimal.valueOf(300);
//        }
//
//
//        // 6️⃣ Validate slabs
//        if (statePT.getSlabDetails() == null || statePT.getSlabDetails().isEmpty()) {
//            log.warn("[{}] ⚠ No slab details found for PT state {}", method, employeeState);
//            return BigDecimal.ZERO;
//        }
//
//        // 7️⃣ Find matching slab
//        BigDecimal finalPT = BigDecimal.ZERO;
//
//        for (SlabDetail slab : statePT.getSlabDetails()) {
//
//            BigDecimal start = BigDecimal.valueOf(slab.getStartAmount());
//            BigDecimal end = slab.getEndAmount() != null
//                    ? BigDecimal.valueOf(slab.getEndAmount())
//                    : null;
//            BigDecimal payAmount = BigDecimal.valueOf(slab.getPayAmount());
//
//            log.info("[{}] 🔹 Checking slab: start={} end={} payAmount={}",
//                     method, start, end, payAmount);
//
//            boolean inRangeStart = monthlySalary.compareTo(start) >= 0;
//            boolean inRangeEnd = (end == null) || (monthlySalary.compareTo(end) <= 0);
//
//            if (inRangeStart && inRangeEnd) {
//                finalPT = payAmount;
//                log.info("[{}] ✅ Salary={} falls in slab → ProfessionalTax={}",
//                         method, monthlySalary, finalPT);
//                break;
//            }
//        }
//
//        // 8️⃣ Final result
//        log.info("[{}] 🎯 Final Professional Tax for employee {} = {}",
//                 method, emp.getEmployeeNumber(), finalPT);
//
//        return finalPT;
//    }



public BigDecimal calculateProfessionalTax(BasicDetails emp, BigDecimal monthlySalary) {

    final String method = "calculateProfessionalTax";
    log.info("[{}] ▶ Starting professional tax calculation", method);

    // 1️⃣ Validate inputs
    if (emp == null) {
        log.warn("[{}] ❌ Employee is null", method);
        return BigDecimal.ZERO;
    }
    if (monthlySalary == null) {
        log.warn("[{}] ❌ Monthly salary is null for employee {}", method, emp.getEmployeeNumber());
        return BigDecimal.ZERO;
    }

    // 2️⃣ Female rule (unchanged – this is your org-level rule)
    if ("female".equalsIgnoreCase(emp.getGender())
            && monthlySalary.compareTo(BigDecimal.valueOf(25000)) < 0) {

        log.info("[{}] 🚺 Female employee {} with salary {} < 25000 → PT = 0",
                method, emp.getEmployeeNumber(), monthlySalary);

        return BigDecimal.ZERO;
    }

    // 3️⃣ Get employee state from WorkLocation
    WorkLocation wl = emp.getWorkLocation();
    if (wl == null || wl.getState() == null) {
        log.warn("[{}] ⚠ No work location/state found for employee {}", method, emp.getEmployeeNumber());
        return BigDecimal.ZERO;
    }

    String employeeState = wl.getState();
    log.info("[{}] 🌍 Employee State = {}", method, employeeState);

    // 4️⃣ Get organizationId (the one used in getAllProfessionalTaxes)
    if (emp.getOrganization() == null || emp.getOrganization().getOrganizationId() == null) {
        log.warn("[{}] ⚠ No organizationId found for employee {}", method, emp.getEmployeeNumber());
        return BigDecimal.ZERO;
    }

    String organizationId = emp.getOrganization().getOrganizationId();
    log.info("[{}] 🏢 OrganizationId for PT lookup = {}", method, organizationId);

    // 5️⃣ Fetch PT slabs (DTO) from MasterConfig + Override
    List<ProfessionalTaxDTO> ptDtos = professionalTaxService.getAllProfessionalTaxes(organizationId);

    if (ptDtos == null || ptDtos.isEmpty()) {
        log.warn("[{}] ⚠ No Professional Tax DTOs returned for organizationId={}", method, organizationId);
        return BigDecimal.ZERO;
    }

    // 6️⃣ Find DTO for this employee's state
    ProfessionalTaxDTO

            statePT = ptDtos.stream()
            .filter(dto -> dto.getState() != null &&
                    dto.getState().equalsIgnoreCase(employeeState))
            .findFirst()
            .orElse(null);

    if (statePT == null) {
        log.warn("[{}] ⚠ No PT slab config found for state={} (orgId={})",
                method, employeeState, organizationId);
        return BigDecimal.ZERO;
    }

    log.info("[{}] ✅ Found PT config for state={}", method, statePT.getState());

    // 7️⃣ Effective-from validation
    String effectiveFromStr = statePT.getEffectiveFrom(); // may be null for pure master
    if (effectiveFromStr != null && !effectiveFromStr.isBlank()) {
        try {
            LocalDate effectiveFromDate = LocalDate.parse(effectiveFromStr);
            LocalDate today = LocalDate.now();

            if (today.isBefore(effectiveFromDate)) {
                log.warn("[{}] ❌ PT not effective yet. EffectiveFrom={}, Today={}",
                        method, effectiveFromDate, today);
                return BigDecimal.ZERO;
            }

            log.info("[{}] 📅 EffectiveFrom={} is valid. PT applies.", method, effectiveFromDate);

        } catch (DateTimeParseException e) {
            log.error("[{}] ❌ Invalid effectiveFrom format: {}", method, effectiveFromStr);
            // if you want to be strict, return ZERO; if lenient, you can continue
            return BigDecimal.ZERO;
        }
    } else {
        log.info("[{}] ℹ No effectiveFrom specified (master config) → treating as always effective", method);
    }

    // ❌ REMOVE this old hard-coded February rule now.
    // It's properly handled by JSON using separate slabs and deductionMonths.
    //
    // Month currentMonth = LocalDate.now().getMonth();
    // if (currentMonth == Month.FEBRUARY && monthlySalary.compareTo(BigDecimal.valueOf(10000)) > 0) { ... }

    // 8️⃣ Validate slabDetails from DTO
    if (statePT.getSlabDetails() == null || statePT.getSlabDetails().isEmpty()) {
        log.warn("[{}] ⚠ No slab details found for PT state={} (DTO)", method, employeeState);
        return BigDecimal.ZERO;
    }

    // Month name like "February", "March" to compare with deductionMonths
    String currentMonthName = LocalDate.now()
            .getMonth()
            .getDisplayName(TextStyle.FULL, Locale.ENGLISH);

    log.info("[{}] 📆 Current month = {}", method, currentMonthName);

    BigDecimal finalPT = BigDecimal.ZERO;

    // 9️⃣ Find matching slab from DTOs
    for (SlabDetailDTO slab : statePT.getSlabDetails()) {

        BigDecimal start = BigDecimal.valueOf(slab.getStartAmount());
        BigDecimal end = slab.getEndAmount() != null
                ? BigDecimal.valueOf(slab.getEndAmount())
                : null;
        BigDecimal payAmount = BigDecimal.valueOf(slab.getPayAmount());

        // 9.1️⃣ Month filtering rule:
        //  - empty/null deductionMonths => applies for all months
        //  - non-empty => applies ONLY if current month exists in that list
        List<String> deductionMonths = slab.getDeductionMonths();
        boolean monthMatches;

        if (deductionMonths == null || deductionMonths.isEmpty()) {
            monthMatches = true; // all months
        } else {
            monthMatches = deductionMonths.stream()
                    .anyMatch(m -> m.equalsIgnoreCase(currentMonthName));
        }

        if (!monthMatches) {
            log.debug("[{}] ⏭ Skipping slab id={} because month={} not in deductionMonths={}",
                    method, slab.getId(), currentMonthName, deductionMonths);
            continue;
        }

        log.info("[{}] 🔹 Checking slab id={} start={} end={} payAmount={} months={}",
                method, slab.getId(), start, end, payAmount, deductionMonths);

        boolean inRangeStart = monthlySalary.compareTo(start) >= 0;
        boolean inRangeEnd = (end == null) || (monthlySalary.compareTo(end) <= 0);

        if (inRangeStart && inRangeEnd) {
            finalPT = payAmount;
            log.info("[{}] ✅ Salary={} falls in slab id={} → ProfessionalTax={}",
                    method, monthlySalary, slab.getId(), finalPT);
            break;
        }
    }

    // 🔟 Final result
    log.info("[{}] 🎯 Final Professional Tax for employee {} = {}",
            method, emp.getEmployeeNumber(), finalPT);

    return finalPT;
}

    private BigDecimal calculateEpfEmployer(BasicDetails emp, LocalDate periodDate) {
        final String method = "calculateEpfEmployer";

        log.info("[{}] 🔍 Starting EPF_EMPLOYER calculation for employee {}", method, emp.getEmployeeNumber());

        BigDecimal totalEpfEmployer = BigDecimal.ZERO;

        CtcStructure ctc = ctcStructureRepository
                .findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        emp.getOrganization().getOrganizationId(),
                        emp.getId(),
                        periodDate
                )
                .orElse(null);

        if (ctc == null) {
            log.warn("[{}] ⚠️ No ACTIVE CTC structure found for employee {}", method, emp.getEmployeeNumber());
            return totalEpfEmployer.setScale(2, RoundingMode.HALF_UP);
        }


        if (ctc.getEpfComponents() == null || ctc.getEpfComponents().isEmpty()) {
            log.info("[{}] ⚠️ No EPF components found for employee {}", method, emp.getEmployeeNumber());
            return totalEpfEmployer.setScale(2, RoundingMode.HALF_UP);
        }

        log.info("[{}] 🗂 Found {} EPF components for employee {}", method, ctc.getEpfComponents().size(), emp.getEmployeeNumber());

        for (CtcEpfComponent epf : ctc.getEpfComponents()) {
            if (epf == null) {
                log.warn("[{}] ⚠️ Null EPF component found, skipping", method);
                continue;
            }

            String code = epf.getComponentCode();
            BigDecimal amt = epf.getMonthlyAmount() != null ? epf.getMonthlyAmount() : BigDecimal.ZERO;

            log.info("[{}] 🔹 Checking EPF component | Emp={} | Code={} | Amount={}", method, emp.getEmployeeNumber(), code, amt);

            if ("EPF_EMPLOYER".equals(code)) {
                totalEpfEmployer = totalEpfEmployer.add(amt);
                log.info("[{}] ✅ Added to total EPF_EMPLOYER | Emp={} | Amount={} | Running Total={}",
                        method, emp.getEmployeeNumber(), amt, totalEpfEmployer);
            } else {
                log.info("[{}] ⚠️ Ignored EPF component | Emp={} | Code={} | Amount={}", method, emp.getEmployeeNumber(), code, amt);
            }
        }

        log.info("[{}] ➕ Total EPF_EMPLOYER for employee {} = {}", method, emp.getEmployeeNumber(), totalEpfEmployer);
        return totalEpfEmployer.setScale(2, RoundingMode.HALF_UP);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<EmployeePayRunDTO> generateEmployeePayRuns(String organizationId, PayRun payRun) {
        String method = "generateEmployeePayRuns";
        log.info("[{}] 🚀 Starting generation for organizationId={} | payrunId={}", method, organizationId, payRun.getPayrunId());

            // ✅ Use the PayRun's processing period
            String processingPeriod = payRun.getProcessingPeriod();




        // Fetch employees and map to DTOs
        List<EmployeePayRunDTO> employeeList = getEmployeePayRunList(organizationId, processingPeriod);
        log.info("[{}] ✅ Retrieved {} employees for pay run creation", method, employeeList.size());


        // ----------------------------------------------------
// Apply revision CTCs for this payrun (activation step)
// ----------------------------------------------------

        LocalDate periodStart = payRun.getPayPeriodStartDate();
        LocalDate periodEnd   = payRun.getPayPeriodEndDate();

        for (EmployeePayRunDTO dto : employeeList) {

            BasicDetails employee = basicDetailsRepository
                    .findByEmployeeIdAndOrganization_OrganizationIdAndIsDeletedFalse(
                dto.getEmployeeId(),
                organizationId
        )
                    .orElse(null);

            if (employee == null) {
                continue;
            }

            CtcStructure ctcToApply =
                    ctcStructureRepository
                            .findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                                    organizationId,
                                    employee.getId(),
                                    periodEnd
                            )
                            .orElse(null);

            if (ctcToApply == null) {
                continue;
            }

            // If this CTC is already applied earlier, skip
            if (Boolean.TRUE.equals(ctcToApply.getAppliedInPayrun())) {
                continue;
            }

            // deactivate previous active CTCs
            ctcStructureRepository.deactivateOtherActiveCtcs(
                    organizationId,
                    employee.getId(),
                    ctcToApply.getId(),
                    LocalDateTime.now()
            );

            // mark this revision as active & applied
            ctcToApply.setActive(true);
            ctcToApply.setAppliedInPayrun(true);
            ctcToApply.setUpdatedAt(LocalDateTime.now());

            ctcStructureRepository.save(ctcToApply);
        }



        // Collect all employee workEmails
        log.info("[{}] 📧 Collecting employee work emails", method);
        Map<String, String> employeeIdToEmail = employeeList.stream()
                .map(EmployeePayRunDTO::getEmployeeId)
                .map(empId -> basicDetailsRepository.findByEmployeeId(empId)
                        .map(basic -> Map.entry(empId, basic.getWorkMail()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<String> employeeEmails = new ArrayList<>(employeeIdToEmail.values());
        log.info("[{}] ✅ Collected {} list of valid employee emails", method, employeeEmails);
        log.info("[{}] ✅ Collected {} valid employee emails", method, employeeEmails.size());

        // Get pay period and call HRMS API
        String payPeriod = payRun.getProcessingPeriod();
        Map<String, Double> leaveData = integrateWithHrmsService.fetchLeaves(employeeEmails, payPeriod);

        // Map DTOs → Entities and link PayRun & Organization
        List<EmployeePayRun> employeePayRuns = employeeList.stream()
                .map(dto -> {
                    EmployeePayRun e = new EmployeePayRun();

                    // Link PayRun and Organization
                    e.setPayRun(payRun);                // JPA @ManyToOne                 
                    e.setPayrunId(payRun.getPayrunId());            // Also store payrunId 

                    // Map employee fields
                    e.setEmployeeId(dto.getEmployeeId());
                    e.setEmployeeNumber(dto.getEmployeeNumber());
                    e.setEmployeeName(dto.getEmployeeName());
                    e.setPaymentMode(dto.getPaymentMode());
                    e.setPaymentStatus("YET_TO_PAY");
                    e.setTotalEarnings(dto.getTotalEarnings());
                    e.setTotalBenefits(dto.getTotalBenefits());
                    e.setTotalDeductions(dto.getTotalDeductions());
                    e.setTotalTaxes(dto.getTotalTaxes());
                    e.setTotalReimbursements(dto.getTotalReimbursements());
//                    e.setNetPay(dto.getNetPay());
                    e.setMonthlySalary(dto.getMonthlySalary());
//                    e.setPaidDays(dto.getPaidDays());
                    e.setEmployeeStatus(dto.getEmployeeStatus());
                    Optional<BasicDetails> employees = basicDetailsRepository.findByEmployeeId(dto.getEmployeeId());


                    employees.ifPresent(e::setEmployee);

                    //Fetch work email and set total leaves
                    String workEmail = employees.map(BasicDetails::getWorkMail).orElse(null);
                    Double leaves = (workEmail != null) ? leaveData.getOrDefault(workEmail, 0.0) : 0.0;
                    e.setTotalNoOfLeaves(leaves);

                    // ===== New logic: adjust paidDays and netPay based on leaves =====

                    long totalPeriodDays =
                            ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;

                    String dojString = employees.map(BasicDetails::getDateOfJoining).orElse(null);

                    LocalDate doj = null;

                    if (dojString != null && !dojString.isBlank()) {
                        doj = LocalDate.parse(dojString);
                    }

                    LocalDate effectiveStart = (doj != null && doj.isAfter(periodStart))
                            ? doj
                            : periodStart;

                    long eligibleDays = 0;

                    if (!effectiveStart.isAfter(periodEnd)) {
                        eligibleDays =
                                ChronoUnit.DAYS.between(effectiveStart, periodEnd) + 1;
                    }

                    e.setPaidDays((double) eligibleDays);


                    Double monthlySalary = dto.getMonthlySalary();
                    Double originalNetPay = dto.getNetPay();

                    if (monthlySalary == null) {
                        monthlySalary = 0.0;
                    }

                    if (originalNetPay == null) {
                        originalNetPay = 0.0;
                    }

                    Double perDayPay = monthlySalary / totalPeriodDays;

// cap leaves
                    leaves = Math.min(leaves, (double) eligibleDays);

                    Double lopAmount = perDayPay * leaves;
                    e.setLOP(lopAmount);

// ⭐ prorate NET PAY (important for DOJ cases)
                    Double perDayNetPay = originalNetPay / totalPeriodDays;
                    Double proratedNetPay = perDayNetPay * eligibleDays;

                    Double finalNetPay = proratedNetPay - lopAmount;
                    Double bonusToAdd = dto.getBonus() != null ? dto.getBonus() : 0.0;
                    finalNetPay += bonusToAdd;

                    if (finalNetPay < 0) {
                        finalNetPay = 0.0;
                    }

                    e.setBonus(bonusToAdd);
                    e.setBonusEarningExistForEmployee(dto.isBonusEarningExistForEmployee());
                    e.setNetPay(finalNetPay);
                    // ==================================================================

                    log.info("[{}] 🗓️ Employee={} | Email={} | Leaves={}",
                            method, dto.getEmployeeNumber(), workEmail, leaves);
                    e.setMonthlyTds(dto.getMonthlyTds());
                    e.setClaimDeduction(dto.getClaimDeduction());
                    e.setClaimReimbursement(dto.getClaimReimbursement());
                    e.setClaimDeductionStatus(dto.getClaimDeductionStatus());
                    e.setClaimReimbursementStatus(dto.getClaimReimbursementStatus());

                    return e;
                    
                })
                .collect(Collectors.toList());

        // Save all employee pay runs
        employeePayRunRepository.saveAll(employeePayRuns);
        log.info("[{}] 💾 Successfully saved {} employee pay run entries for payrunId={}", 
                 method, employeePayRuns.size(), payRun.getPayrunId());

        // ✅ Update claim deduction statuses from ACTIVE → INPAYRUN
        String processingPeriodTrimmed = processingPeriod.trim();
        YearMonth ym = YearMonth.parse(processingPeriodTrimmed, DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        LocalDate deductionMonth = ym.atDay(1);

        List<SalaryDeduction> activeDeductions = salaryDeductionRepository
                .findByOrganizationIdAndDeductionMonthAndStatus(organizationId, deductionMonth, DeductionStatus.ACTIVE);
        for (SalaryDeduction d : activeDeductions) {
            d.setStatus(DeductionStatus.INPAYRUN);
        }
        if (!activeDeductions.isEmpty()) {
            salaryDeductionRepository.saveAll(activeDeductions);
            log.info("[{}] ✅ Updated {} deductions to INPAYRUN for month={}", method, activeDeductions.size(), deductionMonth);
        }

        // ✅ Update claim reimbursement statuses from UNPAID → INPAYRUN
        String reimbMonthFormatted = ym.getYear() + "-" + String.format("%02d", ym.getMonthValue());
        List<EmployeeReimbursementRequest> unpaidReimbursements = employeeReimbursementRequestRepository
                .findByOrganizationIdAndReimbursementMonthAndPaymentStatus(
                        organizationId, reimbMonthFormatted, ReimbursementPaymentStatus.UNPAID);
        // Only update APPROVED ones
        List<EmployeeReimbursementRequest> approvedUnpaid = unpaidReimbursements.stream()
                .filter(r -> r.getStatus() == ReimbursementStatus.APPROVED)
                .collect(Collectors.toList());
        for (EmployeeReimbursementRequest r : approvedUnpaid) {
            r.setPaymentStatus(ReimbursementPaymentStatus.INPAYRUN);
            r.setPayrunId(payRun.getPayrunId());
        }
        if (!approvedUnpaid.isEmpty()) {
            employeeReimbursementRequestRepository.saveAll(approvedUnpaid);
            log.info("[{}] ✅ Updated {} reimbursements to INPAYRUN for month={}", method, approvedUnpaid.size(), processingPeriodTrimmed);
        }

        return employeeList;
    }
    
    @Override
    public List<EmployeePayRunDTO> getEmployeePayRunListByPayRun(String organizationId, String payrunId) {
        String method = "getEmployeePayRunListByPayRun";
        log.info("[{}] 🔍 Start fetching employee pay runs | orgId={} | payrunId={}", method, organizationId, payrunId);

        // Step 1️⃣ - Fetch Organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> {
                    log.error("[{}] ❌ Organization not found for ID={}", method, organizationId);
                    return new RuntimeException("Organization not found");
                });
        log.info("[{}] ✅ Organization found: {}", method, org.getOrganizationName());

        // Step 2️⃣ - Fetch PayRun
        PayRun payRun = payRunRepository.findByPayrunIdAndOrganization(payrunId, org)
                .orElseThrow(() -> {
                    log.error("[{}] ❌ PayRun not found for payrunId={} in organizationId={}", method, payrunId, organizationId);
                    return new RuntimeException("PayRun not found: " + payrunId);
                });
        log.info("[{}] ✅ PayRun found: payrunId={} | type={} | status={}", method, payRun.getPayrunId(), payRun.getType(), payRun.getStatus());

        // Step 3️⃣ - Fetch EmployeePayRuns via JPA relation
        List<EmployeePayRun> employeePayRuns = payRun.getEmployeePayRuns();
        log.info("[{}] 🔍 Retrieved {} employee pay run records from PayRun entity", method, employeePayRuns.size());




//        LocalDate periodDate = YearMonth.parse(
//                payRun.getProcessingPeriod(),
//                DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
//        ).atDay(1);

        // Only New Logic added for effective date checks
        YearMonth yearMonth = YearMonth.parse(
                payRun.getProcessingPeriod(),
                DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
        );

        LocalDate periodDate = yearMonth.atEndOfMonth();

        log.info("[{}] 📅 Using PayRun processingPeriod={} => periodDate={}",
                method, payRun.getProcessingPeriod(), periodDate);



        // Step 4️⃣ - Map EmployeePayRun entities to DTOs (with Effective Date CTC)
        List<EmployeePayRunDTO> dtos = employeePayRuns.stream()
                .map(e -> {
                    CtcStructure applicableCtc = null;

                    if (e.getEmployee() != null) {
                        applicableCtc = ctcStructureRepository
                                .findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                                        organizationId,
                                        e.getEmployee().getId(),
                                        periodDate
                                )
                                .orElse(null);
                    }

                    return EmployeePayRunMapper.toDto(e, applicableCtc);
                })
                .collect(Collectors.toList());



        log.info("[{}] 🏁 Completed mapping {} employee pay run DTOs for payrunId={}", method, dtos.size(), payrunId);

        return dtos;
    }
    
    public Page<EmployeePayslipDTO> getEmployeePayslips(
            String organizationId,
            String employeeId,
            int page,
            int size,
            Integer year
    ) {
        String method = "getEmployeePayslips";

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "payRun.payDate"));
        Page<EmployeePayRun> payRunPage;
        String paymentStatus = "PAID"; // Only paid payslips

        if (year != null) {
            LocalDate startDate = LocalDate.of(year, 1, 1);
            LocalDate endDate = LocalDate.of(year, 12, 31);

            log.info("[{}] 🎯 Applying year filter | startDate={} | endDate={}", method, startDate, endDate);

            payRunPage = employeePayRunRepository
                    .findByPayRun_Organization_OrganizationIdAndEmployeeIdAndPaymentStatusAndPayRun_PayDateBetween(
                            organizationId, employeeId, paymentStatus, startDate, endDate, pageable);
            log.info("[{}] 🎯 Filtered {} payslips found for year {}", method, payRunPage.getTotalElements(), year);
        } else {
            payRunPage = employeePayRunRepository
                    .findByPayRun_Organization_OrganizationIdAndEmployeeIdAndPaymentStatus(
                            organizationId, employeeId, paymentStatus, pageable);
            log.info("[{}] 🔹 Fetched {} payslips without year filter", method, payRunPage.getTotalElements());
        }

        Page<EmployeePayslipDTO> dtoPage = payRunPage.map(epr -> {
            EmployeePayslipDTO dto = toDTO(epr);
            log.info("[{}] 🔹 Mapping EmployeePayRun -> EmployeePayslipDTO | payrunId={} | employee number={}",
                    method, epr.getPayrunId(), epr.getEmployeeNumber());
            return dto;
        });

        log.info("[{}] ✅ Returning {} EmployeePayslipDTO entries", method, dtoPage.getNumberOfElements());
        return dtoPage;
    }
    private EmployeePayslipDTO toDTO(EmployeePayRun epr) {

        EmployeePayslipDTO dto = new EmployeePayslipDTO();

        dto.setPayrunId(epr.getPayrunId());
        dto.setPaymentStatus(epr.getPaymentStatus());
        dto.setGrossEarnings(epr.getTotalEarnings());
        dto.setGrossDeductions(epr.getTotalDeductions());
        dto.setNetPay(epr.getNetPay());
        dto.setGrossReimbursements(epr.getTotalReimbursements());
        dto.setPayDate(epr.getPayRun().getPayDate());
        dto.setPayrollType(epr.getPayRun().getType().name());
        dto.setPayPeriod(epr.getPayRun().getProcessingPeriod());
        dto.setTotalTaxes(epr.getTotalTaxes());

        dto.setMonthlyTds(epr.getMonthlyTds());
        dto.setPaidDays(epr.getPaidDays());

        // 🔹 LOP
        dto.setLop(epr.getLOP());

        dto.setTotalNoOfLeaves(epr.getTotalNoOfLeaves());

        // ==========================================================
        // ✅ ADD EPF + ESI FROM ACTIVE CTC STRUCTURE
        // ==========================================================
// ✅ ADD EPF + ESI FROM CTC STRUCTURE (effectiveDate based for payslip month)
        CtcStructure ctc = null;

        if (epr.getEmployee() != null) {

//            LocalDate periodDate = YearMonth.parse(
//                    epr.getPayRun().getProcessingPeriod(),
//                    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
//            ).atDay(1);

            // Only New Logic added for effective date checks
            YearMonth yearMonth = YearMonth.parse(
                    epr.getPayRun().getProcessingPeriod(),
                    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
            );

            LocalDate periodDate = yearMonth.atEndOfMonth();

            ctc = ctcStructureRepository
                    .findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                            epr.getEmployee().getOrganization().getOrganizationId(),
                            epr.getEmployee().getId(),
                            periodDate
                    )
                    .orElse(null);
        }


        if (ctc != null) {

            // EPF
            if (ctc.getEpfComponents() != null) {
                dto.setEpfComponents(
                        ctc.getEpfComponents().stream().map(epf -> {
                            EpfComponentDTO ed = new EpfComponentDTO();
                            ed.setComponentCode(epf.getComponentCode());
                            ed.setComponentLabel(epf.getComponentLabel());
                            ed.setPercentage(epf.getPercentage());
                            ed.setMonthlyAmount(epf.getMonthlyAmount());
                            ed.setAnnualAmount(epf.getAnnualAmount());
                            ed.setCalculationType(epf.getCalculationType());
                            return ed;
                        }).toList()
                );
            }

            // ESI
            if (ctc.getEsiComponents() != null) {
                dto.setEsiComponents(
                        ctc.getEsiComponents().stream().map(esi -> {
                            EsiComponentDTO es = new EsiComponentDTO();
                            es.setComponentCode(esi.getComponentCode());
                            es.setComponentLabel(esi.getComponentLabel());
                            es.setPercentage(esi.getPercentage());
                            es.setMonthlyAmount(esi.getMonthlyAmount());
                            es.setAnnualAmount(esi.getAnnualAmount());
                            es.setCalculationType(esi.getCalculationType());
                            return es;
                        }).toList()
                );
            }
        }

        return dto;
    }

    
    @Override
    public PayslipResponseDTO getEmployeePayslip(String organizationId, String employeeId, String payRunId) {
        String method = "getEmployeePayslip";
        log.info("========== [{}] START Payslip Generation ==========", method);
        log.info("[{}] 📥 Request received | orgId={} | employeeId={} | payRunId={}",
                method, organizationId, employeeId, payRunId);

        // --- Fetch Employee PayRun ---
        log.info("[{}] 🔍 Fetching EmployeePayRun from repository...", method);
        EmployeePayRun epr = employeePayRunRepository.findEmployeePayRun(organizationId, employeeId, payRunId)
                .orElseThrow(() -> {
                    log.error("[{}] ❌ Payslip not found | orgId={} | employeeId={} | payRunId={}",
                            method, organizationId, employeeId, payRunId);
                    return new RuntimeException("Payslip not found");
                });

        log.info("[{}] ✅ EmployeePayRun fetched successfully | EPR ID={} | Status={}",
                method, epr.getId(), epr.getPaymentStatus());

        BasicDetails emp = epr.getEmployee();
        PayRun payRun = epr.getPayRun();
        Organization org = emp.getOrganization();
        
        log.info("[{}] 👤 Employee Data: ID={} | Name={} {} | EmpNo={} | Designation={}",
                method, emp.getEmployeeId(), emp.getFirstName(), emp.getLastName(),
                emp.getEmployeeNumber(),
                emp.getDesignation() != null ? emp.getDesignation().getName() : "N/A");
        
        log.info("[{}] 📅 PayRun Data: ID={} | Period={} | Type={} | PayDate={}",
                method, payRun.getPayrunId(), payRun.getProcessingPeriod(), payRun.getType().name(), payRun.getPayDate());

        // --- Employee Summary ---
        log.info("[{}] 🧾 Building EmployeeSummaryDTO...", method);
        EmployeeSummaryDTO empSummary = new EmployeeSummaryDTO();
        empSummary.setEmployeeNumber(emp.getEmployeeNumber());
        empSummary.setFirstName(emp.getFirstName());
        empSummary.setMiddleName(emp.getMiddleName());
        empSummary.setLastName(emp.getLastName());
        empSummary.setFullName(emp.getFirstName() + " " + emp.getLastName());
        empSummary.setDesignation(emp.getDesignation() != null ? emp.getDesignation().getName() : null);
        empSummary.setDateOfJoining(emp.getDateOfJoining());
        empSummary.setEmployeeStatus(emp.getEmployeeStatus());
        empSummary.setWorkEmail(emp.getWorkMail());
        empSummary.setPfNumber(emp.getPfAccountNumber());
        empSummary.setUanNumber(emp.getUan());
        empSummary.setMobile(emp.getMobile());
        log.info("[{}] ✅ EmployeeSummaryDTO built successfully for employeeNumber={}", method, emp.getEmployeeNumber());
        
        log.info("[{}] 🏢 Fetching Filing Address WorkLocation...", method);
        WorkLocationDTO workLocationDTO = null;
        if (org.getWorkLocations() != null && !org.getWorkLocations().isEmpty()) {
            workLocationDTO = org.getWorkLocations().stream()
                    .filter(w -> Boolean.TRUE.equals(w.getIsFilingAddress()))
                    .findFirst()
                    .map(w -> {
                        WorkLocationDTO wl = new WorkLocationDTO();
                        wl.setWorkLocationName(w.getWorkLocationName());
                        wl.setStreetAddress1(w.getStreetAddress1());
                        wl.setStreetAddress2(w.getStreetAddress2());
                        wl.setCity(w.getCity());
                        wl.setState(w.getState());
                        wl.setZipCode(w.getZipCode());
                        wl.setCountry(w.getCountry());
                        return wl;
                    })
                    .orElse(null);

            if (workLocationDTO != null) {
                log.info("[{}] ✅ WorkLocation Found | Name={} | City={} | State={}", method,
                        workLocationDTO.getWorkLocationName(), workLocationDTO.getCity(), workLocationDTO.getState());
            } else {
                log.warn("[{}] ⚠️ No filing address found in organization's work locations.", method);
            }
        } else {
            log.warn("[{}] ⚠️ Organization has no work locations configured.", method);
        }
        


        // --- Payroll Summary ---
        log.info("[{}] 💰 Preparing PayrollSummaryDTO...", method);
        PayrollSummaryDTO summary = new PayrollSummaryDTO();
        summary.setGrossPay(epr.getTotalEarnings());
        summary.setGrossEarnings(epr.getTotalEarnings());
        summary.setTotalDeductions(epr.getTotalDeductions());
        summary.setNetPay(epr.getNetPay());
        summary.setPayMonth(payRun.getProcessingPeriod());

        // NEW: set professional tax
        summary.setProfessionalTax(epr.getTotalTaxes()); // or epr.getProfessionalTax() if your getter name differs
        // NEW: include bonus and bonus flag from EmployeePayRun
        summary.setBonus(epr.getBonus());
        summary.setBonusEarningExistForEmployee(epr.getBonusEarningExistForEmployee());
        log.info("[{}] ✅ PayrollSummaryDTO built | Gross={} | Deductions={} | Net={}",
                method, epr.getTotalEarnings(), epr.getTotalDeductions(), epr.getNetPay());

        // --- Earnings Components ---
        log.info("[{}] 🧮 Building Earning Components...", method);
     //   List<EarningComponentDTO> earnings = buildEarnings(emp);


//        LocalDate periodDate = YearMonth.parse(
//                payRun.getProcessingPeriod(),
//                DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
//        ).atDay(1);

        // Only New Logic added for effective date checks
        YearMonth yearMonth = YearMonth.parse(
                payRun.getProcessingPeriod(),
                DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
        );

        LocalDate periodDate = yearMonth.atEndOfMonth();

        List<EarningComponentDTO> earnings = buildEarnings(emp, periodDate);






        log.info("[{}] ✅ {} earning components built for employeeId={}", method, earnings.size(), employeeId);








        // --- Payslip DTO ---
        log.info("[{}] 🧷 Assembling PayslipResponseDTO...", method);
        PayslipResponseDTO dto = new PayslipResponseDTO();
        dto.setPayRunId(payRun.getPayrunId());
        dto.setPayRunType(payRun.getType().name());
        dto.setPayPeriod(payRun.getProcessingPeriod());
        dto.setPayDate(payRun.getPayDate().toString());
        dto.setGrossEarnings(epr.getTotalEarnings());
        dto.setTotalDeductions(epr.getTotalDeductions());
        dto.setTotalReimbursements(epr.getTotalReimbursements());
        dto.setNetPay(epr.getNetPay());
        dto.setPaymentStatus(epr.getPaymentStatus());
        dto.setEmployeeSummary(empSummary);
        dto.setPayrollSummary(summary);
        dto.setEarningComponents(earnings);
        dto.setMonthlyTds(epr.getMonthlyTds());
        dto.setClaimDeduction(epr.getClaimDeduction());
        dto.setClaimReimbursement(epr.getClaimReimbursement());



        // ⭐ ADD THIS LINE HERE
        dto.setPaidDays(epr.getPaidDays());

        // set LOP and total leaves
        dto.setLop(epr.getLOP());
        dto.setTotalNoOfLeaves(epr.getTotalNoOfLeaves());

        log.info("[{}] 🔢 Payslip values: paidDays={}, lop={}, totalNoOfLeaves={}",
        method, epr.getPaidDays(), epr.getLOP(), epr.getTotalNoOfLeaves());


        // --- Add Organization & WorkLocation info ---
        log.info("[{}] ✅ Organization Info Attached | OrgName={} | FileUrl={}", method,
                org.getOrganizationName(), org.getFileUrl());
        
        dto.setOrganizationName(org.getOrganizationName());
        dto.setOrganizationFileUrl(org.getFileUrl());
        dto.setWorkLocation(workLocationDTO);

// --- EPF + ESI Components (effectiveDate based for this PayRun month) ---
        CtcStructure ctc = ctcStructureRepository
                .findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        emp.getOrganization().getOrganizationId(),
                        emp.getId(),
                        periodDate
                )
                .orElse(null);



        if (ctc != null) {

            // EPF
            if (ctc.getEpfComponents() != null) {
                dto.setEpfComponents(
                        ctc.getEpfComponents().stream().map(epf -> {
                            EpfComponentDTO ed = new EpfComponentDTO();
                            ed.setComponentCode(epf.getComponentCode());
                            ed.setComponentLabel(epf.getComponentLabel());
                            ed.setPercentage(epf.getPercentage());
                            ed.setMonthlyAmount(epf.getMonthlyAmount());
                            ed.setAnnualAmount(epf.getAnnualAmount());
                            ed.setCalculationType(epf.getCalculationType());
                            return ed;
                        }).toList()
                );
            }

            // ESI
            if (ctc.getEsiComponents() != null) {
                dto.setEsiComponents(
                        ctc.getEsiComponents().stream().map(esi -> {
                            EsiComponentDTO es = new EsiComponentDTO();
                            es.setComponentCode(esi.getComponentCode());
                            es.setComponentLabel(esi.getComponentLabel());
                            es.setPercentage(esi.getPercentage());
                            es.setMonthlyAmount(esi.getMonthlyAmount());
                            es.setAnnualAmount(esi.getAnnualAmount());
                            es.setCalculationType(esi.getCalculationType());
                            return es;
                        }).toList()
                );
            }
        }


        log.info("[{}] ✅ PayslipResponseDTO assembled successfully for employeeId={} | payRunId={}",
                method, employeeId, payRunId);
        log.info("========== [{}] END Payslip Generation ==========", method);
        return dto;
    }

    private List<EarningComponentDTO> buildEarnings(BasicDetails emp, LocalDate periodDate) {
        String method = "buildEarnings";
        log.info("[{}] 🏗️ Building earnings list for employee number={} | periodDate={}",
                method, emp.getEmployeeNumber(), periodDate);

        CtcStructure ctc = ctcStructureRepository
                .findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        emp.getOrganization().getOrganizationId(),
                        emp.getId(),
                        periodDate
                )
                .orElse(null);

        if (ctc == null) {
            log.warn("[{}] ⚠️ Applicable CTC is null for employeeId={} | periodDate={}",
                    method, emp.getEmployeeId(), periodDate);
            return Collections.emptyList();
        }

        if (ctc.getEarnings() == null || ctc.getEarnings().isEmpty()) {
            log.warn("[{}] ⚠️ No earnings found in applicable CTC for employee number={} | periodDate={}",
                    method, emp.getEmployeeNumber(), periodDate);
            return Collections.emptyList();
        }

        List<EarningComponentDTO> earnings = ctc.getEarnings().stream()
                .peek(earn -> log.info("[{}] ➕ Mapping Earning: name={} | amount={}",
                        method, earn.getEarning().getEarningName(), earn.getAmount()))
                .map(earn -> {
                    EarningComponentDTO dto = new EarningComponentDTO();
                    dto.setEarningName(earn.getEarning().getEarningName());
                    dto.setEarningAmount(earn.getAmount());
                    return dto;
                })
                .collect(Collectors.toList());

        log.info("[{}] ✅ Completed building {} earning components for employee number={}",
                method, earnings.size(), emp.getEmployeeNumber());

        return earnings;
    }



    @Override
    @Transactional(readOnly = true)
    public byte[] generateEmployeePayRunCsv(String organizationId, String payrunId, List<String> employeeIds) {
        Organization org = getOrganizationOrThrow(organizationId);

        PayRun payRun = payRunRepository.findByPayrunIdAndOrganization(payrunId, org)
                .orElseThrow(() -> new RuntimeException("PayRun not found for this organization."));

        List<EmployeePayRun> employeePayRuns = employeePayRunRepository
                .findByPayRunAndEmployeeIdIn(payRun, employeeIds);

        if (employeePayRuns.isEmpty()) {
            throw new RuntimeException("No EmployeePayRun records found for provided employee IDs.");
        }

        return generateCsvData(employeePayRuns);
    }


    private byte[] generateCsvData(List<EmployeePayRun> employeePayRuns) {
        StringWriter writer = new StringWriter();

        try (CSVPrinter csvPrinter = new CSVPrinter(writer,
                CSVFormat.DEFAULT.withHeader(
                        "Employee ID", "Employee Number", "Employee Name", "Full Name",
                        "Payment Status", "Payment Mode", "Total Earnings", "Total Deductions",
                        "Total Taxes", "Total Benefits", "Total Reimbursements", "Net Pay",
                        "Monthly Salary", "Paid Days", "Employee Status", "Bonus Earning Exist",
                        "Tax Overridden", "Employee Having Hold Salary", "PayRun ID",
                        // NEW columns for EPF / ESI
                        "EPF Components (monthly)", "EPF Components (annual)",
                        "ESI Components (monthly)", "ESI Components (annual)"
                ))) {

            for (EmployeePayRun e : employeePayRuns) {

                // ✅ Fetch ACTIVE CTC
                CtcStructure ctc = null;
                if (e.getEmployee() != null) {
                    ctc = ctcStructureRepository
                            .findFirstByOrganization_OrganizationIdAndEmployee_IdAndIsActiveTrueOrderByCreatedAtDesc(
                                    e.getEmployee().getOrganization().getOrganizationId(),
                                    e.getEmployee().getId()
                            )
                            .orElse(null);
                }

                // Build EPF strings
                String epfMonthlyStr = "";
                String epfAnnualStr = "";
                if (ctc != null && ctc.getEpfComponents() != null) {

                    epfMonthlyStr = ctc.getEpfComponents().stream()
                            .map(epf -> {
                                String monthly = epf.getMonthlyAmount() != null ? epf.getMonthlyAmount().toString() : "0";
                                return String.format("%s:%s:%s",
                                        safe(epf.getComponentCode()),
                                        safe(epf.getComponentLabel()),
                                        monthly);
                            })
                            .collect(Collectors.joining(" | "));

                    epfAnnualStr = ctc.getEpfComponents().stream()
                            .map(epf -> {
                                String annual = epf.getAnnualAmount() != null ? epf.getAnnualAmount().toString() : "0";
                                return String.format("%s:%s:%s",
                                        safe(epf.getComponentCode()),
                                        safe(epf.getComponentLabel()),
                                        annual);
                            })
                            .collect(Collectors.joining(" | "));
                }

                // Build ESI strings
                String esiMonthlyStr = "";
                String esiAnnualStr = "";
                if (ctc != null && ctc.getEsiComponents() != null) {

                    esiMonthlyStr = ctc.getEsiComponents().stream()
                            .map(esi -> {
                                String monthly = esi.getMonthlyAmount() != null ? esi.getMonthlyAmount().toString() : "0";
                                return String.format("%s:%s:%s",
                                        safe(esi.getComponentCode()),
                                        safe(esi.getComponentLabel()),
                                        monthly);
                            })
                            .collect(Collectors.joining(" | "));

                    esiAnnualStr = ctc.getEsiComponents().stream()
                            .map(esi -> {
                                String annual = esi.getAnnualAmount() != null ? esi.getAnnualAmount().toString() : "0";
                                return String.format("%s:%s:%s",
                                        safe(esi.getComponentCode()),
                                        safe(esi.getComponentLabel()),
                                        annual);
                            })
                            .collect(Collectors.joining(" | "));
                }

                csvPrinter.printRecord(
                        e.getEmployeeId(),
                        e.getEmployeeNumber(),
                        e.getEmployeeName(),
                        e.getFullName(),
                        e.getPaymentStatus(),
                        e.getPaymentMode(),
                        e.getTotalEarnings(),
                        e.getTotalDeductions(),
                        e.getTotalTaxes(),
                        e.getTotalBenefits(),
                        e.getTotalReimbursements(),
                        e.getNetPay(),
                        e.getMonthlySalary(),
                        e.getPaidDays(),
                        e.getEmployeeStatus(),
                        e.getBonusEarningExistForEmployee(),
                        e.getTaxOverridden(),
                        e.getEmployeeHavingHoldSalary(),
                        e.getPayrunId(),
                        epfMonthlyStr,
                        epfAnnualStr,
                        esiMonthlyStr,
                        esiAnnualStr
                );
            }

            csvPrinter.flush();

        } catch (IOException ex) {
            throw new RuntimeException("Error generating CSV: " + ex.getMessage(), ex);
        }

        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }


    // Helper to avoid "null" strings
    private static String safe(String s) {
        return s == null ? "" : s;
    }


/**
 * Parses processing period string to YearMonth
 * Handles formats: "September 2025", "2025-09", "Sep 2025"
 */
private YearMonth parseProcessingPeriodToYearMonth(String processingPeriod, String method) {
    if (processingPeriod == null || processingPeriod.trim().isEmpty()) {
        log.error("[{}] ❌ Processing period is null or empty", method);
        return null;
    }
    
    try {
        // Try to parse as "September 2025" format first
        try {
            return YearMonth.parse(processingPeriod, PROCESSING_PERIOD_FORMATTER);
        } catch (Exception e1) {
            // Try to parse as "2025-09" format
            try {
                return YearMonth.parse(processingPeriod, YEAR_MONTH_FORMATTER);
            } catch (Exception e2) {
                // Try to parse as "Sep 2025" format (abbreviated month)
                try {
                    DateTimeFormatter shortMonthFormatter = 
                        DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
                    return YearMonth.parse(processingPeriod, shortMonthFormatter);
                } catch (Exception e3) {
                    log.error("[{}] ❌ Could not parse processingPeriod: {} | Error: {}", 
                              method, processingPeriod, e3.getMessage());
                    return null;
                }
            }
        }
    } catch (Exception e) {
        log.error("[{}] ❌ Failed to parse processingPeriod: {} | Error: {}", 
                  method, processingPeriod, e.getMessage());
        return null;
    }
}

/**
 * Derives fiscal year from YearMonth
 * FY is END YEAR: Apr 2025 – Mar 2026 => fiscalYear = 2026
 */
private Integer deriveFiscalYear(YearMonth payMonth) {
    return (payMonth.getMonthValue() >= 4)
            ? payMonth.getYear() + 1
            : payMonth.getYear();
}

private YearMonth getPoiStartYearMonth(int fiscalYear, Month poiStartMonth) {
    // fiscalYear is the END year of FY (e.g., 2026 for FY 2025-26)
    
    if (poiStartMonth.getValue() >= 4) {
        // April-December: belongs to start year of FY
        return YearMonth.of(fiscalYear - 1, poiStartMonth);
    } else {
        // January-March: belongs to end year of FY
        return YearMonth.of(fiscalYear, poiStartMonth);
    }
}



}
