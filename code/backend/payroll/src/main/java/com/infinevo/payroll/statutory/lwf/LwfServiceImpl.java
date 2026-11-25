package com.infinevo.payroll.statutory.lwf;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link LwfService} providing state LWF calculation, configuration, and statement export (W-92).
 */
@Service
public class LwfServiceImpl implements LwfService {

    private static final Logger log = LoggerFactory.getLogger(LwfServiceImpl.class);

    public static final String COMPONENT_LWF_EE = "LWF_EMPLOYEE";
    public static final String COMPONENT_LWF_ER = "LWF_EMPLOYER";

    private static final Set<PayRunStatus> ALLOWED_STATUSES =
            Set.of(PayRunStatus.COMPUTED, PayRunStatus.APPROVED, PayRunStatus.PAID);

    private final OrgLwfSettingRepository orgLwfSettingRepository;
    private final LwfStateRepository lwfStateRepository;
    private final LwfRuleRepository lwfRuleRepository;
    private final WorkLocationService workLocationService;
    private final PayRunRepository payRunRepository;
    private final EmployeePayRunRepository employeePayRunRepository;
    private final EmployeePayRunLineRepository employeePayRunLineRepository;
    private final EmployeeService employeeService;

    @Autowired
    public LwfServiceImpl(
            OrgLwfSettingRepository orgLwfSettingRepository,
            LwfStateRepository lwfStateRepository,
            LwfRuleRepository lwfRuleRepository,
            @Autowired(required = false) WorkLocationService workLocationService,
            PayRunRepository payRunRepository,
            EmployeePayRunRepository employeePayRunRepository,
            EmployeePayRunLineRepository employeePayRunLineRepository,
            EmployeeService employeeService) {
        this.orgLwfSettingRepository =
                Objects.requireNonNull(orgLwfSettingRepository, "orgLwfSettingRepository must not be null");
        this.lwfStateRepository = Objects.requireNonNull(lwfStateRepository, "lwfStateRepository must not be null");
        this.lwfRuleRepository = Objects.requireNonNull(lwfRuleRepository, "lwfRuleRepository must not be null");
        this.workLocationService = workLocationService;
        this.payRunRepository = Objects.requireNonNull(payRunRepository, "payRunRepository must not be null");
        this.employeePayRunRepository =
                Objects.requireNonNull(employeePayRunRepository, "employeePayRunRepository must not be null");
        this.employeePayRunLineRepository =
                Objects.requireNonNull(employeePayRunLineRepository, "employeePayRunLineRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<LwfStateSettingResponse> listSettings(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Map<String, Long> locationCounts = getLocationCountsByState();
        if (locationCounts.isEmpty()) {
            return List.of();
        }

        List<String> sortedStateCodes = new ArrayList<>(locationCounts.keySet());
        Collections.sort(sortedStateCodes);

        List<LwfStateSettingResponse> responses = new ArrayList<>(sortedStateCodes.size());
        LocalDate today = LocalDate.now();

        for (String stateCode : sortedStateCodes) {
            int count = locationCounts.get(stateCode).intValue();
            responses.add(buildStateResponse(tenantId, stateCode, count, today));
        }

        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public LwfStateSettingResponse getStateSetting(UUID tenantId, String stateCode) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        String normCode = normalizeCode(stateCode);
        if (normCode == null) {
            throw new LwfValidationException("State code must not be null or blank");
        }

        Map<String, Long> locationCounts = getLocationCountsByState();
        int count = locationCounts.getOrDefault(normCode, 0L).intValue();

        return buildStateResponse(tenantId, normCode, count, LocalDate.now());
    }

    @Override
    @Transactional
    public LwfStateSettingResponse updateSetting(
            UUID tenantId, String stateCode, LwfSettingUpdateRequest request, String actor) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        String normCode = normalizeCode(stateCode);
        if (normCode == null) {
            throw new LwfValidationException("State code must not be null or blank");
        }
        if (request == null) {
            throw new LwfValidationException("Request body must not be null");
        }

        OrgLwfSetting setting = orgLwfSettingRepository
                .findByTenantIdAndStateCode(tenantId, normCode)
                .orElseGet(() -> new OrgLwfSetting(tenantId, normCode, null, true, actor != null ? actor : "system"));

        if (request.isEnabled() != null) {
            setting.setEnabled(request.isEnabled());
        }
        if (request.registrationNumber() != null) {
            setting.setRegistrationNumber(
                    request.registrationNumber().trim().isEmpty()
                            ? null
                            : request.registrationNumber().trim());
        }
        setting.setUpdated(actor != null ? actor : "system");
        orgLwfSettingRepository.save(setting);

        log.info(
                "Updated LWF setting for tenant {} state {}: enabled={}, registration={}",
                tenantId,
                normCode,
                setting.isEnabled(),
                setting.getRegistrationNumber());

        Map<String, Long> locationCounts = getLocationCountsByState();
        int count = locationCounts.getOrDefault(normCode, 0L).intValue();
        return buildStateResponse(tenantId, normCode, count, LocalDate.now());
    }

