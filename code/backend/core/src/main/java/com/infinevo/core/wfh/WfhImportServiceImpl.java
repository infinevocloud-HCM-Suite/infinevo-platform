package com.infinevo.core.wfh;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WfhImportServiceImpl implements WfhImportService {

    private static final Logger log = LoggerFactory.getLogger(WfhImportServiceImpl.class);

    private final WfhImportLogRepository importLogRepository;
    private final WfhAllocationRepository allocationRepository;
    private final WfhPolicyService policyService;
    private final EmployeeRepository employeeRepository;

    public WfhImportServiceImpl(
            WfhImportLogRepository importLogRepository,
            WfhAllocationRepository allocationRepository,
            WfhPolicyService policyService,
            EmployeeRepository employeeRepository) {
        this.importLogRepository = Objects.requireNonNull(importLogRepository);
        this.allocationRepository = Objects.requireNonNull(allocationRepository);
        this.policyService = Objects.requireNonNull(policyService);
        this.employeeRepository = Objects.requireNonNull(employeeRepository);
    }

    @Override
    public byte[] generateSampleCsv() {
        String csv = "employee_number,wfh_year,entitlement_days,effective_date\n"
                + "EMP001,2026,12.00,2026-01-01\n"
                + "EMP002,2026,6.00,2026-07-01\n";
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public WfhImportSummaryResponse importAllocations(
            UUID tenantId, String fileName, String wfhYear, byte[] csvBytes, boolean dryRun) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        String effectiveYear = (wfhYear != null && !wfhYear.isBlank())
                ? wfhYear.trim()
                : String.valueOf(LocalDate.now().getYear());

        WfhImportLog importLog = new WfhImportLog();
        importLog.setTenantId(tenantId);
        importLog.setFileName(fileName != null && !fileName.isBlank() ? fileName.trim() : "wfh_allocations.csv");
        importLog.setWfhYear(effectiveYear);
        importLog.setDryRun(dryRun);
        importLog.setStatus("PROCESSING");
        importLog = importLogRepository.save(importLog);

        List<String> errors = new ArrayList<>();
        int rowsTotal = 0;
        int rowsImported = 0;
        int rowsFailed = 0;

        if (csvBytes == null || csvBytes.length == 0) {
            importLog.setStatus("FAILED");
            importLog.setErrorSummary("Empty CSV file provided.");
            importLog.setFinishedAt(Instant.now());
            importLogRepository.save(importLog);
            return new WfhImportSummaryResponse(
                    importLog.getId(),
                    importLog.getFileName(),
                    "FAILED",
                    dryRun,
                    0,
                    0,
                    0,
                    List.of("Empty CSV file"),
                    importLog.getStartedAt(),
                    importLog.getFinishedAt());
        }

        try (BufferedReader reader =
                new BufferedReader(new StringReader(new String(csvBytes, StandardCharsets.UTF_8)))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV file contains no content");
            }

            String[] headers = headerLine.split(",");
            int empCodeIdx = -1;
            int yearIdx = -1;
            int daysIdx = -1;
            int effectiveDateIdx = -1;

            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].trim().toLowerCase().replace("\"", "");
                if (h.equals("employee_code")
                        || h.equals("employeecode")
                        || h.equals("employee_number")
                        || h.equals("employeenumber")) {
                    empCodeIdx = i;
                } else if (h.equals("year") || h.equals("wfh_year") || h.equals("wfhyear")) {
                    yearIdx = i;
                } else if (h.equals("allocated_days")
                        || h.equals("days")
                        || h.equals("entitlement_days")
                        || h.equals("entitlementdays")) {
                    daysIdx = i;
                } else if (h.equals("effective_date") || h.equals("effectivedate")) {
                    effectiveDateIdx = i;
                }
            }

            if (empCodeIdx == -1 || daysIdx == -1) {
                throw new IllegalArgumentException(
                        "CSV header must contain 'employee_number' (or 'employee_code') and 'entitlement_days' (or 'allocated_days') columns.");
            }

            WfhPolicy policy = policyService.getOrCreateDefaultPolicy(tenantId);

            String line;
            int lineNum = 1; // 1 was header
            while ((line = reader.readLine()) != null) {
                lineNum++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                rowsTotal++;
                String[] parts = trimmed.split(",", -1);
                if (parts.length <= Math.max(empCodeIdx, daysIdx)) {
                    errors.add("Row " + lineNum + ": Insufficient columns.");
                    rowsFailed++;
                    continue;
                }

                String empCode = parts[empCodeIdx].trim().replace("\"", "");
                if (empCode.isEmpty()) {
                    errors.add("Row " + lineNum + ": Missing employee code.");
                    rowsFailed++;
                    continue;
                }

                Optional<Employee> empOpt =
                        employeeRepository.findByTenantIdAndEmployeeNumberAndDeletedFalse(tenantId, empCode);
                if (empOpt.isEmpty()) {
                    errors.add("Row " + lineNum + ": Employee not found for code '" + empCode + "'.");
                    rowsFailed++;
                    continue;
                }

                String rowYear = (yearIdx != -1
                                && parts.length > yearIdx
                                && !parts[yearIdx].trim().replace("\"", "").isEmpty())
                        ? parts[yearIdx].trim().replace("\"", "")
                        : effectiveYear;

                BigDecimal allocatedDays;
                try {
                    String daysStr = parts[daysIdx].trim().replace("\"", "");
                    allocatedDays = new BigDecimal(daysStr).setScale(2, RoundingMode.HALF_UP);
                    if (allocatedDays.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add("Row " + lineNum + ": Allocated days cannot be negative.");
                        rowsFailed++;
                        continue;
                    }
                } catch (NumberFormatException nfe) {
                    errors.add("Row " + lineNum + ": Invalid allocated days number format.");
                    rowsFailed++;
                    continue;
                }

                if (!dryRun) {
                    int parsedYear;
                    try {
                        parsedYear = Integer.parseInt(rowYear);
                    } catch (Exception ex) {
                        parsedYear = LocalDate.now().getYear();
                    }

                    LocalDate yearStart = LocalDate.of(parsedYear, 1, 1);
                    LocalDate yearEnd = LocalDate.of(parsedYear, 12, 31);
                    BigDecimal proRateFactor = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

                    if (effectiveDateIdx != -1 && parts.length > effectiveDateIdx) {
                        String effDateStr = parts[effectiveDateIdx].trim().replace("\"", "");
                        if (!effDateStr.isEmpty()) {
                            try {
                                LocalDate effDate = LocalDate.parse(effDateStr);
                                yearStart = effDate;
                                int remainingMonths = Math.max(1, 13 - effDate.getMonthValue());
                                proRateFactor = BigDecimal.valueOf(remainingMonths)
                                        .divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    Employee emp = empOpt.get();

                    List<WfhAllocation> existingAllocs =
                            allocationRepository.findByTenantIdAndWfhYear(tenantId, rowYear);
                    WfhAllocation matching = existingAllocs.stream()
                            .filter(a -> a.getEmployeeId().equals(emp.getId()))
                            .findFirst()
                            .orElse(null);

                    if (matching != null) {
                        matching.setYearStartDate(yearStart);
                        matching.setYearEndDate(yearEnd);
                        matching.setEntitlementDays(allocatedDays);
                        matching.setProRateFactor(proRateFactor);
                        allocationRepository.save(matching);
                    } else {
                        WfhAllocation alloc = new WfhAllocation();
                        alloc.setTenantId(tenantId);
                        alloc.setEmployeeId(emp.getId());
                        alloc.setPolicyId(policy.getId());
                        alloc.setWfhYear(rowYear);
                        alloc.setYearStartDate(yearStart);
                        alloc.setYearEndDate(yearEnd);
                        alloc.setEntitlementDays(allocatedDays);
                        alloc.setProRateFactor(proRateFactor);
                        allocationRepository.save(alloc);
                    }
                }

                rowsImported++;
            }

            importLog.setRowsTotal(rowsTotal);
            importLog.setRowsImported(rowsImported);
            importLog.setRowsFailed(rowsFailed);
            importLog.setStatus(rowsFailed == 0 ? "COMPLETED" : (rowsImported > 0 ? "PARTIAL" : "FAILED"));
            if (!errors.isEmpty()) {
                importLog.setErrorSummary(String.join("\n", errors));
            }
            importLog.setFinishedAt(Instant.now());
            importLogRepository.save(importLog);

            return new WfhImportSummaryResponse(
                    importLog.getId(),
                    importLog.getFileName(),
                    importLog.getStatus(),
                    importLog.isDryRun(),
                    rowsTotal,
                    rowsImported,
                    rowsFailed,
                    errors,
                    importLog.getStartedAt(),
                    importLog.getFinishedAt());

        } catch (Exception ex) {
            log.error("Failed processing WFH CSV import", ex);
            importLog.setStatus("FAILED");
            importLog.setErrorSummary("Import failure: " + ex.getMessage());
            importLog.setFinishedAt(Instant.now());
            importLogRepository.save(importLog);
            errors.add(ex.getMessage());
            return new WfhImportSummaryResponse(
                    importLog.getId(),
                    importLog.getFileName(),
                    "FAILED",
                    dryRun,
                    rowsTotal,
                    rowsImported,
                    rowsFailed,
                    errors,
                    importLog.getStartedAt(),
                    importLog.getFinishedAt());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<WfhImportLog> listImports(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return importLogRepository.findByTenantIdOrderByStartedAtDesc(tenantId);
    }
}
