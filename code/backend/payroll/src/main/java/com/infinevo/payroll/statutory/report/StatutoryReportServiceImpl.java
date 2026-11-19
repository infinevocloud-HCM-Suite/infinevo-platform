package com.infinevo.payroll.statutory.report;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import com.infinevo.core.report.ExportFormat;
import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunLine;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.LineKind;
import com.infinevo.payroll.payrun.LineSource;
import com.infinevo.payroll.payrun.PayFigureNotComputedException;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunNotFoundException;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.salary.EmployeeStatutoryProfile;
import com.infinevo.payroll.salary.EmployeeStatutoryProfileRepository;
import com.infinevo.payroll.statutory.pt.OrgPtOverrideRepository;
import com.infinevo.payroll.statutory.settings.EsiSettingResponse;
import com.infinevo.payroll.statutory.settings.StatutorySettingsService;
import com.infinevo.shared.tenant.TenantContext;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link StatutoryReportService} (W-95).
 * Generates ESI and state-wise PT return reports from computed pay runs using streaming SXSSF and CSV writers.
 */
@Service
@Transactional(readOnly = true)
public class StatutoryReportServiceImpl implements StatutoryReportService {

    private static final Logger log = LoggerFactory.getLogger(StatutoryReportServiceImpl.class);

    private static final Set<PayRunStatus> ALLOWED_STATUSES =
            Set.of(PayRunStatus.COMPUTED, PayRunStatus.APPROVED, PayRunStatus.PAID);

    private static final String NOT_CONFIGURED = "NOT_CONFIGURED";
    private static final char BYTE_ORDER_MARK = (char) 0xFEFF;
    private static final String NEWLINE = "\r\n";

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType CSV_MEDIA_TYPE = MediaType.parseMediaType("text/csv");

    private static final List<String> ESI_HEADERS = List.of(
            "IP Number",
            "IP Name",
            "No. of Days Worked",
            "Total Monthly Wages",
            "IP Contribution",
            "Employer Contribution",
            "Total Contribution",
            "Reason Code for Zero Days");

    private static final List<String> PT_RETURN_HEADERS =
            List.of("State", "PTRC Number", "Employee Code", "Employee Name", "Gross Salary", "PT Deducted");

    private static final List<String> PT_SUMMARY_HEADERS =
            List.of("State Code", "PTRC Number", "Total Employees Liable", "Total PT Deducted");

    private final PayRunRepository payRunRepository;
    private final EmployeePayRunRepository employeePayRunRepository;
    private final EmployeePayRunLineRepository employeePayRunLineRepository;
    private final EmployeeStatutoryProfileRepository employeeStatutoryProfileRepository;
    private final OrgPtOverrideRepository orgPtOverrideRepository;
    private final EmployeeService employeeService;
    private final WorkLocationService workLocationService;
    private final StatutorySettingsService statutorySettingsService;

    public StatutoryReportServiceImpl(
            PayRunRepository payRunRepository,
            EmployeePayRunRepository employeePayRunRepository,
            EmployeePayRunLineRepository employeePayRunLineRepository,
            EmployeeStatutoryProfileRepository employeeStatutoryProfileRepository,
            OrgPtOverrideRepository orgPtOverrideRepository,
            EmployeeService employeeService,
            WorkLocationService workLocationService) {
        this(
                payRunRepository,
                employeePayRunRepository,
                employeePayRunLineRepository,
                employeeStatutoryProfileRepository,
                orgPtOverrideRepository,
                employeeService,
                workLocationService,
                null);
    }

