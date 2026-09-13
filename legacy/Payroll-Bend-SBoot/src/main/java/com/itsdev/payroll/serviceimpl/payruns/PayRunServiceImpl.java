package com.itsdev.payroll.serviceimpl.payruns;

import com.itsdev.payroll.controller.payruns.EmployeePayRunController;
import com.itsdev.payroll.dto.payruns.EmployeePayRunDTO;
import com.itsdev.payroll.dto.payruns.PayRunDTO;
import com.itsdev.payroll.dto.payruns.PersistedPayRunDTO;
import com.itsdev.payroll.dto.payruns.ReadyPayRunDTO;
import com.itsdev.payroll.entity.PaySchedule;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.employee.CtcEpfComponent;
import com.itsdev.payroll.entity.employee.CtcEsiComponent;
import com.itsdev.payroll.entity.employee.CtcStructure;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.payruns.EmployeePayRun;
import com.itsdev.payroll.entity.payruns.PayRun;
import com.itsdev.payroll.enumeration.payruns.PayRunStatus;
import com.itsdev.payroll.enumeration.payruns.PayRunType;
import com.itsdev.payroll.enumeration.DeductionStatus;
import com.itsdev.payroll.enumeration.employeereimbursement.ReimbursementPaymentStatus;
import com.itsdev.payroll.enumeration.employeereimbursement.ReimbursementStatus;
import com.itsdev.payroll.entity.SalaryDeduction;
import com.itsdev.payroll.entity.employeereimbursement.EmployeeReimbursementRequest;
import com.itsdev.payroll.mapper.payruns.PayRunMapper;
import com.itsdev.payroll.repository.PayScheduleRepository;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employee.CtcStructureRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.payruns.EmployeePayRunRepository;
import com.itsdev.payroll.repository.payruns.PayRunRepository;
import com.itsdev.payroll.repository.employee.SalaryDeductionRepository;
import com.itsdev.payroll.repository.employeereimbursement.EmployeeReimbursementRequestRepository;
import com.itsdev.payroll.service.payruns.EmployeePayRunService;
import com.itsdev.payroll.service.payruns.PayRunService;
import com.itsdev.payroll.service.payruns.PayslipTokenService;
import com.itsdev.payroll.service.BrevoEmailService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class PayRunServiceImpl implements PayRunService {

    @Value("${app.portal.base-url}")
    private String portalBaseUrl;

    private final PayRunRepository payRunRepository;
    private final OrganizationRepository organizationRepository;
    private final PayScheduleRepository payScheduleRepository;
    private final BasicDetailsRepository basicDetailsRepository;
    private final EmployeePayRunService employeeService;
    private final SecureRandom random = new SecureRandom();
    private final EmployeePayRunRepository employeePayRunRepository;
    private final BrevoEmailService brevoEmailService;
    private final CtcStructureRepository ctcStructureRepository;
    private final PayslipTokenService payslipTokenService;
    private final SalaryDeductionRepository salaryDeductionRepository;
    private final EmployeeReimbursementRequestRepository employeeReimbursementRequestRepository;

    private static final Logger log = LoggerFactory.getLogger(PayRunServiceImpl.class);

    private static final DateTimeFormatter PROCESSING_PERIOD_FORMATTER = DateTimeFormatter.ofPattern("MMMM uuuu",
            Locale.ENGLISH);

    public PayRunServiceImpl(PayRunRepository payRunRepository,
            OrganizationRepository organizationRepository,
            PayScheduleRepository payScheduleRepository,
            BasicDetailsRepository basicDetailsRepository,
            EmployeePayRunService employeeService,
            EmployeePayRunRepository employeePayRunRepository,
            BrevoEmailService brevoEmailService,
            CtcStructureRepository ctcStructureRepository,
            PayslipTokenService payslipTokenService,
            SalaryDeductionRepository salaryDeductionRepository,
            EmployeeReimbursementRequestRepository employeeReimbursementRequestRepository) {
        this.payRunRepository = payRunRepository;
        this.organizationRepository = organizationRepository;
        this.payScheduleRepository = payScheduleRepository;
        this.basicDetailsRepository = basicDetailsRepository;
        this.employeeService = employeeService;
        this.employeePayRunRepository = employeePayRunRepository;
        this.brevoEmailService = brevoEmailService;
        this.ctcStructureRepository=ctcStructureRepository;
        this.payslipTokenService = payslipTokenService;
        this.salaryDeductionRepository = salaryDeductionRepository;
        this.employeeReimbursementRequestRepository = employeeReimbursementRequestRepository;
    }


    private String generateUnique10DigitPayrunId() {
        String candidate;
        do {
            long num = (long) (1_000_000_000L + (Math.abs(random.nextLong()) % 9_000_000_000L));
            candidate = Long.toString(num);
        } while (payRunRepository.existsByPayrunId(candidate));
        return candidate;
    }

    private Organization getOrganizationOrThrow(String organizationId) {
        return organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));
    }

    // --- NEW: helper that checks whether a BasicDetails record has all required
    // steps completed ---
    private boolean isProfileComplete(BasicDetails bd) {
        // Step 1: basic details - always true (since this entity is BasicDetails)
        // Step 2: CTC -> ACTIVE CTC exists
        // Step 3: Personal Detail -> personalDetail != null
        // Step 4: Bank Detail -> bankDetail != null

        boolean hasCTC = !ctcStructureRepository
                .findByOrganization_OrganizationIdAndEmployee_IdAndIsActiveTrue(
                        bd.getOrganization().getOrganizationId(),
                        bd.getId()
                ).isEmpty();

        boolean hasPersonal = bd.getPersonalDetail() != null;
        boolean hasBank = bd.getBankDetail() != null;

        return hasCTC && hasPersonal && hasBank;
    }


    /*
     * @Override
     * public Map<String, Object> getAllPayRunsStructuredResponse(String
     * organizationId) {
     * Organization org = getOrganizationOrThrow(organizationId);
     * 
     * // Fetch persisted payruns whose status != COMPLETED
     * List<PayRun> persistedPayRuns =
     * payRunRepository.findByOrganizationAndStatusNot(org, PayRunStatus.COMPLETED);
     * List<PersistedPayRunDTO> payrollRuns = persistedPayRuns.stream()
     * .map(PayRunMapper::toDTO)
     * .collect(Collectors.toList());
     * 
     * // Compute ready-state payrun in memory
     * ReadyPayRunDTO readyPayRun = null;
     * 
     * String status = "ACTIVE";
     * 
     * List<BasicDetails> allBasics = basicDetailsRepository
     * .findByOrganization_OrganizationIdAndEmployeeStatus(organizationId, status);
     * 
     * 
     * // List<BasicDetails> allBasics =
     * basicDetailsRepository.findByOrganization_OrganizationIdAndIsDeletedFalse(
     * organizationId);
     * int completeCount = 0;
     * if (allBasics != null && !allBasics.isEmpty()) {
     * for (BasicDetails bd : allBasics) {
     * if (isProfileComplete(bd)) {
     * completeCount++;
     * }
     * }
     * }
     * 
     * if (completeCount > 0) {
     * // Step 2: Try to find the last COMPLETED regular payrun
     * Optional<PayRun> lastCompletedOpt = payRunRepository
     * .findFirstByOrganizationAndTypeAndStatusOrderByPayPeriodEndDateDesc(
     * org, PayRunType.REGULAR, PayRunStatus.COMPLETED
     * );
     * 
     * LocalDate nextStart;
     * LocalDate nextEnd;
     * LocalDate nextPayDate;
     * 
     * if (lastCompletedOpt.isPresent()) {
     * // Case 1: Start next period after last completed one
     * PayRun lastCompleted = lastCompletedOpt.get();
     * nextStart =
     * lastCompleted.getPayPeriodEndDate().plusMonths(1).withDayOfMonth(1);
     * nextEnd = nextStart.withDayOfMonth(nextStart.lengthOfMonth());
     * nextPayDate = nextEnd;
     * } else {
     * // Case 2: No completed payruns → use PaySchedule
     * PaySchedule schedule = payScheduleRepository.findByOrganization(org)
     * .orElseThrow(() -> new
     * RuntimeException("PaySchedule not found for organization: " +
     * organizationId));
     * 
     * nextStart = schedule.getPayPeriodStartDate();
     * nextEnd = schedule.getPayPeriodEndDate();
     * nextPayDate = schedule.getPayDate();
     * }
     * 
     * // Step 3: Check if next period already exists
     * boolean nextPeriodExists = payRunRepository
     * .existsByOrganizationAndTypeAndPayPeriodStartDateAndPayPeriodEndDate(
     * org, PayRunType.REGULAR, nextStart, nextEnd);
     * 
     * // Step 4: Create in-memory ready payrun if not already in DB
     * if (!nextPeriodExists) {
     * readyPayRun = PayRunMapper.toReadyDto(nextStart, nextEnd, nextPayDate,
     * completeCount);
     * }
     * }
     * 
     * // Wrap into final response
     * Map<String, Object> response = new LinkedHashMap<>();
     * response.put("status", HttpStatus.OK.value());
     * response.put("message", "Pay runs fetched successfully");
     * response.put("payrollRuns", payrollRuns);
     * response.put("readyStatePayrollRuns", readyPayRun);
     * 
     * return response;
     * }
     */

    @Override
    public Map<String, Object> getAllPayRunsStructuredResponse(String organizationId) {

        String method = "getAllPayRunsStructuredResponse";
        log.info("[{}] 📥 Request received to fetch all pay runs | orgId={}", method, organizationId);

        Organization org = getOrganizationOrThrow(organizationId);
        log.info("[{}] ✅ Organization found | orgName={}", method, org.getOrganizationName());

        // Step 1: Fetch persisted pay runs whose status != COMPLETED
        List<PayRun> persistedPayRuns =
                payRunRepository.findByOrganizationAndStatusNot(org, PayRunStatus.COMPLETED);

        log.info("[{}] 📄 Retrieved {} persisted pay runs (status != COMPLETED)",
                method, persistedPayRuns.size());

        List<PersistedPayRunDTO> payrollRuns = persistedPayRuns.stream()
                .map(PayRunMapper::toDTO)
                .collect(Collectors.toList());

        // Step 2: Fetch all active employees
        String status = "ACTIVE";
        List<BasicDetails> allBasics =
                basicDetailsRepository.findByOrganization_OrganizationIdAndEmployeeStatus(
                        organizationId, status);

        log.info("[{}] 👥 Found {} active employees for organization",
                method, allBasics.size());

        // Step 3: collect employees with complete profile
        List<BasicDetails> completedEmployees = new ArrayList<>();

        if (allBasics != null) {
            for (BasicDetails bd : allBasics) {
                if (isProfileComplete(bd)) {
                    completedEmployees.add(bd);
                }
            }
        }

        log.info("[{}] 🧾 {} employees have complete profiles",
                method, completedEmployees.size());

        ReadyPayRunDTO readyPayRun = null;

        if (!completedEmployees.isEmpty()) {

            // Step 4: Try to find the last COMPLETED regular payrun
            Optional<PayRun> lastCompletedOpt =
                    payRunRepository.findFirstByOrganizationAndTypeAndStatusOrderByPayPeriodEndDateDesc(
                            org, PayRunType.REGULAR, PayRunStatus.COMPLETED);

            LocalDate nextStart;
            LocalDate nextEnd;
            LocalDate nextPayDate;

            if (lastCompletedOpt.isPresent()) {

                PayRun lastCompleted = lastCompletedOpt.get();

                log.info("[{}] 📆 Last completed pay run found | periodEnd={}",
                        method, lastCompleted.getPayPeriodEndDate());

                nextStart = lastCompleted.getPayPeriodEndDate()
                        .plusMonths(1)
                        .withDayOfMonth(1);

                nextEnd = nextStart.withDayOfMonth(nextStart.lengthOfMonth());
                nextPayDate = nextEnd;

            } else {

                log.info("[{}] ⚙️ No completed pay runs found — using PaySchedule configuration",
                        method);

                PaySchedule schedule = payScheduleRepository.findByOrganization(org)
                        .orElseThrow(() -> {
                            log.error("[{}] ❌ PaySchedule not found for orgId={}",
                                    method, organizationId);
                            return new RuntimeException(
                                    "PaySchedule not found for organization: " + organizationId);
                        });

                nextStart = schedule.getPayPeriodStartDate();
                nextEnd   = schedule.getPayPeriodEndDate();
                nextPayDate = schedule.getPayDate();
            }

            // Step 5: Check if next period already exists
            boolean nextPeriodExists =
                    payRunRepository.existsByOrganizationAndTypeAndPayPeriodStartDateAndPayPeriodEndDate(
                            org, PayRunType.REGULAR, nextStart, nextEnd);

            log.info("[{}] 🔍 Next pay period check | start={} | end={} | exists={}",
                    method, nextStart, nextEnd, nextPeriodExists);

            // ------------------------------------------------
            // Step 6 : Eligible employees for THIS pay period
            // (same rule as real payrun generation)
            // ------------------------------------------------
            int eligibleEmployeeCount = 0;

            for (BasicDetails bd : completedEmployees) {

                boolean hasApplicableCtc =
                        ctcStructureRepository
                                .findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                                        organizationId,
                                        bd.getId(),
                                        nextEnd
                                )
                                .isPresent();

                if (hasApplicableCtc) {
                    eligibleEmployeeCount++;
                }
            }

            log.info("[{}] 👥 Eligible employees for ready payrun = {}",
                    method, eligibleEmployeeCount);

            // Step 7: Create in-memory ready payrun if not already in DB
            if (!nextPeriodExists) {

                readyPayRun = PayRunMapper.toReadyDto(
                        nextStart,
                        nextEnd,
                        nextPayDate,
                        eligibleEmployeeCount
                );

                log.info("[{}] 🟢 Ready-state pay run prepared | start={} | end={} | payDate={}",
                        method, nextStart, nextEnd, nextPayDate);

            } else {

                log.info("[{}] ⚠️ Next pay period already exists in DB — skipping ready-state creation",
                        method);
            }

        } else {

            log.warn("[{}] ⚠️ No employees with complete profiles — skipping ready pay run computation",
                    method);
        }

        // Step 8: Build response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Pay runs fetched successfully");
        response.put("payrollRuns", payrollRuns);
        response.put("readyStatePayrollRuns", readyPayRun);

        log.info("[{}] ✅ Pay runs fetched successfully | persistedRuns={} | readyState={}",
                method, payrollRuns.size(), (readyPayRun != null));

        return response;
    }


    @Override
    public List<PersistedPayRunDTO> getAllCompletedPayRuns(String organizationId) {
        Organization org = getOrganizationOrThrow(organizationId);

        // Fetch all payruns whose status is COMPLETED for this organization
        List<PayRun> completedRuns = payRunRepository.findByOrganizationAndStatus(org, PayRunStatus.COMPLETED);

        // Map to DTO
        return completedRuns.stream()
                .map(PayRunMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public PayRunDTO getPayRunById(String organizationId, String payrunId) {
        Organization org = getOrganizationOrThrow(organizationId);
        PayRun pr = payRunRepository.findByPayrunIdAndOrganization(payrunId, org)
                .orElseThrow(() -> new RuntimeException("PayRun not found: " + payrunId));
        return PayRunMapper.toDto(pr);
    }

    @Override
    public PayRunDTO createPayRun(String organizationId, PayRunDTO dto) {
        String method = "createPayRun";
        log.info("[{}] 🚀 Starting pay run creation | organizationId={} | dto={}", method, organizationId, dto);

        // Fetch organization or throw if not found
        Organization org = getOrganizationOrThrow(organizationId);
        log.info("[{}] ✅ Organization found | orgId={} | orgName={}", method, org.getOrganizationId(),
                org.getOrganizationName());

        // Map DTO to entity
        PayRun entity = PayRunMapper.toEntity(dto);
        entity.setOrganization(org);
        entity.setPayrunId(generateUnique10DigitPayrunId());
        entity.setStatus(PayRunStatus.DRAFT);
        log.info("[{}] 🏗️ PayRun entity initialized | payrunId={} | status={}", method, entity.getPayrunId(),
                entity.getStatus());

        // Save PayRun entity
        PayRun saved = payRunRepository.save(entity);
        log.info("[{}] 💾 PayRun saved successfully | id={} | payrunId={}", method, saved.getId(), saved.getPayrunId());

        // Generate employee-level pay runs
        List<EmployeePayRunDTO> generatedEmployees = employeeService.generateEmployeePayRuns(organizationId, saved);
        log.info("[{}] 👥 Employee pay runs generated | count={}", method, generatedEmployees.size());

        // Calculate and update totals
        calculateAndUpdatePayRunTotals(saved, generatedEmployees);
        log.info("[{}] 💰 PayRun totals calculated and updated | payrunId={}", method, saved.getPayrunId());

        PayRunDTO responseDto = PayRunMapper.toDto(saved);
        log.info("[{}] ✅ PayRun creation completed successfully | payrunId={} | totalEmployees={}",
                method, responseDto.getPayrunId(), generatedEmployees.size());

        return responseDto;
    }

    private void calculateAndUpdatePayRunTotals(PayRun payRun, List<EmployeePayRunDTO> employeePayRuns) {
        String method = "calculateAndUpdatePayRunTotals";
        log.info("[{}] 📊 Starting total calculation for payrunId={} | employeeCount={}",
                method, payRun.getPayrunId(), employeePayRuns.size());

        BigDecimal totalEarnings = BigDecimal.ZERO;
        BigDecimal totalBenefits = BigDecimal.ZERO;
        BigDecimal totalReimbursements = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalTaxes = BigDecimal.ZERO;
        BigDecimal totalNetPay = BigDecimal.ZERO;
        BigDecimal totalBonus = BigDecimal.ZERO;
        BigDecimal totalClaimDeduction = BigDecimal.ZERO;
        BigDecimal totalClaimReimbursement = BigDecimal.ZERO;

        // 🧮 Iterate over each employee and accumulate totals
        for (EmployeePayRunDTO dto : employeePayRuns) {
            totalEarnings = totalEarnings
                    .add(BigDecimal.valueOf(dto.getTotalEarnings() != null ? dto.getTotalEarnings() : 0.0));
            totalBenefits = totalBenefits
                    .add(BigDecimal.valueOf(dto.getTotalBenefits() != null ? dto.getTotalBenefits() : 0.0));
            totalReimbursements = totalReimbursements
                    .add(BigDecimal.valueOf(dto.getTotalReimbursements() != null ? dto.getTotalReimbursements() : 0.0));
            totalDeductions = totalDeductions
                    .add(BigDecimal.valueOf(dto.getTotalDeductions() != null ? dto.getTotalDeductions() : 0.0));
            totalTaxes = totalTaxes.add(BigDecimal.valueOf(dto.getTotalTaxes() != null ? dto.getTotalTaxes() : 0.0));
            totalNetPay = totalNetPay.add(BigDecimal.valueOf(dto.getNetPay() != null ? dto.getNetPay() : 0.0));
            totalBonus = totalBonus.add(BigDecimal.valueOf(dto.getBonus() != null ? dto.getBonus() : 0.0));
            totalClaimDeduction = totalClaimDeduction
                    .add(BigDecimal.valueOf(dto.getClaimDeduction() != null ? dto.getClaimDeduction() : 0.0));
            totalClaimReimbursement = totalClaimReimbursement
                    .add(BigDecimal.valueOf(dto.getClaimReimbursement() != null ? dto.getClaimReimbursement() : 0.0));
        }

        int totalEmployees = employeePayRuns.size();
        log.info(
                "[{}] 📈 Aggregated totals | Employees={} | Earnings={} | Benefits={} | Reimbursements={} | Deductions={} | Taxes={} | NetPay={}",
                method, totalEmployees, totalEarnings, totalBenefits, totalReimbursements, totalDeductions, totalTaxes,
                totalNetPay);

        // 🧾 getting epf and esi total
        calculateAndUpdateEpfAndEsiTotals(payRun, employeePayRuns);

        // 🧾 Update PayRun entity fields
        payRun.setNoOfEmployees(totalEmployees);
        payRun.setTotalBenefits(totalBenefits);
        payRun.setTotalDeductions(totalDeductions);
        payRun.setTotalTaxes(totalTaxes);

        payRun.setTotalBonus(totalBonus);
        payRun.setTotalClaimDeduction(totalClaimDeduction);
        payRun.setTotalClaimReimbursement(totalClaimReimbursement);
        payRun.setTotalNetPay(totalNetPay);
        payRun.setPayrollTotal(totalNetPay);
        payRun.setTotalPayrollCost(totalNetPay);

        log.info(
                "[{}] ✅ PayRun totals successfully updated | payrunId={} | totalEmployees={} | totalNetPay={} | totalPayrollCost={} | payrollTotal={}",
                method, payRun.getPayrunId(), totalEmployees, totalNetPay, payRun.getTotalPayrollCost(),
                payRun.getPayrollTotal());
    }

    private void calculateAndUpdateEpfAndEsiTotals(PayRun payRun, List<EmployeePayRunDTO> employeePayRuns) {


        String method = "calculateAndUpdateEpfAndEsiTotals";
        log.info("[{}]  Starting EPF/EPS/EDLI/Admin aggregation for PayRun {}",
                method, payRun.getPayrunId());

// ✅ processing period date for effective CTC fetch
        LocalDate periodDate = YearMonth.parse(
                payRun.getProcessingPeriod(),
                DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
        ).atDay(1);

        log.info("[{}] Using processingPeriod={} => periodDate={}",
                method, payRun.getProcessingPeriod(), periodDate);

        BigDecimal totalEpf = BigDecimal.ZERO;
        BigDecimal totalEsi = BigDecimal.ZERO;
        BigDecimal totalEdli = BigDecimal.ZERO;
        BigDecimal totalAdmin = BigDecimal.ZERO;


        for (EmployeePayRun employeePayrun : payRun.getEmployeePayRuns()) {

            BasicDetails employee = employeePayrun.getEmployee();
            if (employee == null) {
                log.warn("[{}] Skipping entry — Employee missing", method);
                continue;
            }

// ✅ Fetch ACTIVE CTC
            // ✅ Fetch CTC applicable for the PayRun month (effectiveDate based)
            CtcStructure ctc = ctcStructureRepository
                    .findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                            employee.getOrganization().getOrganizationId(),
                            employee.getId(),
                            periodDate
                    )
                    .orElse(null);

            if (ctc == null) {
                log.warn("[{}] Skipping entry — Active CTC structure missing for employee={}",
                        method, employee.getEmployeeNumber());
                continue;
            }

            log.info("[{}] Processing employee | name={}", method, employee.getFirstName());

            String empCode = employee.getEmployeeNumber();


            // EPF TABLE
            if (ctc.getEpfComponents() != null) {
                for (CtcEpfComponent epf : ctc.getEpfComponents()) {

                    BigDecimal amt = epf.getMonthlyAmount();
                    String code = epf.getComponentCode();

                    log.info("[{}] → EPF component found | code={}, monthlyAmount={}",
                            method, code, amt);

                    switch (code) {

                        case "EPF_EMPLOYER":
                            totalEpf = totalEpf.add(amt);
                            log.info("[{}]  • EPF_EMPLOYER | Emp={} | Amount={}", method, empCode, amt);
                            break;

                        case "EDLI_EMPLOYER":
                            totalEdli = totalEdli.add(amt);
                            log.info("[{}]  • EDLI          | Emp={} | Amount={}", method, empCode, amt);
                            break;

                        case "EPF_ADMIN":
                            totalAdmin = totalAdmin.add(amt);
                            log.info("[{}]  • ADMIN Charges | Emp={} | Amount={}", method, empCode, amt);
                            break;

                        default:
                            log.info("[{}]  • EPF Component ignored | Emp={} | Code={} | Amount={}",
                                    method, empCode, code, amt);
                    }
                }
            } else {
                log.info("[{}] No EPF components found for employee {}", method, empCode);
            }

            // ESI TABLE (EPS comes from here)
            if (ctc.getEsiComponents() != null) {
                for (CtcEsiComponent esi : ctc.getEsiComponents()) {

                    BigDecimal amt = esi.getMonthlyAmount();
                    String code = esi.getComponentCode();

                    log.info("[{}] → ESI component | code={} | monthlyAmount={}",
                            method, code, amt);

                    if ("ESI_EMPLOYER".equalsIgnoreCase(code)) {
                        totalEsi = totalEsi.add(amt);
                        log.info("[{}]  • ESI (via ESI_EMPLOYER) | Emp={} | Amount={}", method, empCode, amt);
                    } else {
                        log.info("[{}]  • ESI Component ignored | Emp={} | Code={} | Amount={}",
                                method, empCode, code, amt);
                    }
                }
            } else {
                log.info("[{}] No ESI components found for employee {}", method, empCode);
            }
        }

        // 🔹 Update PayRun totals
        payRun.setTotalEpfContribution(totalEpf);
        payRun.setTotalEsiContribution(totalEsi);
        payRun.setTotalEdliContribution(totalEdli);
        payRun.setTotalEpfAdminCharges(totalAdmin);

        log.info(
                "[{}]  Aggregation complete — Final Totals => " +
                        "EPF={} | ESI={} | EDLI={} | ADMIN={}",
                method, totalEpf, totalEsi, totalEdli, totalAdmin);
    }

    @Override
    public PayRunDTO updatePayRun(String organizationId, String payrunId, PayRunDTO dto) {
        Organization org = getOrganizationOrThrow(organizationId);
        PayRun existing = payRunRepository.findByPayrunIdAndOrganization(payrunId, org)
                .orElseThrow(() -> new RuntimeException("PayRun not found: " + payrunId));
        // update allowed fields
        if (dto.getStatus() != null)
            existing.setStatus(dto.getStatus());
        if (dto.getType() != null)
            existing.setType(dto.getType());
        if (dto.getPayPeriodStartDate() != null)
            existing.setPayPeriodStartDate(dto.getPayPeriodStartDate());
        if (dto.getPayPeriodEndDate() != null)
            existing.setPayPeriodEndDate(dto.getPayPeriodEndDate());
        if (dto.getPayDate() != null)
            existing.setPayDate(dto.getPayDate());
        existing.setProcessingPeriod(dto.getProcessingPeriod());
        existing.setPayrollTotal(dto.getPayrollTotal());
        existing.setStatusInfo(dto.getStatusInfo());
        existing.setNoOfEmployees(dto.getNoOfEmployees());
        existing.setApprovalType(dto.getApprovalType());
        existing.setApprovalDetails(dto.getApprovalDetails());
        existing.setCompensationName(dto.getCompensationName());

        existing.setTotalNetPay(dto.getTotalNetPay());
        existing.setTotalTaxes(dto.getTotalTaxes());
        existing.setTotalBenefits(dto.getTotalBenefits());
        existing.setTotalDonations(dto.getTotalDonations());
        existing.setTotalDeductions(dto.getTotalDeductions());
        existing.setTotalPayrollCost(dto.getTotalPayrollCost());

        existing.setCanEditPaydate(dto.getCanEditPaydate());
        existing.setCanPostPayrunTransactions(dto.getCanPostPayrunTransactions());
        existing.setHasDirectDepositPayments(dto.getHasDirectDepositPayments());
        existing.setHasNonDirectDepositPayments(dto.getHasNonDirectDepositPayments());

        existing.setEarningJson(dto.getEarningJson());
        existing.setVariablePayEarningsListJson(dto.getVariablePayEarningsListJson());
        existing.setDeductionsJson(dto.getDeductionsJson());
        existing.setExpenseBatchesDetailsJson(dto.getExpenseBatchesDetailsJson());

        PayRun saved = payRunRepository.save(existing);
        // return PayRunMapper::toDto != null ? PayRunMapper.toDto(saved) :
        // PayRunMapper.toDto(saved);
        return PayRunMapper.toDto(saved);
    }

    @Override
    public PayRunDTO approvePayRun(String organizationId, String payrunId, boolean canPostPayrunTransactions) {
        Organization org = getOrganizationOrThrow(organizationId);

        PayRun payRun = payRunRepository.findByOrganizationAndPayrunId(org, payrunId)
                .orElseThrow(() -> new RuntimeException("PayRun not found with ID: " + payrunId));

        // Only allow approval if canPostPayrunTransactions is true
        if (!canPostPayrunTransactions) {
            throw new RuntimeException("Cannot approve payrun: canPostPayrunTransactions is false");
        }

        payRun.setStatus(PayRunStatus.APPROVED);
        payRun.setCanPostPayrunTransactions(true);
        payRun.setApprovedDate(LocalDate.now());
        PayRun saved = payRunRepository.save(payRun);

        // ✅ Update claim deductions: INPAYRUN → PROCESSED
        String processingPeriod = payRun.getProcessingPeriod().trim();
        YearMonth ym = YearMonth.parse(processingPeriod, DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        LocalDate deductionMonth = ym.atDay(1);

        List<SalaryDeduction> inPayrunDeductions = salaryDeductionRepository
                .findByOrganizationIdAndDeductionMonthAndStatus(organizationId, deductionMonth, DeductionStatus.INPAYRUN);
        for (SalaryDeduction d : inPayrunDeductions) {
            d.setStatus(DeductionStatus.PROCESSED);
        }
        if (!inPayrunDeductions.isEmpty()) {
            salaryDeductionRepository.saveAll(inPayrunDeductions);
            log.info("[approvePayRun] ✅ Updated {} deductions to PROCESSED", inPayrunDeductions.size());
        }

        // ✅ Update claim reimbursements: INPAYRUN → PAID
        String reimbMonthFormatted = ym.getYear() + "-" + String.format("%02d", ym.getMonthValue());
        List<EmployeeReimbursementRequest> inPayrunReimbursements = employeeReimbursementRequestRepository
                .findByOrganizationIdAndReimbursementMonthAndPaymentStatus(
                        organizationId, reimbMonthFormatted, ReimbursementPaymentStatus.INPAYRUN);
        for (EmployeeReimbursementRequest r : inPayrunReimbursements) {
            r.setPaymentStatus(ReimbursementPaymentStatus.PAID);
        }
        if (!inPayrunReimbursements.isEmpty()) {
            employeeReimbursementRequestRepository.saveAll(inPayrunReimbursements);
            log.info("[approvePayRun] ✅ Updated {} reimbursements to PAID", inPayrunReimbursements.size());
        }

        // ✅ Update employee_payruns stored claim statuses
        List<EmployeePayRun> employeePayRuns = employeePayRunRepository.findByPayrunId(payrunId);
        for (EmployeePayRun epr : employeePayRuns) {
            if (epr.getClaimDeductionStatus() != null) {
                epr.setClaimDeductionStatus("PROCESSED");
            }
            if (epr.getClaimReimbursementStatus() != null) {
                epr.setClaimReimbursementStatus("PAID");
            }
        }
        employeePayRunRepository.saveAll(employeePayRuns);

        return PayRunMapper.toDto(saved);
    }

    @Override
    public PayRunDTO rejectPayRun(String organizationId, String payrunId, String rejectedReason) {
        Organization org = getOrganizationOrThrow(organizationId);

        PayRun payRun = payRunRepository.findByOrganizationAndPayrunId(org, payrunId)
                .orElseThrow(() -> new RuntimeException("PayRun not found with ID: " + payrunId));

        // Update status and rejected reason
        payRun.setStatus(PayRunStatus.REJECTED);
        payRun.setRejectedReason(rejectedReason);

        PayRun saved = payRunRepository.save(payRun);

        // ✅ Revert claim deductions: INPAYRUN → ACTIVE
        String processingPeriod = payRun.getProcessingPeriod().trim();
        YearMonth ym = YearMonth.parse(processingPeriod, DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        LocalDate deductionMonth = ym.atDay(1);

        List<SalaryDeduction> inPayrunDeductions = salaryDeductionRepository
                .findByOrganizationIdAndDeductionMonthAndStatus(organizationId, deductionMonth, DeductionStatus.INPAYRUN);
        for (SalaryDeduction d : inPayrunDeductions) {
            d.setStatus(DeductionStatus.ACTIVE);
        }
        if (!inPayrunDeductions.isEmpty()) {
            salaryDeductionRepository.saveAll(inPayrunDeductions);
            log.info("[rejectPayRun] ✅ Reverted {} deductions to ACTIVE", inPayrunDeductions.size());
        }

        // ✅ Revert claim reimbursements: INPAYRUN → UNPAID
        String reimbMonthFormatted = ym.getYear() + "-" + String.format("%02d", ym.getMonthValue());
        List<EmployeeReimbursementRequest> inPayrunReimbursements = employeeReimbursementRequestRepository
                .findByOrganizationIdAndReimbursementMonthAndPaymentStatus(
                        organizationId, reimbMonthFormatted, ReimbursementPaymentStatus.INPAYRUN);
        for (EmployeeReimbursementRequest r : inPayrunReimbursements) {
            r.setPaymentStatus(ReimbursementPaymentStatus.UNPAID);
            r.setPayrunId(null);
        }
        if (!inPayrunReimbursements.isEmpty()) {
            employeeReimbursementRequestRepository.saveAll(inPayrunReimbursements);
            log.info("[rejectPayRun] ✅ Reverted {} reimbursements to UNPAID", inPayrunReimbursements.size());
        }

        // ✅ Update employee_payruns stored claim statuses
        List<EmployeePayRun> employeePayRuns = employeePayRunRepository.findByPayrunId(payrunId);
        for (EmployeePayRun epr : employeePayRuns) {
            if (epr.getClaimDeductionStatus() != null) {
                epr.setClaimDeductionStatus("ACTIVE");
            }
            if (epr.getClaimReimbursementStatus() != null) {
                epr.setClaimReimbursementStatus("UNPAID");
            }
        }
        employeePayRunRepository.saveAll(employeePayRuns);

        return PayRunMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void completePayRunPayment(String organizationId, String payrunId, List<String> employeeIds) {
        String method = "completePayRunPayment";
        log.info("[{}] Starting PayRun Payment Completion", method);
        log.info("[{}] Incoming request: organizationId={}, payrunId={}, employeeIds={}", method, organizationId,
                payrunId, employeeIds);

        // 1. Validate organization
        log.info("[{}] Validating organization for ID: {}", method, organizationId);
        Organization org = getOrganizationOrThrow(organizationId);
        log.info("[{}] Organization validated: {}", method, org.getOrganizationName());

        // 2. Validate PayRun exists
        log.info("[{}] Fetching PayRun by ID: {} for organization: {}", method, payrunId, organizationId);
        PayRun payRun = payRunRepository.findByPayrunIdAndOrganization(payrunId, org)
                .orElseThrow(() -> {
                    log.error("[{}] PayRun not found for organization: {}", method, organizationId);
                    return new RuntimeException("PayRun not found for organization: " + organizationId);
                });
        log.info("[{}] PayRun found with status: {}", method, payRun.getStatus());

        // 3. Fetch EmployeePayRun entries
        log.info("[{}] Fetching EmployeePayRun records for employeeIds: {}", method, employeeIds);
        List<EmployeePayRun> employeePayRuns = employeePayRunRepository.findByPayRunAndEmployeeIdIn(payRun,
                employeeIds);

        if (employeePayRuns.isEmpty()) {
            log.error("[{}] No matching EmployeePayRun records found", method);
            throw new RuntimeException("No matching employee records found for this payrun.");
        }
        log.info("[{}] Found {} EmployeePayRun records", method, employeePayRuns.size());

        // 4. Update payment status for each employee
        log.info("[{}] Updating payment status to 'PAID' for employees...", method);
        for (EmployeePayRun epr : employeePayRuns) {
            log.info("[{}] Marking employeeId={} as PAID", method, epr.getEmployeeId());
            epr.setPaymentStatus("PAID");
        }
        employeePayRunRepository.saveAll(employeePayRuns);
        log.info("[{}] Employee payment statuses updated", method);

        // 5. Update PayRun overall status
        log.info("[{}] Updating PayRun status to COMPLETED", method);
        payRun.setPaymentStatus("COMPLETED");
        payRun.setStatus(PayRunStatus.COMPLETED);
        payRunRepository.save(payRun);
        log.info("[{}] PayRun marked as COMPLETED successfully", method);

        log.info("[{}] PayRun Payment Completion Process Completed Successfully", method);

        log.info("Triggering salary slip emails for payrun {}", payrunId);
        sendSalarySlipReadyEmails(organizationId, payRun, employeePayRuns);

    }

    @Override
    public void deletePayRun(String organizationId, String payrunId) {
        String method = "deletePayRun";
        log.info("[{}] 🔍 Fetching Organization and PayRun details (organizationId={}, payrunId={})",
                method, organizationId, payrunId);

        Organization org = getOrganizationOrThrow(organizationId);

        PayRun payRun = payRunRepository.findByOrganizationAndPayrunId(org, payrunId)
                .orElseThrow(() -> {
                    log.error("[{}] ❌ PayRun not found for organizationId={} and payrunId={}",
                            method, organizationId, payrunId);
                    return new RuntimeException("PayRun not found with ID: " + payrunId);
                });

        // ✅ Revert claim deductions: INPAYRUN → ACTIVE
        String processingPeriod = payRun.getProcessingPeriod().trim();
        YearMonth ym = YearMonth.parse(processingPeriod, DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        LocalDate deductionMonth = ym.atDay(1);

        List<SalaryDeduction> inPayrunDeductions = salaryDeductionRepository
                .findByOrganizationIdAndDeductionMonthAndStatus(organizationId, deductionMonth, DeductionStatus.INPAYRUN);
        for (SalaryDeduction d : inPayrunDeductions) {
            d.setStatus(DeductionStatus.ACTIVE);
        }
        if (!inPayrunDeductions.isEmpty()) {
            salaryDeductionRepository.saveAll(inPayrunDeductions);
            log.info("[{}] ✅ Reverted {} deductions to ACTIVE", method, inPayrunDeductions.size());
        }

        // ✅ Revert claim reimbursements: INPAYRUN → UNPAID
        String reimbMonthFormatted = ym.getYear() + "-" + String.format("%02d", ym.getMonthValue());
        List<EmployeeReimbursementRequest> inPayrunReimbursements = employeeReimbursementRequestRepository
                .findByOrganizationIdAndReimbursementMonthAndPaymentStatus(
                        organizationId, reimbMonthFormatted, ReimbursementPaymentStatus.INPAYRUN);
        for (EmployeeReimbursementRequest r : inPayrunReimbursements) {
            r.setPaymentStatus(ReimbursementPaymentStatus.UNPAID);
            r.setPayrunId(null);
        }
        if (!inPayrunReimbursements.isEmpty()) {
            employeeReimbursementRequestRepository.saveAll(inPayrunReimbursements);
            log.info("[{}] ✅ Reverted {} reimbursements to UNPAID", method, inPayrunReimbursements.size());
        }

        log.info("[{}] 🗑️ Deleting PayRun with ID: {}", method, payRun.getPayrunId());
        payRunRepository.delete(payRun);
        log.info("[{}] ✅ Successfully deleted PayRun with ID: {}", method, payRun.getPayrunId());
    }

    @Override
    public String getCurrentPayrunForItDeclaration(String organizationId) {
        String method = "getCurrentPayrunForItDeclaration";
        log.info("[{}] Calculating current IT declaration payrun | orgId={}", method, organizationId);

        // 1) Get Organization
        Organization org = getOrganizationOrThrow(organizationId);

        // 2) Get all REGULAR payruns for this org
        List<PayRun> runs = payRunRepository.findByOrganizationAndType(org, PayRunType.REGULAR);
        if (runs == null || runs.isEmpty()) {
            log.info("[{}] No REGULAR payruns found for orgId={} -> returning empty currentPayrun", method,
                    organizationId);
            return "";
        }

        // 3) Find the latest processingPeriod (based on YearMonth)
        PayRun latest = runs.stream()
                .filter(r -> StringUtils.hasText(r.getProcessingPeriod()))
                .max(Comparator.comparing(r -> YearMonth.parse(r.getProcessingPeriod(), PROCESSING_PERIOD_FORMATTER)))
                .orElse(null);

        if (latest == null) {
            log.info("[{}] No valid processingPeriod found among REGULAR payruns -> returning empty", method);
            return "";
        }

        String processingPeriod = latest.getProcessingPeriod();
        PayRunStatus status = latest.getStatus();
        log.info("[{}] Latest payrun | processingPeriod={} | status={}", method, processingPeriod, status);

        // 4) If status is NOT COMPLETED -> use same month as currentPayrun
        if (status != PayRunStatus.COMPLETED) {
            log.info("[{}] Latest REGULAR payrun is not COMPLETED -> currentPayrun={}", method, processingPeriod);
            return processingPeriod;
        }

        // 5) If COMPLETED -> next month is currentPayrun
        YearMonth ym = YearMonth.parse(processingPeriod, PROCESSING_PERIOD_FORMATTER);
        YearMonth next = ym.plusMonths(1);
        String nextPeriod = next.format(PROCESSING_PERIOD_FORMATTER);
        log.info("[{}] Latest REGULAR payrun is COMPLETED -> next month currentPayrun={}", method, nextPeriod);

        return nextPeriod;
    }

    private void sendSalarySlipReadyEmails(
            String organizationId,
            PayRun payRun,
            List<EmployeePayRun> employeePayRuns) {

        Organization org = getOrganizationOrThrow(organizationId);

        for (EmployeePayRun epr : employeePayRuns) {
            try {
                BasicDetails employee = epr.getEmployee();

                if (employee == null || !StringUtils.hasText(employee.getWorkMail())) {
                    log.warn("Skipping salary slip mail for employee {}", epr.getEmployeeId());
                    continue;
                }

                Map<String, Object> params = new LinkedHashMap<>();
                params.put("employee_name",
                        employee.getFirstName() + " " + employee.getLastName());
                params.put("employee_number", employee.getEmployeeNumber());
                params.put("month", payRun.getProcessingPeriod());
                params.put("payment_date", LocalDate.now().toString());
                params.put("net_salary", epr.getNetPay());
                // Public link for direct download without login (using a secure HMAC token)
                String token = payslipTokenService.generateToken(payRun.getPayrunId(), employee.getEmployeeId(), org.getOrganizationId());
                params.put("emp_portal_link", portalBaseUrl + "/public/payslips/" + payRun.getPayrunId() + "/" + employee.getEmployeeId() + "?orgId=" + org.getOrganizationId() + "&token=" + token);

                boolean sent = brevoEmailService.sendSalarySlipEmail(
                        employee,
                        org,
                        params);

                if (sent) {
                    log.info("Salary slip email sent to {}", employee.getEmployeeNumber());
                } else {
                    log.warn("Salary slip email failed for {}", employee.getEmployeeNumber());
                }

            } catch (Exception ex) {
                log.error("Error sending salary slip email", ex);
            }
        }
    }
}