    @Override
    @Transactional(readOnly = true)
    public LwfCalculationResult calculate(
            UUID tenantId, String stateCode, BigDecimal grossEarnings, int month, LocalDate periodEnd) {
        if (tenantId == null || stateCode == null || stateCode.isBlank()) {
            return LwfCalculationResult.ZERO;
        }

        String normCode = stateCode.trim().toUpperCase();

        // 1. Check tenant override enablement
        Optional<OrgLwfSetting> settingOpt = orgLwfSettingRepository.findByTenantIdAndStateCode(tenantId, normCode);
        if (settingOpt.isPresent() && !settingOpt.get().isEnabled()) {
            return LwfCalculationResult.ZERO;
        }

        // 2. Check state LWF levy status
        Optional<LwfState> stateOpt = lwfStateRepository.findById(normCode);
        if (stateOpt.isPresent() && !stateOpt.get().isLeviesLwf()) {
            return LwfCalculationResult.ZERO;
        }

        // 3. Find in-force statutory rule
        LocalDate effectiveDate = periodEnd != null ? periodEnd : LocalDate.now();
        List<LwfRule> rules = lwfRuleRepository.findInForceRules(normCode, effectiveDate);
        if (rules.isEmpty()) {
            return LwfCalculationResult.ZERO;
        }

        LwfRule rule = rules.get(0);

        // 4. Check if current month is a deduction month
        if (!rule.appliesToMonth(month)) {
            return LwfCalculationResult.ZERO;
        }

        // 5. Evaluate gross salary bounds (if specified)
        if (grossEarnings != null) {
            if (grossEarnings.compareTo(rule.getMinGross()) < 0) {
                return LwfCalculationResult.ZERO;
            }
            if (rule.getMaxGross() != null && grossEarnings.compareTo(rule.getMaxGross()) > 0) {
                return LwfCalculationResult.ZERO;
            }
        }

        // 6. Compute employee and employer shares
        if (LwfRule.MODE_PERCENTAGE_WITH_CAP.equalsIgnoreCase(rule.getCalcMode())) {
            BigDecimal gross = grossEarnings != null ? grossEarnings : BigDecimal.ZERO;
            BigDecimal ee = BigDecimal.ZERO;
            BigDecimal er = BigDecimal.ZERO;

            if (rule.getEmployeeRate() != null) {
                ee = gross.multiply(rule.getEmployeeRate()).setScale(2, RoundingMode.HALF_UP);
                if (rule.getEmployeeCap() != null && ee.compareTo(rule.getEmployeeCap()) > 0) {
                    ee = rule.getEmployeeCap().setScale(2, RoundingMode.HALF_UP);
                }
            }

            if (rule.getEmployerRate() != null) {
                er = gross.multiply(rule.getEmployerRate()).setScale(2, RoundingMode.HALF_UP);
                if (rule.getEmployerCap() != null && er.compareTo(rule.getEmployerCap()) > 0) {
                    er = rule.getEmployerCap().setScale(2, RoundingMode.HALF_UP);
                }
            }

            return LwfCalculationResult.of(ee, er);
        } else {
            BigDecimal ee = rule.getEmployeeAmount() != null
                    ? rule.getEmployeeAmount().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal er = rule.getEmployerAmount() != null
                    ? rule.getEmployerAmount().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return LwfCalculationResult.of(ee, er);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportReturnFile(UUID tenantId, UUID payrunId, String format) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(payrunId, "payrunId must not be null");

        PayRun run = payRunRepository
                .findByIdAndTenantId(payrunId, tenantId)
                .orElseThrow(() -> new PayRunNotFoundException(payrunId));

        if (run.getRunType() != PayRunType.REGULAR) {
            throw new IllegalArgumentException("LWF return statements apply only to regular pay runs: " + payrunId);
        }
        if (!ALLOWED_STATUSES.contains(run.getStatus())) {
            throw new PayFigureNotComputedException(payrunId, run.getStatus());
        }

        List<EmployeePayRun> eprs = employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                tenantId, payrunId, InclusionStatus.INCLUDED);
        List<EmployeePayRunLine> lines = employeePayRunLineRepository.findByTenantIdAndPayrunId(tenantId, payrunId);

        Map<UUID, BigDecimal> eeLwfByEpr = lines.stream()
                .filter(l -> COMPONENT_LWF_EE.equalsIgnoreCase(l.getComponentCode()))
                .collect(Collectors.toMap(
                        EmployeePayRunLine::getEmployeePayrunId, EmployeePayRunLine::getAmount, BigDecimal::add));

        Map<UUID, BigDecimal> erLwfByEpr = lines.stream()
                .filter(l -> COMPONENT_LWF_ER.equalsIgnoreCase(l.getComponentCode()))
                .collect(Collectors.toMap(
                        EmployeePayRunLine::getEmployeePayrunId, EmployeePayRunLine::getAmount, BigDecimal::add));

        List<UUID> empIds = eprs.stream().map(EmployeePayRun::getEmployeeId).toList();
        Map<UUID, EmployeeResponse> empMap = employeeService.findByIdInIncludingDeleted(empIds);

        Map<UUID, String> workLocationStateMap = getWorkLocationStateMap();

        List<LwfReturnRow> rows = new ArrayList<>();
        for (EmployeePayRun epr : eprs) {
            BigDecimal ee = eeLwfByEpr.getOrDefault(epr.getId(), BigDecimal.ZERO);
            BigDecimal er = erLwfByEpr.getOrDefault(epr.getId(), BigDecimal.ZERO);

            if (ee.signum() > 0 || er.signum() > 0) {
                EmployeeResponse emp = empMap.get(epr.getEmployeeId());
                String empNo = emp != null ? emp.employeeNumber() : "";
                String name = formatName(emp);
                String stateCode = (emp != null && emp.workLocationId() != null)
                        ? workLocationStateMap.getOrDefault(emp.workLocationId(), "")
                        : "";
                BigDecimal gross = epr.getGrossEarnings() != null ? epr.getGrossEarnings() : BigDecimal.ZERO;

                rows.add(new LwfReturnRow(empNo, name, stateCode, gross, ee, er, ee.add(er)));
            }
        }

        rows.sort(Comparator.comparing(LwfReturnRow::employeeNumber));

        boolean isCsv = "CSV".equalsIgnoreCase(format);
        if (isCsv) {
            return generateCsv(rows);
        } else {
            return generateXlsx(rows, run);
        }
    }

