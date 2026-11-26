package com.infinevo.payroll.compliance.form24q;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeeIdentificationResponse;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.payroll.challan.TdsChallan;
import com.infinevo.payroll.challan.TdsChallanAllocation;
import com.infinevo.payroll.challan.TdsChallanAllocationRepository;
import com.infinevo.payroll.challan.TdsChallanRepository;
import com.infinevo.payroll.challan.TdsQuarter;
import com.infinevo.payroll.challan.TdsReturnRepository;
import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunLine;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeductor.TaxDeductorResponse;
import com.infinevo.payroll.taxdeductor.TaxDeductorService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production implementation of {@link Form24QAnnexure1Service} (W-89.2).
 */
@Service
@Transactional(readOnly = true)
public class Form24QAnnexure1ServiceImpl implements Form24QAnnexure1Service {

    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]{1}$");

    private final TaxDeductorService taxDeductorService;
    private final TdsChallanRepository challanRepository;
    private final TdsChallanAllocationRepository challanAllocationRepository;
    private final PayRunRepository payRunRepository;
    private final EmployeePayRunRepository employeePayRunRepository;
    private final EmployeePayRunLineRepository payRunLineRepository;
    private final EmployeeService employeeService;
    private final EmployeeIdentificationService employeeIdentificationService;
    private final Form138DeductorResolver deductorResolver;
    private final TdsReturnRepository returnRepository;

    public Form24QAnnexure1ServiceImpl(
            TaxDeductorService taxDeductorService,
            TdsChallanRepository challanRepository,
            TdsChallanAllocationRepository challanAllocationRepository,
            PayRunRepository payRunRepository,
            EmployeePayRunRepository employeePayRunRepository,
            EmployeePayRunLineRepository payRunLineRepository,
            EmployeeService employeeService,
            EmployeeIdentificationService employeeIdentificationService,
            Form138DeductorResolver deductorResolver,
            TdsReturnRepository returnRepository) {
        this.taxDeductorService = Objects.requireNonNull(taxDeductorService, "taxDeductorService must not be null");
        this.challanRepository = Objects.requireNonNull(challanRepository, "challanRepository must not be null");
        this.challanAllocationRepository =
                Objects.requireNonNull(challanAllocationRepository, "challanAllocationRepository must not be null");
        this.payRunRepository = Objects.requireNonNull(payRunRepository, "payRunRepository must not be null");
        this.employeePayRunRepository =
                Objects.requireNonNull(employeePayRunRepository, "employeePayRunRepository must not be null");
        this.payRunLineRepository =
                Objects.requireNonNull(payRunLineRepository, "payRunLineRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.employeeIdentificationService =
                Objects.requireNonNull(employeeIdentificationService, "employeeIdentificationService must not be null");
        this.deductorResolver = Objects.requireNonNull(deductorResolver, "deductorResolver must not be null");
        this.returnRepository = Objects.requireNonNull(returnRepository, "returnRepository must not be null");
    }

    @Override
    public Annexure1PreviewResponse previewAnnexure1(String financialYear, String quarter) {
        UUID tenantId = TenantContext.require();
        String q = normalizeQuarter(quarter);
        FinancialYear fy = parseFinancialYear(financialYear);
        String assessmentYear = (fy.startYear() + 1) + "-" + (fy.endYear() + 1);

        // 1. Fetch Deductor
        // No deductor yet is one of the problems listed below, not an error: the page still shows the quarter.
        TaxDeductorResponse deductor = taxDeductorService.current().orElse(null);

        // 2. Fetch Challans
        List<TdsChallan> challanEntities =
                challanRepository.findByTenantIdAndFinancialYearAndQuarterOrderByTenderDateAsc(tenantId, fy.label(), q);

        List<UUID> challanIds = challanEntities.stream().map(TdsChallan::getId).toList();
        List<TdsChallanAllocation> allocations = challanIds.isEmpty()
                ? Collections.emptyList()
                : challanAllocationRepository.findByTenantIdAndChallanIdIn(tenantId, challanIds);

        Map<UUID, List<TdsChallanAllocation>> allocationsByChallan = new HashMap<>();
        Map<UUID, Map<UUID, List<TdsChallanAllocation>>> allocationByPayRunAndEmployee = new HashMap<>();
        for (TdsChallanAllocation alloc : allocations) {
            allocationsByChallan
                    .computeIfAbsent(alloc.getChallanId(), k -> new ArrayList<>())
                    .add(alloc);
            allocationByPayRunAndEmployee
                    .computeIfAbsent(alloc.getPayRunId(), k -> new HashMap<>())
                    .computeIfAbsent(alloc.getEmployeeId(), k -> new ArrayList<>())
                    .add(alloc);
        }

        // 3. Resolve closed pay runs for the quarter's 3 periods
        TdsQuarter tdsQuarter = new TdsQuarter(fy, q);
        List<PayRun> payRuns = payRunRepository
                .findByTenantIdAndPeriodInAndStatusIn(
                        tenantId,
                        tdsQuarter.candidateMonths().stream()
                                .map(java.time.YearMonth::toString)
                                .toList(),
                        List.of(PayRunStatus.PAID))
                .stream()
                .filter(r -> TdsQuarter.of(r).equals(tdsQuarter))
                .toList();

        List<UUID> payRunIds = payRuns.stream().map(PayRun::getId).toList();

        // 4. Fetch employee pay runs and tax lines
        List<EmployeePayRun> employeeRuns = payRunIds.isEmpty()
                ? Collections.emptyList()
                : employeePayRunRepository.findByTenantIdAndPayrunIdInAndInclusionStatus(
                        tenantId, payRunIds, InclusionStatus.INCLUDED);

        List<EmployeePayRunLine> taxLines = payRunIds.isEmpty()
                ? Collections.emptyList()
                : payRunLineRepository.findTaxLinesByPayrunIds(tenantId, payRunIds);

        Map<UUID, BigDecimal> taxAmountByEmployeeRunId = new HashMap<>();
        for (EmployeePayRunLine line : taxLines) {
            taxAmountByEmployeeRunId.merge(line.getEmployeePayrunId(), line.getAmount(), BigDecimal::add);
        }

        Map<UUID, PayRun> payRunMap =
                payRuns.stream().collect(java.util.stream.Collectors.toMap(PayRun::getId, p -> p));

        // 5. Build Deductee Records
        List<DeducteeDetailRecord> deducteeRecords = new ArrayList<>();
        int missingPanCount = 0;
        List<UUID> missingPanEmployeeIds = new ArrayList<>();
        Set<UUID> seenMissingPanEmployees = new HashSet<>();

        BigDecimal totalGrossPaid = BigDecimal.ZERO;
        BigDecimal totalTdsDeducted = BigDecimal.ZERO;
        BigDecimal totalTdsDeposited = BigDecimal.ZERO;
        BigDecimal unallocatedTds = BigDecimal.ZERO;
        boolean overCovered = false;

        Map<UUID, Integer> challanSerialById = new HashMap<>();
        int cIdx = 1;
        for (TdsChallan c : challanEntities) {
            challanSerialById.put(c.getId(), cIdx++);
        }

        for (EmployeePayRun er : employeeRuns) {
            BigDecimal tdsAmount = taxAmountByEmployeeRunId.getOrDefault(er.getId(), BigDecimal.ZERO);
            if (tdsAmount.compareTo(BigDecimal.ZERO) <= 0) {
                // Deductee Annexure I requires employees who had tax deducted
                continue;
            }

            PayRun pr = payRunMap.get(er.getPayrunId());
            LocalDate payDate = TdsQuarter.deductionDate(pr);

            UUID employeeId = er.getEmployeeId();

            // Employee name & number
            String employeeNumber = "";
            String employeeName = "Employee";
            EmployeeResponse emp = employeeService.get(employeeId);
            if (emp != null) {
                employeeNumber = emp.employeeNumber() != null ? emp.employeeNumber() : "";
                String first = emp.firstName() != null ? emp.firstName() : "";
                String last = emp.lastName() != null ? emp.lastName() : "";
                employeeName = (first + " " + last).trim();
            }

            // PAN lookup
            String pan = null;
            Optional<EmployeeIdentificationResponse> identOpt = employeeIdentificationService.find(employeeId);
            if (identOpt.isPresent() && identOpt.get().panNumber() != null) {
                String candidatePan = identOpt.get().panNumber().trim().toUpperCase();
                if (PAN_PATTERN.matcher(candidatePan).matches()) {
                    pan = candidatePan;
                }
            }

            String reasonCode = null;
            if (pan == null) {
                pan = "PANNOTAVBL";
                reasonCode = "C"; // Form 24Q: deduction at a higher rate, section 206AA (no PAN)
                if (seenMissingPanEmployees.add(employeeId)) {
                    missingPanCount++;
                    missingPanEmployeeIds.add(employeeId);
                }
            }

            // Salary from a non-government employer: 1002 in Form 138 (tax year 2026-27 on), 92B in Form 24Q before.
            String sectionCode = fy.startYear() >= FIRST_FORM_138_YEAR ? Form138File.SECTION_PRIVATE_EMPLOYER : "92B";

            BigDecimal grossAmount = er.getGrossEarnings() != null ? er.getGrossEarnings() : BigDecimal.ZERO;
            totalGrossPaid = totalGrossPaid.add(grossAmount);
            totalTdsDeducted = totalTdsDeducted.add(tdsAmount);

            // W-89.2: one row per challan that covers this employee's TDS on this run, so a deduction split
            // across two challans is shown under both. What no challan covers is its own row, deposited 0, and
            // blocks the FVU file until it is allocated.
            List<TdsChallanAllocation> shares = allocationByPayRunAndEmployee
                    .getOrDefault(er.getPayrunId(), Map.of())
                    .getOrDefault(employeeId, List.of());
            BigDecimal covered = BigDecimal.ZERO;
            BigDecimal grossLeft = grossAmount;
            BigDecimal uncoveredTds = tdsAmount.subtract(
                    shares.stream().map(TdsChallanAllocation::total).reduce(BigDecimal.ZERO, BigDecimal::add));
            int rowsForEmployee = shares.size() + (uncoveredTds.signum() > 0 ? 1 : 0);
            int rowIndex = 0;
            for (TdsChallanAllocation share : shares) {
                BigDecimal part = share.total();
                covered = covered.add(part);
                // Form 138 needs an amount paid above 0 on every row: the run's gross is split in proportion to
                // each challan's share of the TDS, the last row taking what rounding leaves.
                rowIndex++;
                BigDecimal grossShare = rowIndex == rowsForEmployee
                        ? grossLeft
                        : grossAmount.multiply(part).divide(tdsAmount, 2, RoundingMode.HALF_UP);
                grossLeft = grossLeft.subtract(grossShare);
                totalTdsDeposited = totalTdsDeposited.add(part);
                deducteeRecords.add(new DeducteeDetailRecord(
                        employeeId,
                        employeeNumber,
                        employeeName,
                        pan,
                        null,
                        sectionCode,
                        payDate,
                        money(grossShare),
                        money(share.getAllocatedTax()),
                        money(share.getAllocatedSurcharge()),
                        money(share.getAllocatedCess()),
                        money(part),
                        money(part),
                        payDate,
                        reasonCode,
                        null,
                        share.getChallanId(),
                        challanSerialById.getOrDefault(share.getChallanId(), 0)));
            }
            BigDecimal uncovered = tdsAmount.subtract(covered);
            if (uncovered.signum() < 0) {
                overCovered = true;
            }
            if (uncovered.signum() > 0) {
                unallocatedTds = unallocatedTds.add(uncovered);
                deducteeRecords.add(new DeducteeDetailRecord(
                        employeeId,
                        employeeNumber,
                        employeeName,
                        pan,
                        null,
                        sectionCode,
                        payDate,
                        money(grossLeft),
                        money(uncovered),
                        money(BigDecimal.ZERO),
                        money(BigDecimal.ZERO),
                        money(uncovered),
                        money(BigDecimal.ZERO),
                        payDate,
                        reasonCode,
                        null,
                        null,
                        0));
            }
        }

        // 6. Build Challan Detail Records
        List<ChallanDetailRecord> challanRecords = new ArrayList<>();
        BigDecimal totalChallanAmount = BigDecimal.ZERO;
        int challanSerial = 1;

        Map<UUID, Long> deducteesPerChallan = deducteeRecords.stream()
                .filter(d -> d.matchedChallanId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        DeducteeDetailRecord::matchedChallanId, java.util.stream.Collectors.counting()));

        for (TdsChallan c : challanEntities) {
            BigDecimal cTotal = c.getTotalAmount() != null ? c.getTotalAmount() : BigDecimal.ZERO;
            totalChallanAmount = totalChallanAmount.add(cTotal);

            int dCount = deducteesPerChallan.getOrDefault(c.getId(), 0L).intValue();

            challanRecords.add(new ChallanDetailRecord(
                    c.getId(),
                    challanSerial++,
                    c.getBsrCode(),
                    c.getChallanSerialNo(),
                    c.getTenderDate(),
                    c.getTaxAmount(),
                    c.getSurchargeAmount(),
                    c.getCessAmount(),
                    c.getInterestAmount(),
                    c.getFeePenaltyAmount(),
                    cTotal,
                    c.getMinorHead() != null ? c.getMinorHead() : "200",
                    null,
                    dCount));
        }

        boolean isReconciled = !challanEntities.isEmpty() && unallocatedTds.signum() == 0;

        Form138DeductorResolver.Resolution resolution = deductorResolver.resolve();
        String companyName =
                resolution.deductor() == null ? "" : resolution.deductor().name();
        List<String> problems = portalFileProblems(
                tdsQuarter, resolution, challanEntities, deducteeRecords, unallocatedTds, overCovered, tenantId);

        return new Annexure1PreviewResponse(
                fy.label(),
                q,
                assessmentYear,
                deductor == null ? null : deductor.tan(),
                deductor == null ? null : deductor.pan(),
                companyName,
                challanRecords.size(),
                totalChallanAmount.setScale(2, RoundingMode.HALF_UP),
                deducteeRecords.size(),
                totalGrossPaid.setScale(2, RoundingMode.HALF_UP),
                totalTdsDeducted.setScale(2, RoundingMode.HALF_UP),
                totalTdsDeposited.setScale(2, RoundingMode.HALF_UP),
                isReconciled,
                money(unallocatedTds),
                missingPanCount,
                missingPanEmployeeIds,
                challanRecords,
                deducteeRecords,
                problems);
    }

    /** Form 138 is the return from tax year 2026-27 (Income-tax Act 2025); earlier years filed Form 24Q. */
    static final int FIRST_FORM_138_YEAR = 2026;

    /** Everything that stops the portal file for this quarter, in plain words; empty when it can be made. */
    private List<String> portalFileProblems(
            TdsQuarter quarter,
            Form138DeductorResolver.Resolution resolution,
            List<TdsChallan> challans,
            List<DeducteeDetailRecord> deductees,
            BigDecimal unallocatedTds,
            boolean overCovered,
            UUID tenantId) {
        List<String> problems = new ArrayList<>();
        if (quarter.financialYear().startYear() < FIRST_FORM_138_YEAR) {
            problems.add("The portal file is made for tax year 2026-27 onwards (Form 138). For "
                    + quarter.financialYear().label() + " use the CSV and your Form 24Q software");
            return problems;
        }
        if (quarter.isQ4()) {
            problems.add("Protean has not yet published the Form 138 Q4 file layout; the Q4 portal file will be "
                    + "added when it is. Use the CSV until then");
        }
        problems.addAll(resolution.missing().stream().map(m -> "Fill in: " + m).toList());
        if (challans.isEmpty()) {
            problems.add("Record the quarter's TDS challans");
        }
        if (deductees.isEmpty()) {
            problems.add("No employee had TDS deducted in this quarter; a regular statement needs at least one");
        }
        if (unallocatedTds.signum() > 0) {
            problems.add("Allocate the remaining "
                    + unallocatedTds.setScale(2, RoundingMode.HALF_UP).toPlainString()
                    + " of TDS to challans: every employee's TDS must be covered by a challan");
        }
        for (TdsChallan c : challans) {
            if (!wholeRupees(c.getTaxAmount().add(c.getSurchargeAmount()).add(c.getCessAmount()))
                    || !wholeRupees(c.getInterestAmount())
                    || !wholeRupees(c.getFeePenaltyAmount())) {
                problems.add("Challan " + c.getBsrCode() + "/" + c.getChallanSerialNo()
                        + ": challan amounts must be whole rupees");
            }
        }
        deductees.stream()
                .filter(d -> d.reasonForNonDeduction() != null && "C".equals(d.reasonForNonDeduction()))
                .filter(d ->
                        d.totalTaxDeducted().multiply(BigDecimal.valueOf(5)).compareTo(d.grossAmount()) < 0)
                .map(DeducteeDetailRecord::employeeNumber)
                .distinct()
                .forEach(no -> problems.add("Employee " + no + " has no valid PAN, and TDS is below 20% of pay: "
                        + "record the PAN, or the portal will reject the row"));
        deductees.stream()
                .filter(d -> d.grossAmount() == null || d.grossAmount().signum() <= 0)
                .map(DeducteeDetailRecord::employeeNumber)
                .distinct()
                .forEach(no -> problems.add("Employee " + no
                        + " has TDS but no gross pay on a run this quarter; the portal needs the amount paid"));
        deductees.stream()
                .filter(d -> Form138File.text(d.employeeName(), 75).isBlank())
                .map(DeducteeDetailRecord::employeeNumber)
                .distinct()
                .forEach(no -> problems.add("Employee " + no + " needs a name in English letters"));
        if (resolution.deductor() != null) {
            Form138Deductor d = resolution.deductor();
            if (Form138File.text(d.responsibleName(), 75).isBlank()) {
                problems.add("The signatory's name must be in English letters (Payroll › Settings › TDS deductor)");
            }
            String designation = Form138File.text(d.responsibleDesignation(), 1000);
            if (designation.isBlank() || designation.length() > 20) {
                problems.add("The signatory's designation must be 1 to 20 English characters for the portal, e.g."
                        + " \"Director\" (Payroll › Settings › TDS deductor)");
            }
        }
        if (overCovered) {
            problems.add("Some employee's TDS is covered by more than was deducted; check the challan allocations");
        }
        PreviousStatement previous = previousStatement(tenantId, quarter);
        if (previous.missing() != null) {
            problems.add("Mark " + previous.missing() + " filed with its token number, so this file can quote the"
                    + " previous Form 138 statement");
        }
        return problems;
    }

    private static boolean wholeRupees(BigDecimal v) {
        return v == null || v.stripTrailingZeros().scale() <= 0;
    }

    /**
     * The immediately previous Form 138 statement (BH 9 and 52): the quarter before, across the year end. None
     * before Q1 2026-27, the first Form 138 quarter. {@code missing} names the quarter when it is not marked filed.
     */
    record PreviousStatement(String token, String missing) {}

    PreviousStatement previousStatement(UUID tenantId, TdsQuarter quarter) {
        int start = quarter.financialYear().startYear();
        if (start == FIRST_FORM_138_YEAR && "Q1".equals(quarter.quarter())) {
            return new PreviousStatement(null, null);
        }
        String fy;
        String q;
        if ("Q1".equals(quarter.quarter())) {
            fy = (start - 1) + "-" + start;
            q = "Q4";
        } else {
            fy = quarter.financialYear().label();
            q = "Q" + (Integer.parseInt(quarter.quarter().substring(1)) - 1);
        }
        return returnRepository
                .findByTenantIdAndFinancialYearAndQuarter(tenantId, fy, q)
                .map(r -> new PreviousStatement(r.getTokenNumber(), null))
                .orElse(new PreviousStatement(null, q + " " + fy));
    }

    @Override
    public byte[] exportAnnexure1(String financialYear, String quarter, String format) {
        Annexure1PreviewResponse preview = previewAnnexure1(financialYear, quarter);
        String f = format != null ? format.trim().toUpperCase() : "CSV";

        return switch (f) {
            case "CSV" -> Annexure1CsvFormatter.format(preview);
            case "TXT" -> {
                if (!preview.portalFileProblems().isEmpty()) {
                    throw new Form24QValidationException(
                            "The portal file cannot be made yet: " + String.join("; ", preview.portalFileProblems()));
                }
                TdsQuarter q = TdsQuarter.parse(financialYear, quarter);
                yield Form138File.write(
                        preview,
                        deductorResolver.resolve().deductor(),
                        previousStatement(TenantContext.require(), q).token(),
                        java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")));
            }
            default ->
                throw new Form24QValidationException("Unsupported export format: " + format + " (expected CSV or TXT)");
        };
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeQuarter(String quarter) {
        if (quarter == null || quarter.isBlank()) {
            throw new Form24QValidationException("Quarter is required (expected Q1, Q2, Q3, or Q4)");
        }
        String q = quarter.trim().toUpperCase();
        if (!Set.of("Q1", "Q2", "Q3", "Q4").contains(q)) {
            throw new Form24QValidationException("Invalid quarter: " + quarter + " (expected Q1, Q2, Q3, or Q4)");
        }
        return q;
    }

    private FinancialYear parseFinancialYear(String fy) {
        try {
            return FinancialYear.parse(fy);
        } catch (Exception e) {
            throw new Form24QValidationException("Invalid financial year format: " + fy + " (expected YYYY-YYYY)");
        }
    }
}
