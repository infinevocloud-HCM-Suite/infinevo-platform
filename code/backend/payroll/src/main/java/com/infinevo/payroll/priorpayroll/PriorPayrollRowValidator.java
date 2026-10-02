package com.infinevo.payroll.priorpayroll;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Validates parsed prior payroll import rows before database writes (W-38.1 §4).
 */
@Component
public class PriorPayrollRowValidator {

    private static final ZoneId DEFAULT_TENANT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^\\d+(\\.\\d{1,2})?$");

    public record ValidatedPriorPayrollRow(
            PriorPayrollRow rawRow,
            Employee employee,
            String period,
            BigDecimal grossEarnings,
            BigDecimal epfEmployee,
            BigDecimal esiEmployee,
            BigDecimal professionalTax,
            BigDecimal tds,
            BigDecimal netPay) {}

    public record ValidationResult(List<ValidatedPriorPayrollRow> validRows, List<PriorPayrollRowError> errors) {}

    private final EmployeeRepository employeeRepository;
    private final PayRunRepository payRunRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public PriorPayrollRowValidator(
            EmployeeRepository employeeRepository,
            PayRunRepository payRunRepository,
            @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        this(employeeRepository, payRunRepository, jdbcTemplate, Clock.systemUTC());
    }

    public PriorPayrollRowValidator(
            EmployeeRepository employeeRepository,
            PayRunRepository payRunRepository,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.payRunRepository = Objects.requireNonNull(payRunRepository, "payRunRepository must not be null");
        this.jdbcTemplate = jdbcTemplate;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public ValidationResult validate(UUID tenantId, String financialYearStr, List<PriorPayrollRow> rows) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(financialYearStr, "financialYearStr must not be null");
        Objects.requireNonNull(rows, "rows must not be null");

        FinancialYear fy = FinancialYear.parse(financialYearStr);
        LocalDate today = tenantToday(tenantId);
        YearMonth currentYearMonth = YearMonth.from(today);

        Map<String, Optional<Employee>> employeeCache = new HashMap<>();
        Map<String, Boolean> realRunCache = new HashMap<>();
        Set<String> seenInFile = new HashSet<>();

        List<ValidatedPriorPayrollRow> validRows = new ArrayList<>();
        List<PriorPayrollRowError> errors = new ArrayList<>();

        for (PriorPayrollRow row : rows) {
            if (row.employeeNumber() == null
                    || row.period() == null
                    || row.grossEarnings() == null
                    || row.netPay() == null) {
                errors.add(new PriorPayrollRowError(
                        row.lineNumber(), row.employeeNumber(), row.period(), "INVALID_FORMAT"));
                continue;
            }
            String empNum = row.employeeNumber().trim();
            String period = row.period().trim();

            // 1. Employee lookup
            if (empNum.isEmpty()) {
                errors.add(
                        new PriorPayrollRowError(row.lineNumber(), row.employeeNumber(), period, "UNKNOWN_EMPLOYEE"));
                continue;
            }
            Optional<Employee> empOpt = employeeCache.computeIfAbsent(
                    empNum, num -> employeeRepository.findByTenantIdAndEmployeeNumberAndDeletedFalse(tenantId, num));
            if (empOpt.isEmpty()) {
                errors.add(
                        new PriorPayrollRowError(row.lineNumber(), row.employeeNumber(), period, "UNKNOWN_EMPLOYEE"));
                continue;
            }
            Employee employee = empOpt.get();

            // 2. Period format (YYYY-MM)
            if (period.isEmpty() || !PERIOD_PATTERN.matcher(period).matches()) {
                errors.add(new PriorPayrollRowError(
                        row.lineNumber(), row.employeeNumber(), row.period(), "INVALID_PERIOD"));
                continue;
            }

            YearMonth ym;
            try {
                ym = YearMonth.parse(period);
            } catch (DateTimeException e) {
                errors.add(new PriorPayrollRowError(
                        row.lineNumber(), row.employeeNumber(), row.period(), "INVALID_PERIOD"));
                continue;
            }

            // 3. Financial year check (April to March)
            LocalDate firstDayOfMonth = ym.atDay(1);
            if (!fy.contains(firstDayOfMonth)) {
                errors.add(
                        new PriorPayrollRowError(row.lineNumber(), row.employeeNumber(), row.period(), "OUTSIDE_YEAR"));
                continue;
            }

            // 4. Past check: period must be before current month in tenant's timezone
            if (!ym.isBefore(currentYearMonth)) {
                errors.add(new PriorPayrollRowError(row.lineNumber(), row.employeeNumber(), row.period(), "NOT_PAST"));
                continue;
            }

            // 5. Joining date check: period end must not be before employee date of joining
            LocalDate periodEnd = ym.atEndOfMonth();
            if (employee.getDateOfJoining() != null && periodEnd.isBefore(employee.getDateOfJoining())) {
                errors.add(new PriorPayrollRowError(
                        row.lineNumber(), row.employeeNumber(), row.period(), "BEFORE_JOINING"));
                continue;
            }

            // 6. Real run exists: regular non-cancelled pay run exists for period
            boolean regularRunExists = realRunCache.computeIfAbsent(
                    period,
                    p -> payRunRepository.existsByTenantIdAndPeriodAndRunTypeAndStatusNot(
                            tenantId, p, PayRunType.REGULAR, PayRunStatus.CANCELLED));
            if (regularRunExists) {
                errors.add(new PriorPayrollRowError(
                        row.lineNumber(), row.employeeNumber(), row.period(), "REAL_RUN_EXISTS"));
                continue;
            }

            // 7. Amount validation
            BigDecimal gross = parseRequiredAmount(row.grossEarnings());
            BigDecimal net = parseRequiredAmount(row.netPay());
            BigDecimal epf = parseOptionalAmount(row.epf());
            BigDecimal esi = parseOptionalAmount(row.esi());
            BigDecimal pt = parseOptionalAmount(row.pt());
            BigDecimal tds = parseOptionalAmount(row.tds());

            if (gross == null || net == null || epf == null || esi == null || pt == null || tds == null) {
                errors.add(new PriorPayrollRowError(
                        row.lineNumber(), row.employeeNumber(), row.period(), "INVALID_AMOUNT"));
                continue;
            }

            // 8. Net too high check: net_pay <= gross - (epf + esi + pt + tds)
            BigDecimal maxNet = gross.subtract(epf.add(esi).add(pt).add(tds));
            if (net.compareTo(maxNet) > 0) {
                errors.add(
                        new PriorPayrollRowError(row.lineNumber(), row.employeeNumber(), row.period(), "NET_TOO_HIGH"));
                continue;
            }

            // 9. Duplicate in file
            String key = empNum + ":" + period;
            if (!seenInFile.add(key)) {
                errors.add(new PriorPayrollRowError(
                        row.lineNumber(), row.employeeNumber(), row.period(), "DUPLICATE_IN_FILE"));
                continue;
            }

            validRows.add(new ValidatedPriorPayrollRow(row, employee, period, gross, epf, esi, pt, tds, net));
        }

        return new ValidationResult(validRows, errors);
    }