    private LwfStateSettingResponse buildStateResponse(
            UUID tenantId, String stateCode, int activeLocationsCount, LocalDate date) {
        Optional<LwfState> stateOpt = lwfStateRepository.findById(stateCode);
        String stateName = stateOpt.map(LwfState::getStateName).orElse(stateCode);
        boolean levies = stateOpt.map(LwfState::isLeviesLwf).orElse(false);

        Optional<OrgLwfSetting> settingOpt = orgLwfSettingRepository.findByTenantIdAndStateCode(tenantId, stateCode);
        boolean isEnabled = settingOpt.map(OrgLwfSetting::isEnabled).orElse(true);
        String regNo = settingOpt.map(OrgLwfSetting::getRegistrationNumber).orElse(null);

        List<LwfRule> rules = lwfRuleRepository.findInForceRules(stateCode, date);
        LwfRule rule = rules.isEmpty() ? null : rules.get(0);

        String cycle = determineDeductionCycle(rule);
        List<Integer> months = rule != null ? rule.getDeductionMonthsAsList() : List.of();
        String calcMode = rule != null ? rule.getCalcMode() : LwfRule.MODE_FLAT;

        return new LwfStateSettingResponse(
                stateCode,
                stateName,
                levies,
                isEnabled,
                cycle,
                months,
                calcMode,
                rule != null ? rule.getEmployeeAmount() : null,
                rule != null ? rule.getEmployerAmount() : null,
                rule != null ? rule.getEmployeeRate() : null,
                rule != null ? rule.getEmployerRate() : null,
                rule != null ? rule.getEmployeeCap() : null,
                rule != null ? rule.getEmployerCap() : null,
                regNo,
                activeLocationsCount);
    }

