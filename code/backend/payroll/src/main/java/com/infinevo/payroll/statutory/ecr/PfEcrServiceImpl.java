package com.infinevo.payroll.statutory.ecr;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeePersonalService;
import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunLine;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.PayFigureNotComputedException;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunNotFoundException;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.salary.EmployeeStatutoryProfile;
import com.infinevo.payroll.salary.EmployeeStatutoryProfileRepository;
import com.infinevo.payroll.statutory.settings.EpfSettingResponse;
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
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link PfEcrService} generating EPFO ECR v2.0 text files and audit spreadsheets (W-87).
 */
@Service
@Transactional(readOnly = true)
public class PfEcrServiceImpl implements PfEcrService {

    private static final Logger log = LoggerFactory.getLogger(PfEcrServiceImpl.class);

    private static final Set<PayRunStatus> ALLOWED_STATUSES =
            Set.of(PayRunStatus.COMPUTED, PayRunStatus.APPROVED, PayRunStatus.PAID);

    private static final char BYTE_ORDER_MARK = (char) 0xFEFF;
    private static final String CRLF = "\r\n";
    private static final String DEFAULT_ESTABLISHMENT_CODE = "DLCPM0000000000";

    private static final MediaType TXT_MEDIA_TYPE = MediaType.parseMediaType("text/plain; charset=utf-8");
    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType CSV_MEDIA_TYPE = MediaType.parseMediaType("text/csv");

    private static final List<String> ECR_HEADERS = List.of(
            "UAN",
            "Member Name",
            "Gross Wages",
            "EPF Wages",
            "EPS Wages",
            "EDLI Wages",
            "EE Share Remitted",
            "EPS Contribution",
            "ER Share Remitted",
            "NCP Days",
            "Refund of Advances");

    private final PayRunRepository payRunRepository;
    private final EmployeePayRunRepository employeePayRunRepository;
    private final EmployeePayRunLineRepository employeePayRunLineRepository;
    private final EmployeeStatutoryProfileRepository employeeStatutoryProfileRepository;
    private final EmployeeService employeeService;
    private final StatutorySettingsService statutorySettingsService;
    private final EmployeePersonalService employeePersonalService;

    public PfEcrServiceImpl(
            PayRunRepository payRunRepository,
            EmployeePayRunRepository employeePayRunRepository,
            EmployeePayRunLineRepository employeePayRunLineRepository,
            EmployeeStatutoryProfileRepository employeeStatutoryProfileRepository,
            EmployeeService employeeService) {
        this(
                payRunRepository,
                employeePayRunRepository,
                employeePayRunLineRepository,
                employeeStatutoryProfileRepository,
                employeeService,
                null,
                null);
    }