    private BigDecimal parseRequiredAmount(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        String trimmed = str.trim();
        if (!AMOUNT_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        try {
            BigDecimal val = new BigDecimal(trimmed).setScale(4);
            if (val.compareTo(BigDecimal.ZERO) < 0) {
                return null;
            }
            return val;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseOptionalAmount(String str) {
        if (str == null || str.trim().isEmpty()) {
            return BigDecimal.ZERO.setScale(4);
        }
        String trimmed = str.trim();
        if (!AMOUNT_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        try {
            BigDecimal val = new BigDecimal(trimmed).setScale(4);
            if (val.compareTo(BigDecimal.ZERO) < 0) {
                return null;
            }
            return val;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public LocalDate tenantToday(UUID tenantId) {
        return LocalDate.now(clock.withZone(tenantZone(tenantId)));
    }

    private ZoneId tenantZone(UUID tenantId) {
        if (jdbcTemplate == null) {
            return DEFAULT_TENANT_ZONE;
        }
        try {
            List<String> zones = jdbcTemplate.queryForList(
                    "SELECT timezone FROM core.tenant WHERE tenant_id = ?", String.class, tenantId);
            String zone = zones.isEmpty() ? null : zones.get(0);
            if (zone == null || zone.isBlank()) {
                return DEFAULT_TENANT_ZONE;
            }
            return ZoneId.of(zone.strip());
        } catch (Exception e) {
            return DEFAULT_TENANT_ZONE;
        }
    }
}
