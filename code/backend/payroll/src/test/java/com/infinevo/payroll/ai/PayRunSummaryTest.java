package com.infinevo.payroll.ai;

import static com.infinevo.payroll.ai.PayRunFixtures.row;
import static com.infinevo.payroll.ai.PayRunFixtures.run;
import static com.infinevo.payroll.ai.PayRunFixtures.skipped;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.infinevo.payroll.payrun.EmployeePayRunResponse;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.SkipReason;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-114.21 spec, Tests: {@link PayRunSummary} - totals, the six lists, the thresholds and the caps. */
class PayRunSummaryTest {

    private final UUID asha = UUID.randomUUID();
    private final UUID ravi = UUID.randomUUID();
    private final UUID omar = UUID.randomUUID();
    private final UUID dina = UUID.randomUUID();

    @Test
    @DisplayName("totals and employer contributions (the sum of included rows' benefits)")
    void totals() {
        PayRunResponse nov = run("2026-11", PayRunStatus.COMPUTED, "100000", "20000", "80000");
        PayRunResponse oct = run("2026-10", PayRunStatus.PAID, "90000", "18000", "72000");
        when(nov.includedCount()).thenReturn(2);
        when(nov.skippedCount()).thenReturn(1);

        PayRunSummary.Result r = PayRunSummary.of(
                nov,
                List.of(
                        row(asha, "Asha", "E-1", "40000", "1800.50"),
                        row(ravi, "Ravi", "E-2", "40000", "1800.00"),
                        skipped(dina, "Dina", "E-4", SkipReason.NO_BANK_DETAILS)),
                oct,
                List.of(row(asha, "Asha", "E-1", "36000", "1700")));

        assertThat(r.thisRun().gross()).isEqualByComparingTo("100000");
        assertThat(r.thisRun().employerContributions()).isEqualByComparingTo("3600.50");
        assertThat(r.thisRun().included()).isEqualTo(2);
        assertThat(r.lastRun().netPay()).isEqualByComparingTo("72000");
        assertThat(r.lastRun().employerContributions()).isEqualByComparingTo("1700");
        assertThat(r.skipped().items()).extracting(PayRunSummary.Item::note).containsExactly("NO_BANK_DETAILS");
    }

    @Test
    @DisplayName("in this run only, and in last run only, land in the right list")
    void joinersAndLeavers() {
        PayRunSummary.Result r = PayRunSummary.of(
                run("2026-11", PayRunStatus.COMPUTED, "0", "0", "0"),
                List.of(row(asha, "Asha", "E-1", "40000", null), row(omar, "Omar", "E-3", "30000", null)),
                run("2026-10", PayRunStatus.PAID, "0", "0", "0"),
                List.of(row(asha, "Asha", "E-1", "40000", null), row(ravi, "Ravi", "E-2", "35000", null)));

        assertThat(r.inThisRunNotLast().items())
                .extracting(PayRunSummary.Item::name)
                .containsExactly("Omar");
        assertThat(r.inLastRunNotThis().items())
                .extracting(PayRunSummary.Item::name)
                .containsExactly("Ravi");
        assertThat(r.inLastRunNotThis().items().get(0).lastNet()).isEqualByComparingTo("35000");
    }

    @Test
    @DisplayName("no previous run: nobody is a newcomer and there is nothing to compare")
    void noPreviousRun() {
        PayRunSummary.Result r = PayRunSummary.of(
                run("2026-11", PayRunStatus.COMPUTED, "0", "0", "0"),
                List.of(row(asha, "Asha", "E-1", "40000", null)),
                null,
                null);

        assertThat(r.lastRun()).isNull();
        assertThat(r.inThisRunNotLast().total()).isZero();
        assertThat(r.largeChange().total()).isZero();
    }

    @Test
    @DisplayName("net 0.00 and -250.00 are listed; a positive net is not")
    void zeroOrNegative() {
        PayRunSummary.Result r = PayRunSummary.of(
                run("2026-11", PayRunStatus.COMPUTED, "0", "0", "0"),
                List.of(
                        row(asha, "Asha", "E-1", "0.00", null),
                        row(ravi, "Ravi", "E-2", "-250.00", null),
                        row(omar, "Omar", "E-3", "100.00", null)),
                null,
                null);

        assertThat(r.zeroOrNegativeNet().items())
                .extracting(PayRunSummary.Item::name)
                .containsExactlyInAnyOrder("Asha", "Ravi");
    }

    @Test
    @DisplayName("25% and 5,000 is a large change; 25% and 400 is not; 5% and 5,000 is not")
    void largeChangeThresholds() {
        assertThat(PayRunSummary.isLargeChange(new BigDecimal("20000"), new BigDecimal("25000")))
                .isTrue();
        assertThat(PayRunSummary.isLargeChange(new BigDecimal("1600"), new BigDecimal("2000")))
                .isFalse();
        assertThat(PayRunSummary.isLargeChange(new BigDecimal("100000"), new BigDecimal("105000")))
                .isFalse();
        assertThat(PayRunSummary.isLargeChange(new BigDecimal("20000"), new BigDecimal("15000")))
                .isTrue();

        PayRunSummary.Result r = PayRunSummary.of(
                run("2026-11", PayRunStatus.COMPUTED, "0", "0", "0"),
                List.of(row(asha, "Asha", "E-1", "25000", null), row(ravi, "Ravi", "E-2", "70000", null)),
                run("2026-10", PayRunStatus.PAID, "0", "0", "0"),
                List.of(row(asha, "Asha", "E-1", "20000", null), row(ravi, "Ravi", "E-2", "40000", null)));
        // Largest move first.
        assertThat(r.largeChange().items()).extracting(PayRunSummary.Item::name).containsExactly("Ravi", "Asha");
    }

    @Test
    @DisplayName("a list of 37 returns 20 with total 37")
    void capped() {
        List<EmployeePayRunResponse> rows = new ArrayList<>();
        for (int i = 0; i < 37; i++) {
            rows.add(row(UUID.randomUUID(), "P" + i, "E-" + i, "0.00", null));
        }
        PayRunSummary.Result r =
                PayRunSummary.of(run("2026-11", PayRunStatus.COMPUTED, "0", "0", "0"), rows, null, null);

        assertThat(r.zeroOrNegativeNet().items()).hasSize(20);
        assertThat(r.zeroOrNegativeNet().total()).isEqualTo(37);
    }

    @Test
    @DisplayName("needs attention: unpriced inputs counted; a computation error named, its text never carried")
    void needsAttention_noErrorText() {
        EmployeePayRunResponse unpriced = row(asha, "Asha", "E-1", "100", null);
        when(unpriced.unpricedInputCount()).thenReturn(3);
        EmployeePayRunResponse failed = row(ravi, "Ravi", "E-2", "100", null);
        when(failed.computationError()).thenReturn("Salary component BASIC for PAN ABCDE1234F missing");

        PayRunSummary.Result r = PayRunSummary.of(
                run("2026-11", PayRunStatus.COMPUTED, "0", "0", "0"), List.of(unpriced, failed), null, null);

        assertThat(r.needsAttention().items())
                .extracting(PayRunSummary.Item::note)
                .containsExactly("unpriced inputs: 3", "computation error");
        assertThat(r.toString()).doesNotContain("ABCDE1234F").doesNotContain("BASIC");
    }
}