    public StatutoryReportServiceImpl(
            PayRunRepository payRunRepository,
            EmployeePayRunRepository employeePayRunRepository,
            EmployeePayRunLineRepository employeePayRunLineRepository,
            EmployeeStatutoryProfileRepository employeeStatutoryProfileRepository,
            OrgPtOverrideRepository orgPtOverrideRepository,
            EmployeeService employeeService,
            WorkLocationService workLocationService,
            StatutorySettingsService statutorySettingsService) {
        this.payRunRepository = Objects.requireNonNull(payRunRepository, "payRunRepository must not be null");
        this.employeePayRunRepository =
                Objects.requireNonNull(employeePayRunRepository, "employeePayRunRepository must not be null");
        this.employeePayRunLineRepository =
                Objects.requireNonNull(employeePayRunLineRepository, "employeePayRunLineRepository must not be null");
        this.employeeStatutoryProfileRepository = Objects.requireNonNull(
                employeeStatutoryProfileRepository, "employeeStatutoryProfileRepository must not be null");
        this.orgPtOverrideRepository =
                Objects.requireNonNull(orgPtOverrideRepository, "orgPtOverrideRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.workLocationService = Objects.requireNonNull(workLocationService, "workLocationService must not be null");
        this.statutorySettingsService = statutorySettingsService;
    }

    @Override
    public byte[] generateEsiReturn(UUID payrunId, ExportFormat format) {
        return generateEsiReturnFile(payrunId, format).content();
    }

    @Override
    public byte[] generatePtReturn(UUID payrunId, ExportFormat format, String state) {
        return generatePtReturnFile(payrunId, format, state).content();
    }

    @Override
    public StatutoryReportFile generateEsiReturnFile(UUID payrunId, ExportFormat format) {
        UUID tenantId = TenantContext.require();
        PayRun run = getAndValidateRun(payrunId, tenantId);

        List<EsiReturnRow> rows = collectEsiRows(tenantId, run);
        byte[] bytes =
                switch (format) {
                    case XLSX -> renderEsiXlsx(rows);
                    case CSV -> renderEsiCsv(rows);
                };

        String filename = "esi-return-" + run.getPeriod() + "." + format.extension();
        MediaType mediaType = format == ExportFormat.XLSX ? XLSX_MEDIA_TYPE : CSV_MEDIA_TYPE;
        return new StatutoryReportFile(bytes, filename, mediaType);
    }

    @Override
    public StatutoryReportFile generatePtReturnFile(UUID payrunId, ExportFormat format, String state) {
        UUID tenantId = TenantContext.require();
        PayRun run = getAndValidateRun(payrunId, tenantId);

        List<PtReturnRow> rows = collectPtRows(tenantId, run, state);
        List<PtStateSummary> summaries = buildPtSummaries(rows);

        byte[] bytes =
                switch (format) {
                    case XLSX -> renderPtXlsx(rows, summaries);
                    case CSV -> renderPtCsv(rows);
                };

        String stateSuffix = (state != null && !state.isBlank()) ? state.trim().toLowerCase() + "-" : "";
        String filename = "pt-return-" + stateSuffix + run.getPeriod() + "." + format.extension();
        MediaType mediaType = format == ExportFormat.XLSX ? XLSX_MEDIA_TYPE : CSV_MEDIA_TYPE;
        return new StatutoryReportFile(bytes, filename, mediaType);
    }

    private PayRun getAndValidateRun(UUID payrunId, UUID tenantId) {
        PayRun run = payRunRepository
                .findByIdAndTenantId(payrunId, tenantId)
                .orElseThrow(() -> new PayRunNotFoundException(payrunId));

        if (!ALLOWED_STATUSES.contains(run.getStatus())) {
            throw new PayFigureNotComputedException(payrunId, run.getStatus());
        }
        return run;
    }

    private List<EsiReturnRow> collectEsiRows(UUID tenantId, PayRun run) {
        List<EmployeePayRun> employeePayruns = employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                tenantId, run.getId(), InclusionStatus.INCLUDED);

        if (employeePayruns.isEmpty()) {
            return List.of();
        }

        List<EmployeePayRunLine> lines = employeePayRunLineRepository.findByTenantIdAndPayrunId(tenantId, run.getId());
        Map<UUID, List<EmployeePayRunLine>> linesByPayrunEmployee =
                lines.stream().collect(Collectors.groupingBy(EmployeePayRunLine::getEmployeePayrunId));

        List<UUID> empIds =
                employeePayruns.stream().map(EmployeePayRun::getEmployeeId).toList();
        Map<UUID, EmployeeStatutoryProfile> profiles =
                employeeStatutoryProfileRepository.findAllByTenantIdAndEmployeeIdIn(tenantId, empIds).stream()
                        .collect(Collectors.toMap(EmployeeStatutoryProfile::getEmployeeId, p -> p, (a, b) -> a));

        Map<UUID, EmployeeResponse> employeeCache = loadEmployees(empIds);

        List<EsiReturnRow> result = new ArrayList<>();
        for (EmployeePayRun epr : employeePayruns) {
            UUID empId = epr.getEmployeeId();
            EmployeeStatutoryProfile profile = profiles.get(empId);
            List<EmployeePayRunLine> empLines = linesByPayrunEmployee.getOrDefault(epr.getId(), List.of());

            BigDecimal ipContrib = sumComponent(empLines, "ESI_EMPLOYEE");
            BigDecimal erContrib = sumComponent(empLines, "ESI_EMPLOYER");

            boolean eligible = (profile != null && profile.isEligibleForEsi())
                    || ipContrib.compareTo(BigDecimal.ZERO) > 0
                    || erContrib.compareTo(BigDecimal.ZERO) > 0;

            if (!eligible) {
                continue;
            }

            EmployeeResponse emp = employeeCache.get(empId);
            String ipNumber = (profile != null && profile.getEsiNumber() != null) ? profile.getEsiNumber() : "";
            String ipName = formatEmployeeName(emp);
            BigDecimal paidDays = epr.getPaidDays() != null ? epr.getPaidDays() : BigDecimal.ZERO;
            BigDecimal monthlyWages = computeEsiMonthlyWages(epr, empLines, paidDays, ipContrib, erContrib, tenantId);
            BigDecimal totalContrib = ipContrib.add(erContrib);
            String reasonCode = (paidDays.compareTo(BigDecimal.ZERO) == 0) ? "1" : "";

            result.add(new EsiReturnRow(
                    ipNumber, ipName, paidDays, monthlyWages, ipContrib, erContrib, totalContrib, reasonCode));
        }

        result.sort(Comparator.comparing(EsiReturnRow::ipNumber, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(EsiReturnRow::ipName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return result;
    }

    private List<PtReturnRow> collectPtRows(UUID tenantId, PayRun run, String stateFilter) {
        List<EmployeePayRun> employeePayruns = employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                tenantId, run.getId(), InclusionStatus.INCLUDED);

        if (employeePayruns.isEmpty()) {
            return List.of();
        }

        List<EmployeePayRunLine> lines = employeePayRunLineRepository.findByTenantIdAndPayrunId(tenantId, run.getId());
        Map<UUID, List<EmployeePayRunLine>> linesByPayrunEmployee =
                lines.stream().collect(Collectors.groupingBy(EmployeePayRunLine::getEmployeePayrunId));

        List<UUID> empIds =
                employeePayruns.stream().map(EmployeePayRun::getEmployeeId).toList();
        Map<UUID, EmployeeResponse> employeeCache = loadEmployees(empIds);

        Map<UUID, WorkLocationResponse> locationCache = loadWorkLocations(employeeCache.values());

        Map<String, String> ptRegistrationMap = orgPtOverrideRepository.findAllByTenantId(tenantId).stream()
                .filter(o -> o.getStateCode() != null)
                .collect(Collectors.toMap(
                        o -> o.getStateCode().trim().toUpperCase(),
                        o -> (o.getRegistrationNumber() != null
                                        && !o.getRegistrationNumber().isBlank())
                                ? o.getRegistrationNumber()
                                : NOT_CONFIGURED,
                        (a, b) -> a));

        List<PtReturnRow> result = new ArrayList<>();
        for (EmployeePayRun epr : employeePayruns) {
            UUID empId = epr.getEmployeeId();
            List<EmployeePayRunLine> empLines = linesByPayrunEmployee.getOrDefault(epr.getId(), List.of());

            BigDecimal ptDeducted = sumComponent(empLines, "PROFESSIONAL_TAX");
            if (ptDeducted.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            EmployeeResponse emp = employeeCache.get(empId);
            WorkLocationResponse loc =
                    (emp != null && emp.workLocationId() != null) ? locationCache.get(emp.workLocationId()) : null;

            String stateCode = resolveState(loc);
            if (stateFilter != null && !stateFilter.isBlank() && !stateCode.equalsIgnoreCase(stateFilter.trim())) {
                continue;
            }

            String ptrcNumber = ptRegistrationMap.getOrDefault(stateCode, NOT_CONFIGURED);
            String empCode = (emp != null && emp.employeeNumber() != null) ? emp.employeeNumber() : "";
            String empName = formatEmployeeName(emp);
            BigDecimal grossSalary = epr.getGrossEarnings() != null ? epr.getGrossEarnings() : BigDecimal.ZERO;

            result.add(new PtReturnRow(stateCode, ptrcNumber, empCode, empName, grossSalary, ptDeducted));
        }

        result.sort(Comparator.comparing(PtReturnRow::state, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(PtReturnRow::employeeCode, Comparator.nullsLast(String::compareToIgnoreCase)));
        return result;
    }

    private List<PtStateSummary> buildPtSummaries(List<PtReturnRow> ptRows) {
        Map<String, List<PtReturnRow>> byState = ptRows.stream()
                .collect(Collectors.groupingBy(PtReturnRow::state, LinkedHashMap::new, Collectors.toList()));

        List<PtStateSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<PtReturnRow>> entry : byState.entrySet()) {
            String stateCode = entry.getKey();
            List<PtReturnRow> stateRows = entry.getValue();
            String ptrcNumber =
                    stateRows.isEmpty() ? NOT_CONFIGURED : stateRows.getFirst().ptrcNumber();
            long count = stateRows.size();
            BigDecimal totalPt = stateRows.stream()
                    .map(PtReturnRow::ptDeducted)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            summaries.add(new PtStateSummary(stateCode, ptrcNumber, count, totalPt));
        }
        summaries.sort(
                Comparator.comparing(PtStateSummary::stateCode, Comparator.nullsLast(String::compareToIgnoreCase)));
        return summaries;
    }

    private BigDecimal computeEsiMonthlyWages(
            EmployeePayRun epr,
            List<EmployeePayRunLine> empLines,
            BigDecimal paidDays,
            BigDecimal ipContrib,
            BigDecimal erContrib,
            UUID tenantId) {
        if (paidDays.compareTo(BigDecimal.ZERO) == 0
                || (ipContrib.compareTo(BigDecimal.ZERO) == 0 && erContrib.compareTo(BigDecimal.ZERO) == 0)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal eeRate = new BigDecimal("0.7500");
        BigDecimal erRate = new BigDecimal("3.2500");
        if (statutorySettingsService != null) {
            try {
                EsiSettingResponse esiResp = statutorySettingsService.esi(tenantId);
                if (esiResp != null) {
                    if (esiResp.employeeRate() != null) {
                        eeRate = esiResp.employeeRate();
                    }
                    if (esiResp.employerRate() != null) {
                        erRate = esiResp.employerRate();
                    }
                }
            } catch (Exception e) {
                log.debug("Using default ESI rates for tenant {}: {}", tenantId, e.getMessage());
            }
        }

        BigDecimal lop = sumComponent(empLines, "LOP");
        BigDecimal baseGross = BigDecimal.ZERO;
        for (EmployeePayRunLine l : empLines) {
            if (l.getLineKind() == LineKind.EARNING && l.getSource() == LineSource.STRUCTURE && l.getAmount() != null) {
                baseGross = baseGross.add(l.getAmount());
            }
        }
        if (baseGross.compareTo(BigDecimal.ZERO) == 0 && epr.getGrossEarnings() != null) {
            baseGross = epr.getGrossEarnings();
        }

        BigDecimal earnedCandidate = baseGross.subtract(lop);
        if (epr.getUnpaidDays() != null
                && epr.getUnpaidDays().compareTo(BigDecimal.ZERO) > 0
                && lop.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal totalDays = paidDays.add(epr.getUnpaidDays());
            if (totalDays.compareTo(BigDecimal.ZERO) > 0) {
                earnedCandidate = baseGross.multiply(paidDays).divide(totalDays, 4, RoundingMode.HALF_UP);
            }
        }
        if (earnedCandidate.compareTo(BigDecimal.ZERO) < 0) {
            earnedCandidate = BigDecimal.ZERO;
        }

        if (ipContrib.compareTo(BigDecimal.ZERO) > 0 && eeRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal expectedEe = earnedCandidate
                    .multiply(eeRate)
                    .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                    .setScale(0, RoundingMode.CEILING);
            if (expectedEe.compareTo(ipContrib.setScale(0, RoundingMode.CEILING)) == 0) {
                return earnedCandidate.setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal rateFrac = eeRate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
            return ipContrib.divide(rateFrac, 2, RoundingMode.HALF_UP);
        } else if (erContrib.compareTo(BigDecimal.ZERO) > 0 && erRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal expectedEr = earnedCandidate
                    .multiply(erRate)
                    .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                    .setScale(0, RoundingMode.CEILING);
            if (expectedEr.compareTo(erContrib.setScale(0, RoundingMode.CEILING)) == 0) {
                return earnedCandidate.setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal rateFrac = erRate.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
            return erContrib.divide(rateFrac, 2, RoundingMode.HALF_UP);
        } else {
            return earnedCandidate.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private Map<UUID, EmployeeResponse> loadEmployees(List<UUID> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<UUID, EmployeeResponse> map = employeeService.findByIdInIncludingDeleted(empIds);
            if (map != null && !map.isEmpty()) {
                return map;
            }
        } catch (Exception e) {
            log.warn("Could not batch load employees: {}", e.getMessage());
        }
        Map<UUID, EmployeeResponse> map = new HashMap<>();
        for (UUID empId : empIds) {
            try {
                EmployeeResponse emp = employeeService.get(empId);
                if (emp != null) {
                    map.put(empId, emp);
                }
            } catch (Exception e) {
                log.warn("Could not load employee details for ID {}: {}", empId, e.getMessage());
            }
        }
        return map;
    }

    private Map<UUID, WorkLocationResponse> loadWorkLocations(Iterable<EmployeeResponse> employees) {
        Map<UUID, WorkLocationResponse> map = new HashMap<>();
        for (EmployeeResponse emp : employees) {
            if (emp.workLocationId() != null && !map.containsKey(emp.workLocationId())) {
                try {
                    WorkLocationResponse loc = workLocationService.get(emp.workLocationId());
                    if (loc != null) {
                        map.put(emp.workLocationId(), loc);
                    }
                } catch (Exception e) {
                    log.warn("Could not load work location for ID {}: {}", emp.workLocationId(), e.getMessage());
                }
            }
        }
        return map;
    }

    private static String resolveState(WorkLocationResponse loc) {
        if (loc == null) {
            return "UNKNOWN";
        }
        if (loc.stateCode() != null && !loc.stateCode().isBlank()) {
            return loc.stateCode().trim().toUpperCase();
        }
        if (loc.state() != null && !loc.state().isBlank()) {
            return loc.state().trim().toUpperCase();
        }
        return "UNKNOWN";
    }

    private static BigDecimal sumComponent(List<EmployeePayRunLine> lines, String componentCode) {
        return lines.stream()
                .filter(l -> componentCode.equals(l.getComponentCode()))
                .map(EmployeePayRunLine::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String formatEmployeeName(EmployeeResponse emp) {
        if (emp == null) {
            return "";
        }
        return Stream.of(emp.firstName(), emp.middleName(), emp.lastName())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));
    }

    private static String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatDays(BigDecimal days) {
        if (days == null) {
            return "0";
        }
        return days.stripTrailingZeros().toPlainString();
    }

    // --- XLSX Rendering ---

    private byte[] renderEsiXlsx(List<EsiReturnRow> rows) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            SXSSFSheet sheet = workbook.createSheet("ESI Return");
            writeHeaderRow(workbook, sheet, ESI_HEADERS);

            int rowIndex = 1;
            for (EsiReturnRow r : rows) {
                Row row = sheet.createRow(rowIndex++);
                writeCell(row, 0, r.ipNumber());
                writeCell(row, 1, r.ipName());
                writeCell(row, 2, formatDays(r.paidDays()));
                writeCell(row, 3, formatMoney(r.totalMonthlyWages()));
                writeCell(row, 4, formatMoney(r.ipContribution()));
                writeCell(row, 5, formatMoney(r.employerContribution()));
                writeCell(row, 6, formatMoney(r.totalContribution()));
                writeCell(row, 7, r.reasonCodeZeroDays());
            }

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate ESI XLSX report", e);
        } finally {
            try {
                workbook.close();
            } catch (IOException ignored) {
            }
            workbook.dispose();
        }
    }

    private byte[] renderPtXlsx(List<PtReturnRow> rows, List<PtStateSummary> summaries) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            SXSSFSheet sheet = workbook.createSheet("PT Return");
            writeHeaderRow(workbook, sheet, PT_RETURN_HEADERS);

            int rowIndex = 1;
            for (PtReturnRow r : rows) {
                Row row = sheet.createRow(rowIndex++);
                writeCell(row, 0, r.state());
                writeCell(row, 1, r.ptrcNumber());
                writeCell(row, 2, r.employeeCode());
                writeCell(row, 3, r.employeeName());
                writeCell(row, 4, formatMoney(r.grossSalary()));
                writeCell(row, 5, formatMoney(r.ptDeducted()));
            }

            SXSSFSheet summarySheet = workbook.createSheet("State Summary");
            writeHeaderRow(workbook, summarySheet, PT_SUMMARY_HEADERS);

            int summaryRowIndex = 1;
            for (PtStateSummary s : summaries) {
                Row row = summarySheet.createRow(summaryRowIndex++);
                writeCell(row, 0, s.stateCode());
                writeCell(row, 1, s.ptrcNumber());
                writeCell(row, 2, String.valueOf(s.totalEmployeesLiable()));
                writeCell(row, 3, formatMoney(s.totalPtDeducted()));
            }

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate PT XLSX report", e);
        } finally {
            try {
                workbook.close();
            } catch (IOException ignored) {
            }
            workbook.dispose();
        }
    }

    private static void writeHeaderRow(SXSSFWorkbook workbook, SXSSFSheet sheet, List<String> headers) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(style);
        }
    }

    private static void writeCell(Row row, int colIndex, String value) {
        Cell cell = row.createCell(colIndex);
        if (value != null) {
            cell.setCellValue(neutraliseFormula(value));
        }
    }

    // --- CSV Rendering ---

    private byte[] renderEsiCsv(List<EsiReturnRow> rows) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Writer writer = new BufferedWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            writer.write(BYTE_ORDER_MARK);
            writeCsvLine(writer, ESI_HEADERS);

            for (EsiReturnRow r : rows) {
                writeCsvLine(
                        writer,
                        List.of(
                                r.ipNumber() != null ? r.ipNumber() : "",
                                r.ipName() != null ? r.ipName() : "",
                                formatDays(r.paidDays()),
                                formatMoney(r.totalMonthlyWages()),
                                formatMoney(r.ipContribution()),
                                formatMoney(r.employerContribution()),
                                formatMoney(r.totalContribution()),
                                r.reasonCodeZeroDays() != null ? r.reasonCodeZeroDays() : ""));
            }
            writer.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate ESI CSV report", e);
        }
    }

    private byte[] renderPtCsv(List<PtReturnRow> rows) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Writer writer = new BufferedWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            writer.write(BYTE_ORDER_MARK);
            writeCsvLine(writer, PT_RETURN_HEADERS);

            for (PtReturnRow r : rows) {
                writeCsvLine(
                        writer,
                        List.of(
                                r.state() != null ? r.state() : "",
                                r.ptrcNumber() != null ? r.ptrcNumber() : "",
                                r.employeeCode() != null ? r.employeeCode() : "",
                                r.employeeName() != null ? r.employeeName() : "",
                                formatMoney(r.grossSalary()),
                                formatMoney(r.ptDeducted())));
            }
            writer.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate PT CSV report", e);
        }
    }

    private static void writeCsvLine(Writer writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escapeCsv(values.get(i)));
        }
        writer.write(NEWLINE);
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String safe = neutraliseFormula(value);
        boolean quote =
                safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0;
        if (!quote) {
            return safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private static String neutraliseFormula(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}