    @Autowired
    public PfEcrServiceImpl(
            PayRunRepository payRunRepository,
            EmployeePayRunRepository employeePayRunRepository,
            EmployeePayRunLineRepository employeePayRunLineRepository,
            EmployeeStatutoryProfileRepository employeeStatutoryProfileRepository,
            EmployeeService employeeService,
            @Autowired(required = false) StatutorySettingsService statutorySettingsService,
            @Autowired(required = false) EmployeePersonalService employeePersonalService) {
        this.payRunRepository = Objects.requireNonNull(payRunRepository, "payRunRepository must not be null");
        this.employeePayRunRepository =
                Objects.requireNonNull(employeePayRunRepository, "employeePayRunRepository must not be null");
        this.employeePayRunLineRepository =
                Objects.requireNonNull(employeePayRunLineRepository, "employeePayRunLineRepository must not be null");
        this.employeeStatutoryProfileRepository = Objects.requireNonNull(
                employeeStatutoryProfileRepository, "employeeStatutoryProfileRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.statutorySettingsService = statutorySettingsService;
        this.employeePersonalService = employeePersonalService;
    }

    @Override
    public PfEcrPreviewResponse getEcrPreview(UUID payrunId) {
        UUID tenantId = TenantContext.require();
        PayRun run = validateAndGetRun(tenantId, payrunId);
        return buildPreview(tenantId, run);
    }

    @Override
    public PfEcrFile exportEcrFile(UUID payrunId, PfEcrFormat format) {
        UUID tenantId = TenantContext.require();
        PayRun run = validateAndGetRun(tenantId, payrunId);
        PfEcrPreviewResponse preview = buildPreview(tenantId, run);

        String establishment = preview.establishmentCode();
        String periodStr = run.getPeriod().toString().replace("-", "_");
        String baseName = "ECR_" + establishment + "_" + periodStr;

        PfEcrFormat selectedFormat = format != null ? format : PfEcrFormat.TXT;
        return switch (selectedFormat) {
            case TXT -> new PfEcrFile(baseName + ".txt", TXT_MEDIA_TYPE, generateEcrTxt(preview.rows()));
            case XLSX -> new PfEcrFile(baseName + ".xlsx", XLSX_MEDIA_TYPE, generateEcrXlsx(preview));
            case CSV -> new PfEcrFile(baseName + ".csv", CSV_MEDIA_TYPE, generateEcrCsv(preview));
        };
    }

    private PayRun validateAndGetRun(UUID tenantId, UUID payrunId) {
        PayRun run = payRunRepository
                .findByIdAndTenantId(payrunId, tenantId)
                .orElseThrow(() -> new PayRunNotFoundException(payrunId));

        if (run.getRunType() == PayRunType.OFF_CYCLE) {
            throw new IllegalArgumentException("Statutory ECR returns apply only to regular pay runs: " + payrunId);
        }

        if (!ALLOWED_STATUSES.contains(run.getStatus())) {
            throw new PayFigureNotComputedException(payrunId, run.getStatus());
        }

        return run;
    }

    private PfEcrPreviewResponse buildPreview(UUID tenantId, PayRun run) {
        String establishmentCode = resolveEstablishmentCode(tenantId);
        int seniorAge = 58;
        if (statutorySettingsService != null) {
            try {
                EpfSettingResponse epf = statutorySettingsService.epf(tenantId);
                if (epf != null && epf.epsSeniorAge() > 0) {
                    seniorAge = epf.epsSeniorAge();
                }
            } catch (Exception e) {
                log.debug("Could not read EPF setting for senior age: {}", e.getMessage());
            }
        }

        List<EmployeePayRun> employeePayruns = employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                tenantId, run.getId(), InclusionStatus.INCLUDED);

        if (employeePayruns.isEmpty()) {
            return new PfEcrPreviewResponse(
                    run.getId(),
                    run.getPeriod().toString(),
                    establishmentCode,
                    0,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    List.of(),
                    List.of());
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

        List<PfEcrRowResponse> rows = new ArrayList<>();
        List<PfEcrWarning> warnings = new ArrayList<>();

        long totalGross = 0;
        long totalEpf = 0;
        long totalEps = 0;
        long totalEdli = 0;
        long totalEe = 0;
        long totalEpsContrib = 0;
        long totalEr = 0;
        long totalAdminCharges = 0;
        long totalEdliCharges = 0;

        int daysInMonth = run.getPeriod().lengthOfMonth();

        for (EmployeePayRun epr : employeePayruns) {
            UUID empId = epr.getEmployeeId();
            EmployeeStatutoryProfile profile = profiles.get(empId);
            List<EmployeePayRunLine> empLines = linesByPayrunEmployee.getOrDefault(epr.getId(), List.of());

            BigDecimal eePfLine = sumComponent(empLines, "EPF_EMPLOYEE");
            BigDecimal erEpsLine = sumComponent(empLines, "EPS_EMPLOYER");
            BigDecimal erPfLine = sumComponent(empLines, "EPF_EMPLOYER");
            BigDecimal edliLine = sumComponent(empLines, "EDLI");
            BigDecimal adminLine = sumComponent(empLines, "EPF_ADMIN");

            boolean hasPfLine = eePfLine.signum() > 0 || erPfLine.signum() > 0 || erEpsLine.signum() > 0;
            boolean hasStoredWages =
                    epr.getEpfWage() != null && epr.getEpfWage().signum() > 0;
            boolean isPfEligible = profile != null && profile.isEligibleForPf();

            if (!hasPfLine && !hasStoredWages && !isPfEligible) {
                continue;
            }

            EmployeeResponse emp = employeeCache.get(empId);
            String rawUan = profile != null ? profile.getUan() : null;
            String cleanUan = cleanUan(rawUan);
            String memberName = formatMemberName(emp);
            String empNumber = (emp != null && emp.employeeNumber() != null) ? emp.employeeNumber() : "";
            String empDisplayName = (emp != null) ? formatMemberName(emp) : memberName;

            if (cleanUan.length() != 12) {
                warnings.add(new PfEcrWarning(
                        empId,
                        empNumber,
                        empDisplayName,
                        "MISSING_UAN",
                        "Active PF contributor has no valid 12-digit UAN recorded in statutory profile"));
            }

            boolean isSenior = isEpsSenior(empId, run.getPeriodStart(), seniorAge);
            boolean isEpsEligible = profile != null && profile.isEligibleForEps() && !isSenior;

            long grossWages = epr.getGrossEarnings() != null
                    ? epr.getGrossEarnings().setScale(0, RoundingMode.HALF_UP).longValue()
                    : 0L;

            long epfWages;
            if (epr.getEpfWage() != null) {
                epfWages = epr.getEpfWage().setScale(0, RoundingMode.HALF_UP).longValue();
            } else if (eePfLine.signum() > 0) {
                epfWages = eePfLine.divide(new BigDecimal("0.12"), 0, RoundingMode.HALF_UP)
                        .longValue();
            } else {
                epfWages = grossWages;
            }

            long epsWages;
            if (!isEpsEligible) {
                epsWages = 0L;
            } else if (epr.getEpsWage() != null) {
                epsWages = epr.getEpsWage().setScale(0, RoundingMode.HALF_UP).longValue();
            } else if (erEpsLine.signum() > 0) {
                epsWages = erEpsLine
                        .divide(new BigDecimal("0.0833"), 0, RoundingMode.HALF_UP)
                        .longValue();
            } else {
                epsWages = Math.min(epfWages, 15000L);
            }

            long edliWages;
            if (epr.getEdliWage() != null) {
                edliWages = epr.getEdliWage().setScale(0, RoundingMode.HALF_UP).longValue();
            } else {
                edliWages = Math.min(epfWages, 15000L);
            }

            long eeShare = eePfLine.setScale(0, RoundingMode.HALF_UP).longValue();
            if (eeShare == 0 && epfWages > 0) {
                eeShare = Math.round(epfWages * 0.12);
            }

            long epsShare;
            if (!isEpsEligible) {
                epsShare = 0L;
            } else {
                epsShare = erEpsLine.setScale(0, RoundingMode.HALF_UP).longValue();
                if (epsShare == 0 && epsWages > 0) {
                    epsShare = Math.round(epsWages * 0.0833);
                }
            }

            long erShare = Math.max(0L, eeShare - epsShare);

            int ncpDays = epr.getLopDays() != null
                    ? epr.getLopDays().setScale(0, RoundingMode.HALF_UP).intValue()
                    : 0;
            if (ncpDays < 0) {
                ncpDays = 0;
            }
            if (ncpDays > daysInMonth) {
                ncpDays = daysInMonth;
            }

            long refundOfAdvances = 0L;

            PfEcrRowResponse row = new PfEcrRowResponse(
                    cleanUan,
                    memberName,
                    grossWages,
                    epfWages,
                    epsWages,
                    edliWages,
                    eeShare,
                    epsShare,
                    erShare,
                    ncpDays,
                    refundOfAdvances);

            rows.add(row);

            totalGross += grossWages;
            totalEpf += epfWages;
            totalEps += epsWages;
            totalEdli += edliWages;
            totalEe += eeShare;
            totalEpsContrib += epsShare;
            totalEr += erShare;

            if (adminLine.signum() > 0) {
                totalAdminCharges += adminLine.setScale(0, RoundingMode.HALF_UP).longValue();
            }
            if (edliLine.signum() > 0) {
                totalEdliCharges += edliLine.setScale(0, RoundingMode.HALF_UP).longValue();
            }
        }

        if (totalAdminCharges == 0 && totalEpf > 0) {
            totalAdminCharges = Math.round(totalEpf * 0.0050);
        }
        if (totalEdliCharges == 0 && totalEdli > 0) {
            totalEdliCharges = Math.round(totalEdli * 0.0050);
        }

        long grandTotalChallan = totalEe + totalEpsContrib + totalEr + totalAdminCharges + totalEdliCharges;

        rows.sort(
                Comparator.comparing(PfEcrRowResponse::memberName, Comparator.nullsLast(String::compareToIgnoreCase)));

        return new PfEcrPreviewResponse(
                run.getId(),
                run.getPeriod().toString(),
                establishmentCode,
                rows.size(),
                totalGross,
                totalEpf,
                totalEps,
                totalEdli,
                totalEe,
                totalEpsContrib,
                totalEr,
                totalAdminCharges,
                totalEdliCharges,
                grandTotalChallan,
                warnings,
                rows);
    }

    private byte[] generateEcrTxt(List<PfEcrRowResponse> rows) {
        StringBuilder sb = new StringBuilder();
        for (PfEcrRowResponse row : rows) {
            sb.append(row.toEcrLine()).append(CRLF);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generateEcrXlsx(PfEcrPreviewResponse preview) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            SXSSFSheet sheet = workbook.createSheet("PF ECR Return");

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < ECR_HEADERS.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(ECR_HEADERS.get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (PfEcrRowResponse r : preview.rows()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(r.uan());
                row.createCell(1).setCellValue(r.memberName());
                row.createCell(2).setCellValue(r.grossWages());
                row.createCell(3).setCellValue(r.epfWages());
                row.createCell(4).setCellValue(r.epsWages());
                row.createCell(5).setCellValue(r.edliWages());
                row.createCell(6).setCellValue(r.eeShare());
                row.createCell(7).setCellValue(r.epsShare());
                row.createCell(8).setCellValue(r.erShare());
                row.createCell(9).setCellValue(r.ncpDays());
                row.createCell(10).setCellValue(r.refundOfAdvances());
            }

            Row totalRow = sheet.createRow(rowIndex);
            Cell totalLabel = totalRow.createCell(1);
            totalLabel.setCellValue("Total");
            totalLabel.setCellStyle(headerStyle);

            totalRow.createCell(2).setCellValue(preview.totalGrossWages());
            totalRow.createCell(3).setCellValue(preview.totalEpfWages());
            totalRow.createCell(4).setCellValue(preview.totalEpsWages());
            totalRow.createCell(5).setCellValue(preview.totalEdliWages());
            totalRow.createCell(6).setCellValue(preview.totalEeShare());
            totalRow.createCell(7).setCellValue(preview.totalEpsShare());
            totalRow.createCell(8).setCellValue(preview.totalErShare());
            totalRow.createCell(9).setCellValue(0);
            totalRow.createCell(10).setCellValue(0);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate PF ECR Excel workbook", e);
        }
    }

    private byte[] generateEcrCsv(PfEcrPreviewResponse preview) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Writer writer = new BufferedWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            writer.write(BYTE_ORDER_MARK);

            writeCsvLine(writer, ECR_HEADERS);

            for (PfEcrRowResponse r : preview.rows()) {
                writeCsvLine(
                        writer,
                        List.of(
                                r.uan(),
                                r.memberName(),
                                String.valueOf(r.grossWages()),
                                String.valueOf(r.epfWages()),
                                String.valueOf(r.epsWages()),
                                String.valueOf(r.edliWages()),
                                String.valueOf(r.eeShare()),
                                String.valueOf(r.epsShare()),
                                String.valueOf(r.erShare()),
                                String.valueOf(r.ncpDays()),
                                String.valueOf(r.refundOfAdvances())));
            }

            writeCsvLine(
                    writer,
                    List.of(
                            "",
                            "Total",
                            String.valueOf(preview.totalGrossWages()),
                            String.valueOf(preview.totalEpfWages()),
                            String.valueOf(preview.totalEpsWages()),
                            String.valueOf(preview.totalEdliWages()),
                            String.valueOf(preview.totalEeShare()),
                            String.valueOf(preview.totalEpsShare()),
                            String.valueOf(preview.totalErShare()),
                            "0",
                            "0"));

            writer.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate PF ECR CSV report", e);
        }
    }

