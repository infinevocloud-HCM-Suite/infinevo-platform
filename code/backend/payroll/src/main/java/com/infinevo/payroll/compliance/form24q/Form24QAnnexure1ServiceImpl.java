package com.infinevo.payroll.compliance.form24q;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeeIdentificationResponse;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.payroll.challan.TdsChallan;
import com.infinevo.payroll.challan.TdsChallanAllocation;
import com.infinevo.payroll.challan.TdsChallanAllocationRepository;
import com.infinevo.payroll.challan.TdsChallanRepository;
import com.infinevo.payroll.form16.exception.DeductorNotSetException;
import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunLine;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeductor.TaxDeductorResponse;
import com.infinevo.payroll.taxdeductor.TaxDeductorService;
import com.infinevo.payroll.tds.EmployeeTds;
import com.infinevo.payroll.tds.EmployeeTdsRepository;
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
    private final EmployeeTdsRepository employeeTdsRepository;

    public Form24QAnnexure1ServiceImpl(
            TaxDeductorService taxDeductorService,
            TdsChallanRepository challanRepository,
            TdsChallanAllocationRepository challanAllocationRepository,
            PayRunRepository payRunRepository,
            EmployeePayRunRepository employeePayRunRepository,
            EmployeePayRunLineRepository payRunLineRepository,
            EmployeeService employeeService,
            EmployeeIdentificationService employeeIdentificationService,
            EmployeeTdsRepository employeeTdsRepository) {
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
        this.employeeTdsRepository =
                Objects.requireNonNull(employeeTdsRepository, "employeeTdsRepository must not be null");
    }

    @Override
    public Annexure1PreviewResponse previewAnnexure1(String financialYear, String quarter) {
        UUID tenantId = TenantContext.require();
        String q = normalizeQuarter(quarter);
        FinancialYear fy = parseFinancialYear(financialYear);
        String assessmentYear = (fy.startYear() + 1) + "-" + (fy.endYear() + 1);

        // 1. Fetch Deductor
        TaxDeductorResponse deductor = taxDeductorService.current().orElseThrow(DeductorNotSetException::new);

        // 2. Fetch Challans
        List<TdsChallan> challanEntities =
                challanRepository.findByTenantIdAndFinancialYearAndQuarterOrderByTenderDateAsc(tenantId, fy.label(), q);

        List<UUID> challanIds = challanEntities.stream().map(TdsChallan::getId).toList();
        List<TdsChallanAllocation> allocations = challanIds.isEmpty()
                ? Collections.emptyList()
                : challanAllocationRepository.findByTenantIdAndChallanIdIn(tenantId, challanIds);

        Map<UUID, List<TdsChallanAllocation>> allocationsByChallan = new HashMap<>();
        Map<UUID, Map<UUID, TdsChallanAllocation>> allocationByPayRunAndEmployee = new HashMap<>();
        for (TdsChallanAllocation alloc : allocations) {
            allocationsByChallan
                    .computeIfAbsent(alloc.getChallanId(), k -> new ArrayList<>())
                    .add(alloc);
            allocationByPayRunAndEmployee
                    .computeIfAbsent(alloc.getPayRunId(), k -> new HashMap<>())
                    .put(alloc.getEmployeeId(), alloc);
        }

        // 3. Resolve closed pay runs for the quarter's 3 periods
        List<String> periods = getQuarterPeriods(fy, q);
        List<PayRun> payRuns = payRunRepository.findByTenantIdAndPeriodInAndStatusIn(
                tenantId, periods, List.of(PayRunStatus.APPROVED, PayRunStatus.PAID));

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
            LocalDate payDate = pr != null && pr.getPayDate() != null ? pr.getPayDate() : LocalDate.now();

            UUID employeeId = er.getEmployeeId();

            // Employee name & number
            String employeeNumber = "";
            String employeeName = "Employee";
            try {
                EmployeeResponse emp = employeeService.get(employeeId);
                if (emp != null) {
                    employeeNumber = emp.employeeNumber() != null ? emp.employeeNumber() : "";
                    String first = emp.firstName() != null ? emp.firstName() : "";
                    String last = emp.lastName() != null ? emp.lastName() : "";
                    employeeName = (first + " " + last).trim();
                }
            } catch (Exception ignored) {
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
                reasonCode = "Y"; // Higher rate of deduction under Sec 206AA
                if (seenMissingPanEmployees.add(employeeId)) {
                    missingPanCount++;
                    missingPanEmployeeIds.add(employeeId);
                }
            }

            // Tax regime lookup
            String sectionCode = "92B";
            Optional<EmployeeTds> tdsOpt = employeeTdsRepository.findActive(tenantId, employeeId, fy.label());
            if (tdsOpt.isPresent() && tdsOpt.get().getRegime() == TaxRegime.NEW) {
                sectionCode = "92B(1A)";
            }

            // Matching Challan & Allocated Deposit
            UUID matchedChallanId = null;
            BigDecimal deposited = BigDecimal.ZERO;

            Map<UUID, TdsChallanAllocation> runAllocs = allocationByPayRunAndEmployee.get(er.getPayrunId());
            if (runAllocs != null && runAllocs.containsKey(employeeId)) {
                TdsChallanAllocation alloc = runAllocs.get(employeeId);
                matchedChallanId = alloc.getChallanId();
                BigDecimal allocTax = alloc.getAllocatedTax() != null ? alloc.getAllocatedTax() : BigDecimal.ZERO;
                BigDecimal allocCess = alloc.getAllocatedCess() != null ? alloc.getAllocatedCess() : BigDecimal.ZERO;
                deposited = allocTax.add(allocCess);
            }

            int challanSerialInBatch =
                    matchedChallanId != null ? challanSerialById.getOrDefault(matchedChallanId, 0) : 0;

            BigDecimal grossAmount = er.getGrossEarnings() != null ? er.getGrossEarnings() : BigDecimal.ZERO;

            totalGrossPaid = totalGrossPaid.add(grossAmount);
            totalTdsDeducted = totalTdsDeducted.add(tdsAmount);
            totalTdsDeposited = totalTdsDeposited.add(deposited);

            deducteeRecords.add(new DeducteeDetailRecord(
                    employeeId,
                    employeeNumber,
                    employeeName,
                    pan,
                    "02", // Deductee code for individuals
                    sectionCode,
                    payDate,
                    grossAmount.setScale(2, RoundingMode.HALF_UP),
                    tdsAmount.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    tdsAmount.setScale(2, RoundingMode.HALF_UP),
                    deposited.setScale(2, RoundingMode.HALF_UP),
                    payDate,
                    reasonCode,
                    null,
                    matchedChallanId,
                    challanSerialInBatch));
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

        boolean allDeducteesAllocated = deducteeRecords.isEmpty()
                || deducteeRecords.stream()
                        .allMatch(d -> d.matchedChallanId() != null
                                && d.totalTaxDeposited().compareTo(d.totalTaxDeducted()) >= 0);

        boolean isReconciled = totalChallanAmount.compareTo(totalTdsDeducted) >= 0
                && totalTdsDeposited.compareTo(totalTdsDeducted) >= 0
                && !challanEntities.isEmpty()
                && allDeducteesAllocated;

        return new Annexure1PreviewResponse(
                fy.label(),
                q,
                assessmentYear,
                deductor.tan(),
                deductor.pan(),
                deductor.signatoryName(),
                challanRecords.size(),
                totalChallanAmount.setScale(2, RoundingMode.HALF_UP),
                deducteeRecords.size(),
                totalGrossPaid.setScale(2, RoundingMode.HALF_UP),
                totalTdsDeducted.setScale(2, RoundingMode.HALF_UP),
                totalTdsDeposited.setScale(2, RoundingMode.HALF_UP),
                isReconciled,
                missingPanCount,
                missingPanEmployeeIds,
                challanRecords,
                deducteeRecords);
    }

    @Override
    public byte[] exportAnnexure1(String financialYear, String quarter, String format) {
        Annexure1PreviewResponse preview = previewAnnexure1(financialYear, quarter);
        String f = format != null ? format.trim().toUpperCase() : "CSV";

        return switch (f) {
            case "CSV" -> Annexure1CsvFormatter.format(preview);
            case "TXT" -> NsdlFvuTextFormatter.format(preview);
            default ->
                throw new Form24QValidationException("Unsupported export format: " + format + " (expected CSV or TXT)");
        };
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

    private List<String> getQuarterPeriods(FinancialYear fy, String quarter) {
        int startYear = fy.startYear();
        int endYear = fy.endYear();
        return switch (quarter) {
            case "Q1" ->
                List.of(
                        String.format("%04d-04", startYear),
                        String.format("%04d-05", startYear),
                        String.format("%04d-06", startYear));
            case "Q2" ->
                List.of(
                        String.format("%04d-07", startYear),
                        String.format("%04d-08", startYear),
                        String.format("%04d-09", startYear));
            case "Q3" ->
                List.of(
                        String.format("%04d-10", startYear),
                        String.format("%04d-11", startYear),
                        String.format("%04d-12", startYear));
            case "Q4" ->
                List.of(
                        String.format("%04d-01", endYear),
                        String.format("%04d-02", endYear),
                        String.format("%04d-03", endYear));
            default -> throw new Form24QValidationException("Invalid quarter: " + quarter);
        };
    }
}
