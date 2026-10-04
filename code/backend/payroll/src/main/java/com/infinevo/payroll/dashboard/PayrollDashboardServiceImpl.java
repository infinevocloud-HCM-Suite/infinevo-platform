package com.infinevo.payroll.dashboard;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.dashboard.DashboardQueryRepository.LineSum;
import com.infinevo.payroll.dashboard.DashboardQueryRepository.RunRow;
import com.infinevo.payroll.dashboard.PayrollDashboardResponse.AsAtRun;
import com.infinevo.payroll.dashboard.PayrollDashboardResponse.Employees;
import com.infinevo.payroll.dashboard.PayrollDashboardResponse.MonthRow;
import com.infinevo.payroll.dashboard.PayrollDashboardResponse.RunCard;
import com.infinevo.payroll.dashboard.PayrollDashboardResponse.Statutory;
import com.infinevo.payroll.dashboard.PayrollDashboardResponse.StatutoryTile;
import com.infinevo.payroll.dashboard.PayrollDashboardResponse.Year;
import com.infinevo.payroll.dashboard.PayrollDashboardResponse.YearTotals;
import com.infinevo.payroll.payrun.LineKind;
import com.infinevo.payroll.payrun.LineSource;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.SkipReason;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationRules;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * W-37 §3. Six tenant-bound reads, no write and no cache (§13 decision 5). Ports the April rule
 * ({@code DashboardServiceImpl.java:174-177}) and the summary's intent ({@code :75-208}); counts only
 * {@code PAID} runs towards the year (§13 decision 3 — legacy summed every row whatever its status,
 * {@code :178}) and reads statutory figures from the run's lines, employer share included (§13
 * decision 4 — legacy summed declared earnings and set the employer share to zero, {@code :225-243}).
 */
@Service
@Transactional(readOnly = true)
public class PayrollDashboardServiceImpl implements PayrollDashboardService {

    static final int RECENT_RUNS = 6;

    /** Statuses a month row shows (§13 decision 3). */
    static final Set<PayRunStatus> MONTH_STATUSES =
            Set.of(PayRunStatus.COMPUTED, PayRunStatus.APPROVED, PayRunStatus.PAID);

    /** W-31.3's provident fund codes — employee and employer shares (W-37 §4). */
    static final Set<String> EPF_CODES = Set.of("EPF_EMPLOYEE", "EPF_EMPLOYER", "EPS_EMPLOYER", "EDLI", "EPF_ADMIN");

    static final Set<String> ESI_CODES = Set.of("ESI_EMPLOYEE", "ESI_EMPLOYER");

    static final String PT_CODE = "PROFESSIONAL_TAX";

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final DashboardQueryRepository queries;
    private final EmployeeService employeeService;
    private final Clock clock;

    @Autowired
    public PayrollDashboardServiceImpl(DashboardQueryRepository queries, EmployeeService employeeService) {
        this(queries, employeeService, TaxDeclarationRules.defaultClock());
    }

    PayrollDashboardServiceImpl(DashboardQueryRepository queries, EmployeeService employeeService, Clock clock) {
        this.queries = Objects.requireNonNull(queries, "queries must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public PayrollDashboardResponse summary(Integer fy) {
        UUID tenantId = TenantContext.require();
        LocalDate today = TaxDeclarationRules.today(clock);
        FinancialYear year = resolveYear(fy, today);
        List<YearMonth> months = year.months();
        String from = months.get(0).toString();
        String to = months.get(months.size() - 1).toString();

        List<RunRow> recent = queries.recentRuns(tenantId, RECENT_RUNS);
        RunRow latest = recent.isEmpty() ? null : recent.get(0);
        AsAtRun asAtRun = latest == null
                ? null
                : new AsAtRun(
                        latest.id(),
                        latest.period(),
                        latest.included(),
                        latest.skipped(),
                        skippedByReason(queries.skippedByReason(tenantId, latest.id())));
        int activeToday = employeeService.listEmployedBetween(today, today).size();

        List<RunRow> yearRuns = queries.runsInPeriods(tenantId, from, to, MONTH_STATUSES);
        Map<UUID, BigDecimal> tax =
                queries.taxByRun(tenantId, yearRuns.stream().map(RunRow::id).toList());
        List<LineSum> lineSums = queries.paidLineSums(tenantId, from, to, Set.of(LineSource.STATUTORY, LineSource.TAX));

        List<RunCard> cards =
                recent.stream().map(PayrollDashboardServiceImpl::card).toList();
        return new PayrollDashboardResponse(
                new Year(year.start(), year.end()),
                new Employees(activeToday, asAtRun),
                cards.isEmpty() ? null : cards.get(0),
                cards,
                monthRows(yearRuns, tax),
                yearTotals(yearRuns, tax),
                statutory(lineSums));
    }

    /** {@code fy} or, when null, the year containing {@code today}; outside 2000–2100 is refused. */
    static FinancialYear resolveYear(Integer fy, LocalDate today) {
        if (fy == null) {
            return FinancialYear.containing(today);
        }
        if (fy < MIN_YEAR || fy > MAX_YEAR) {
            throw new IllegalArgumentException("fy must be a year between " + MIN_YEAR + " and " + MAX_YEAR
                    + " (the year the financial year starts), was " + fy);
        }
        return FinancialYear.of(fy);
    }

    /** Every reason the run can record, zero when none, so the screen never meets a missing key. */
    static Map<String, Long> skippedByReason(Map<SkipReason, Long> counts) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (SkipReason reason : SkipReason.values()) {
            result.put(reason.name(), counts.getOrDefault(reason, 0L));
        }
        return result;
    }