    private static void writeCsvLine(Writer writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escapeCsv(values.get(i)));
        }
        writer.write(CRLF);
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

    private String resolveEstablishmentCode(UUID tenantId) {
        if (statutorySettingsService != null) {
            try {
                EpfSettingResponse epf = statutorySettingsService.epf(tenantId);
                if (epf != null
                        && epf.registrationNumber() != null
                        && !epf.registrationNumber().isBlank()) {
                    return epf.registrationNumber().trim().replaceAll("[^A-Za-z0-9]", "");
                }
            } catch (Exception e) {
                log.debug("Could not resolve establishment code from EpfSetting: {}", e.getMessage());
            }
        }
        return DEFAULT_ESTABLISHMENT_CODE;
    }

    private Map<UUID, EmployeeResponse> loadEmployees(List<UUID> empIds) {
        if (empIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Map<UUID, EmployeeResponse> map = employeeService.findByIdInIncludingDeleted(empIds);
            if (map != null && !map.isEmpty()) {
                return map;
            }
        } catch (Exception e) {
            log.warn("Could not batch load employees including deleted: {}", e.getMessage());
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

    private boolean isEpsSenior(UUID employeeId, LocalDate periodStart, int seniorAge) {
        if (employeePersonalService == null || employeeId == null || periodStart == null) {
            return false;
        }
        LocalDate dob;
        try {
            dob = employeePersonalService.dateOfBirth(employeeId).orElse(null);
        } catch (RuntimeException e) {
            return false;
        }
        if (dob == null || dob.isAfter(periodStart)) {
            return false;
        }
        return Period.between(dob, periodStart).getYears() >= seniorAge;
    }

    private static String cleanUan(String uan) {
        if (uan == null) {
            return "";
        }
        return uan.replaceAll("[^0-9]", "").trim();
    }

    private static String formatMemberName(EmployeeResponse emp) {
        if (emp == null) {
            return "UNKNOWN";
        }
        String first = emp.firstName() != null ? emp.firstName().trim() : "";
        String last = emp.lastName() != null ? emp.lastName().trim() : "";
        String full = (first + " " + last).trim();
        if (full.isEmpty()) {
            full = (emp.employeeNumber() != null && !emp.employeeNumber().isBlank())
                    ? emp.employeeNumber().trim()
                    : "UNKNOWN";
        }
        full = full.toUpperCase()
                .replace("#", "")
                .replace("~", "")
                .replace(",", "")
                .replaceAll("[^A-Z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (full.isEmpty()) {
            full = "UNKNOWN";
        }
        return full.length() > 80 ? full.substring(0, 80) : full;
    }

    private static BigDecimal sumComponent(List<EmployeePayRunLine> lines, String componentCode) {
        BigDecimal sum = BigDecimal.ZERO;
        for (EmployeePayRunLine line : lines) {
            if (componentCode.equals(line.getComponentCode()) && line.getAmount() != null) {
                sum = sum.add(line.getAmount());
            }
        }
        return sum;
    }
}
