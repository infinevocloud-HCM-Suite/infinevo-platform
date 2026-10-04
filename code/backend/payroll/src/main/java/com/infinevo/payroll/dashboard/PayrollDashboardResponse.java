package com.infinevo.payroll.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.payrun.PayRunStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The whole payroll dashboard for the bound tenant in one response (W-37 §4). Every amount is a
 * {@link BigDecimal} at scale 2 — never a {@code Double} (legacy {@code DashboardServiceImpl.java:262},
 * BUG-011). A tenant with no runs gets zeros and empty lists; {@code current_run} and
 * {@code employees.as_at_run} are the only nulls.
 */
public record PayrollDashboardResponse(
        @JsonProperty("financial_year") Year financialYear,
        @JsonProperty("employees") Employees employees,
        @JsonProperty("current_run") RunCard currentRun,
        @JsonProperty("recent_runs") List<RunCard> recentRuns,
        @JsonProperty("months") List<MonthRow> months,
        @JsonProperty("year_totals") YearTotals yearTotals,
        @JsonProperty("statutory") Statutory statutory) {

    /** The financial year the months, totals and statutory tiles cover: 1 April to 31 March. */
    public record Year(@JsonProperty("start") LocalDate start, @JsonProperty("end") LocalDate end) {}

    /**
     * Readiness: who is employed today, and what the newest non-cancelled run included and skipped.
     * {@code as_at_run} is null when the tenant has no run.
     */
    public record Employees(
            @JsonProperty("active_today") int activeToday, @JsonProperty("as_at_run") AsAtRun asAtRun) {}

    /** The newest non-cancelled run's counts, with the skipped employees grouped by reason (W-29.1). */
    public record AsAtRun(
            @JsonProperty("payrun_id") UUID payrunId,
            @JsonProperty("period") String period,
            @JsonProperty("included") int included,
            @JsonProperty("skipped") int skipped,
            @JsonProperty("skipped_by_reason") Map<String, Long> skippedByReason) {}

    /** One run as a card. The progress fields are null unless the run is {@code COMPUTING}. */
    public record RunCard(
            @JsonProperty("payrun_id") UUID payrunId,
            @JsonProperty("period") String period,
            @JsonProperty("status") PayRunStatus status,
            @JsonProperty("pay_date") LocalDate payDate,
            @JsonProperty("paid_on") LocalDate paidOn,
            @JsonProperty("included") int included,
            @JsonProperty("skipped") int skipped,
            @JsonProperty("progress_done") Integer progressDone,
            @JsonProperty("progress_total") Integer progressTotal,
            @JsonProperty("gross") BigDecimal gross,
            @JsonProperty("deductions") BigDecimal deductions,
            @JsonProperty("net_pay") BigDecimal netPay) {}

    /** One computed, approved or paid run in the year, with the tax its lines deducted. */
    public record MonthRow(
            @JsonProperty("payrun_id") UUID payrunId,
            @JsonProperty("period") String period,
            @JsonProperty("status") PayRunStatus status,
            @JsonProperty("gross") BigDecimal gross,
            @JsonProperty("deductions") BigDecimal deductions,
            @JsonProperty("tax") BigDecimal tax,
            @JsonProperty("net_pay") BigDecimal netPay) {}

    /** The year's figures over {@code PAID} runs only (W-37 §13 decision 3). */
    public record YearTotals(
            @JsonProperty("gross") BigDecimal gross,
            @JsonProperty("deductions") BigDecimal deductions,
            @JsonProperty("tax") BigDecimal tax,
            @JsonProperty("net_pay") BigDecimal netPay,
            @JsonProperty("paid_runs") int paidRuns) {}

    /** The four statutory tiles for the year, from the lines of {@code PAID} runs. */
    public record Statutory(
            @JsonProperty("epf") StatutoryTile epf,
            @JsonProperty("esi") StatutoryTile esi,
            @JsonProperty("professional_tax") StatutoryTile professionalTax,
            @JsonProperty("tds") StatutoryTile tds) {}

    /**
     * One tile: the employee's share ({@code DEDUCTION} lines) and the employer's ({@code BENEFIT}
     * lines). {@code employer} is omitted for professional tax and TDS, which have no employer share.
     */
    public record StatutoryTile(
            @JsonProperty("employee") BigDecimal employee,
            @JsonProperty("employer") @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal employer) {}
}