    static RunCard card(RunRow run) {
        boolean computing = run.status() == PayRunStatus.COMPUTING;
        return new RunCard(
                run.id(),
                run.period(),
                run.status(),
                run.payDate(),
                run.paidOn(),
                run.included(),
                run.skipped(),
                computing ? run.progressDone() : null,
                computing ? run.progressTotal() : null,
                money(run.gross()),
                money(run.deductions()),
                money(run.netPay()));
    }

    static List<MonthRow> monthRows(List<RunRow> runs, Map<UUID, BigDecimal> tax) {
        List<MonthRow> rows = new ArrayList<>(runs.size());
        for (RunRow run : runs) {
            rows.add(new MonthRow(
                    run.id(),
                    run.period(),
                    run.status(),
                    money(run.gross()),
                    money(run.deductions()),
                    money(tax.get(run.id())),
                    money(run.netPay())));
        }
        return rows;
    }

    /** Sums the {@code PAID} runs at stored precision and rounds once, here at the boundary. */
    static YearTotals yearTotals(List<RunRow> runs, Map<UUID, BigDecimal> tax) {
        BigDecimal gross = ZERO;
        BigDecimal deductions = ZERO;
        BigDecimal taxTotal = ZERO;
        BigDecimal netPay = ZERO;
        int paid = 0;
        for (RunRow run : runs) {
            if (run.status() != PayRunStatus.PAID) {
                continue;
            }
            paid++;
            gross = gross.add(orZero(run.gross()));
            deductions = deductions.add(orZero(run.deductions()));
            taxTotal = taxTotal.add(orZero(tax.get(run.id())));
            netPay = netPay.add(orZero(run.netPay()));
        }
        return new YearTotals(money(gross), money(deductions), money(taxTotal), money(netPay), paid);
    }

    /**
     * Groups the year's paid line sums into the four tiles: {@code DEDUCTION} is the employee's
     * share, {@code BENEFIT} the employer's. EPF, ESI and professional tax read {@code STATUTORY}
     * lines by code; TDS is every {@code TAX} line. Any other code is ignored, not an error.
     */
    static Statutory statutory(List<LineSum> sums) {
        BigDecimal epfEmployee = ZERO;
        BigDecimal epfEmployer = ZERO;
        BigDecimal esiEmployee = ZERO;
        BigDecimal esiEmployer = ZERO;
        BigDecimal pt = ZERO;
        BigDecimal tds = ZERO;
        for (LineSum sum : sums) {
            BigDecimal amount = orZero(sum.amount());
            if (sum.source() == LineSource.TAX) {
                tds = tds.add(amount);
                continue;
            }
            if (sum.source() != LineSource.STATUTORY || sum.componentCode() == null) {
                continue;
            }
            String code = sum.componentCode();
            boolean employee = sum.lineKind() == LineKind.DEDUCTION;
            boolean employer = sum.lineKind() == LineKind.BENEFIT;
            if (EPF_CODES.contains(code)) {
                if (employee) {
                    epfEmployee = epfEmployee.add(amount);
                } else if (employer) {
                    epfEmployer = epfEmployer.add(amount);
                }
            } else if (ESI_CODES.contains(code)) {
                if (employee) {
                    esiEmployee = esiEmployee.add(amount);
                } else if (employer) {
                    esiEmployer = esiEmployer.add(amount);
                }
            } else if (PT_CODE.equals(code) && employee) {
                pt = pt.add(amount);
            }
        }
        return new Statutory(
                new StatutoryTile(money(epfEmployee), money(epfEmployer)),
                new StatutoryTile(money(esiEmployee), money(esiEmployer)),
                new StatutoryTile(money(pt), null),
                new StatutoryTile(money(tds), null));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    /** Scale 2, half up — the one rounding, at the response boundary (CONVENTIONS.md §2). */
    static BigDecimal money(BigDecimal value) {
        return orZero(value).setScale(2, RoundingMode.HALF_UP);
    }
}