    private static String determineDeductionCycle(LwfRule rule) {
        if (rule == null || rule.getDeductionMonths() == null || rule.getDeductionMonths().length == 0) {
            return "NONE";
        }
        List<Integer> months = rule.getDeductionMonthsAsList();
        if (months.size() == 12) {
            return "MONTHLY";
        }
        if (months.size() == 2 && months.contains(6) && months.contains(12)) {
            return "HALF_YEARLY_JUNE_DECEMBER";
        }
        if (months.size() == 1 && months.contains(12)) {
            return "ANNUAL_DECEMBER";
        }
        return "CUSTOM";
    }

    private Map<String, Long> getLocationCountsByState() {
        if (workLocationService == null) {
            return Map.of();
        }
        try {
            List<WorkLocationResponse> locations = workLocationService.list(true);
            return locations.stream()
                    .filter(l -> l.stateCode() != null && !l.stateCode().isBlank())
                    .collect(Collectors.groupingBy(l -> l.stateCode().trim().toUpperCase(), Collectors.counting()));
        } catch (Exception ex) {
            log.warn("Could not retrieve active work locations: {}", ex.getMessage());
            return Map.of();
        }
    }

    private Map<UUID, String> getWorkLocationStateMap() {
        if (workLocationService == null) {
            return Map.of();
        }
        try {
            List<WorkLocationResponse> locations = workLocationService.list(true);
            return locations.stream()
                    .filter(l -> l.id() != null && l.stateCode() != null)
                    .collect(Collectors.toMap(
                            WorkLocationResponse::id,
                            l -> l.stateCode().trim().toUpperCase(),
                            (first, second) -> first));
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static String formatName(EmployeeResponse emp) {
        if (emp == null) {
            return "";
        }
        String first = emp.firstName() != null ? emp.firstName().trim() : "";
        String last = emp.lastName() != null ? emp.lastName().trim() : "";
        return (first + " " + last).trim();
    }

    private static String normalizeCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return code.trim().toUpperCase();
    }

    private static byte[] generateCsv(List<LwfReturnRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "Employee Number,Employee Name,State,Gross Earnings,Employee Contribution,Employer Contribution,Total Remittance\r\n");
        for (LwfReturnRow r : rows) {
            sb.append(escapeCsv(r.employeeNumber)).append(",");
            sb.append(escapeCsv(r.employeeName)).append(",");
            sb.append(escapeCsv(r.stateCode)).append(",");
            sb.append(r.grossEarnings.setScale(2, RoundingMode.HALF_UP)).append(",");
            sb.append(r.employeeContribution.setScale(2, RoundingMode.HALF_UP)).append(",");
            sb.append(r.employerContribution.setScale(2, RoundingMode.HALF_UP)).append(",");
            sb.append(r.totalRemittance.setScale(2, RoundingMode.HALF_UP)).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] generateXlsx(List<LwfReturnRow> rows, PayRun run) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("LWF Return Statement");

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            CellStyle numericStyle = workbook.createCellStyle();
            numericStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            Row header = sheet.createRow(0);
            String[] headers = {
                "Employee Number",
                "Employee Name",
                "State",
                "Gross Earnings",
                "Employee Contribution",
                "Employer Contribution",
                "Total Remittance"
            };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (LwfReturnRow r : rows) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0, CellType.STRING).setCellValue(r.employeeNumber);
                row.createCell(1, CellType.STRING).setCellValue(r.employeeName);
                row.createCell(2, CellType.STRING).setCellValue(r.stateCode);

                Cell cGross = row.createCell(3, CellType.NUMERIC);
                cGross.setCellValue(r.grossEarnings.doubleValue());
                cGross.setCellStyle(numericStyle);

                Cell cEe = row.createCell(4, CellType.NUMERIC);
                cEe.setCellValue(r.employeeContribution.doubleValue());
                cEe.setCellStyle(numericStyle);

                Cell cEr = row.createCell(5, CellType.NUMERIC);
                cEr.setCellValue(r.employerContribution.doubleValue());
                cEr.setCellStyle(numericStyle);

                Cell cTotal = row.createCell(6, CellType.NUMERIC);
                cTotal.setCellValue(r.totalRemittance.doubleValue());
                cTotal.setCellStyle(numericStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate LWF return Excel file", e);
        }
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private record LwfReturnRow(
            String employeeNumber,
            String employeeName,
            String stateCode,
            BigDecimal grossEarnings,
            BigDecimal employeeContribution,
            BigDecimal employerContribution,
            BigDecimal totalRemittance) {}
}
