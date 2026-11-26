package com.infinevo.payroll.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.payroll.ai.PayslipComparison.Line;
import com.infinevo.payroll.ai.PayslipComparison.Reason;
import com.infinevo.payroll.payslip.PayslipLineResponse;
import com.infinevo.payroll.payslip.PayslipResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-114.14: a reason only where a stored fact proves it; every difference subtracted as BigDecimal. */
class PayslipComparisonTest {

    private static PayslipLineResponse line(String code, String amount, String source) {
        return new PayslipLineResponse(code, code + " name", new BigDecimal(amount), source, true);
    }

    private static PayslipResponse slip(
            String period,
            LocalDate joined,
            String paidDays,
            String lopDays,
            String gross,
            String net,
            List<PayslipLineResponse> earnings,
            List<PayslipLineResponse> deductions) {
        return new PayslipResponse(
                new PayslipResponse.RunSummary(UUID.randomUUID(), "REGULAR", period, null, null, "PAID"),
                null,
                new PayslipResponse.EmployeeSummary(UUID.randomUUID(), "E1", "Asha", null, null, joined),
                new PayslipResponse.DaysSummary(
                        new BigDecimal("30"), new BigDecimal(paidDays), new BigDecimal(lopDays), BigDecimal.ZERO),
                earnings,
                deductions,
                List.of(),
                List.of(),
                new PayslipResponse.TotalsSummary(
                        new BigDecimal(gross), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(net)),
                null);
    }

    private static final LocalDate LONG_AGO = LocalDate.of(2020, 1, 6);

    private static Line find(PayslipComparison.Result r, String code) {
        return r.lines().stream().filter(l -> l.code().equals(code)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("loss of pay, a month-only entry, a new and an ended component, tax: each line its own reason")
    void sourceReasons() {
        PayslipResponse sep = slip(
                "2026-09",
                LONG_AGO,
                "30",
                "0",
                "50000",
                "45000",
                List.of(line("BASIC", "30000", "STRUCTURE"), line("SPECIAL", "20000", "STRUCTURE")),
                List.of(line("TDS", "2000", "TAX"), line("ADV", "1000", "PAY_INPUT")));
        PayslipResponse oct = slip(
                "2026-10",
                LONG_AGO,
                "28",
                "2",
                "52000",
                "44000",
                List.of(
                        line("BASIC", "30000", "STRUCTURE"),
                        line("LOP", "-2000", "LOP"),
                        line("OT", "4000", "PAY_INPUT"),
                        line("TRAVEL", "20000", "STRUCTURE")),
                List.of(line("TDS", "2500", "TAX")));

        PayslipComparison.Result r = PayslipComparison.compare(oct, sep, false);

        assertThat(r.comparedWith()).isEqualTo("2026-09");
        // Paid days went from 30 to 28: that shows on the loss-of-pay line, the one line days move.
        assertThat(find(r, "LOP").reasons()).containsExactly(Reason.LOSS_OF_PAY, Reason.PAID_DAYS_CHANGED);
        assertThat(find(r, "OT").reasons()).containsExactly(Reason.MONTH_ONLY_ENTRY);
        assertThat(find(r, "TRAVEL").reasons()).contains(Reason.NEW_COMPONENT);
        assertThat(find(r, "TDS").reasons()).containsExactly(Reason.TAX_SEE_TDS);
        assertThat(find(r, "BASIC").reasons()).isEmpty();
        assertThat(r.gone()).extracting(Line::code).containsExactlyInAnyOrder("SPECIAL", "ADV");
        assertThat(r.gone().stream()
                        .filter(l -> l.code().equals("SPECIAL"))
                        .findFirst()
                        .orElseThrow()
                        .reasons())
                .containsExactly(Reason.ENDED_COMPONENT);
        assertThat(r.gone().stream()
                        .filter(l -> l.code().equals("ADV"))
                        .findFirst()
                        .orElseThrow()
                        .reasons())
                .containsExactly(Reason.MONTH_ONLY_ENTRY);
    }

    @Test
    @DisplayName("a salary revision and changed paid days explain a structure line; nothing proven is NOT_KNOWN")
    void structureReasons() {
        PayslipResponse sep = slip(
                "2026-09",
                LONG_AGO,
                "30",
                "0",
                "43500",
                "40000",
                List.of(line("BASIC", "43500", "STRUCTURE")),
                List.of());
        PayslipResponse oct = slip(
                "2026-10",
                LONG_AGO,
                "30",
                "0",
                "45000",
                "41500",
                List.of(line("BASIC", "45000", "STRUCTURE")),
                List.of());

        assertThat(find(PayslipComparison.compare(oct, sep, true), "BASIC").reasons())
                .containsExactly(Reason.SALARY_REVISED);
        assertThat(find(PayslipComparison.compare(oct, sep, false), "BASIC").reasons())
                .containsExactly(Reason.NOT_KNOWN);

        PayslipResponse fewerDays = slip(
                "2026-10",
                LONG_AGO,
                "25",
                "0",
                "40000",
                "37000",
                List.of(line("BASIC", "40000", "STRUCTURE")),
                List.of());
        // A structure line is a full-month amount: fewer days never move it, so nothing stored proves why it moved.
        assertThat(find(PayslipComparison.compare(fewerDays, sep, false), "BASIC")
                        .reasons())
                .containsExactly(Reason.NOT_KNOWN);
    }

    @Test
    @DisplayName("joined this month is proven by the joining date inside the month, and shows on the loss-of-pay line")
    void joinedThisMonth() {
        PayslipResponse aug =
                slip("2026-08", LONG_AGO, "30", "0", "1", "1", List.of(line("BASIC", "1", "STRUCTURE")), List.of());
        PayslipResponse sep = slip(
                "2026-09",
                LocalDate.of(2026, 9, 15),
                "15",
                "15",
                "20000",
                "19000",
                List.of(line("BASIC", "40000", "STRUCTURE")),
                List.of(line("LOP", "20000", "LOP")));

        PayslipComparison.Result r = PayslipComparison.compare(sep, aug, false);

        assertThat(find(r, "LOP").reasons())
                .containsExactly(Reason.LOSS_OF_PAY, Reason.PAID_DAYS_CHANGED, Reason.JOINED_THIS_MONTH);
        assertThat(find(r, "BASIC").reasons()).containsExactly(Reason.NOT_KNOWN);

        PayslipResponse oct = slip(
                "2026-10",
                LocalDate.of(2026, 9, 15),
                "30",
                "0",
                "40000",
                "38000",
                List.of(line("BASIC", "40000", "STRUCTURE")),
                List.of());
        PayslipComparison.Result next = PayslipComparison.compare(oct, sep, false);
        assertThat(find(next, "BASIC").reasons()).isEmpty();
        assertThat(next.gone()).singleElement().satisfies(l -> assertThat(l.reasons())
                .containsExactly(Reason.LOSS_OF_PAY));
    }

    @Test
    @DisplayName(
            "a loss-of-pay refund is LOSS_OF_PAY_REFUNDED, never LOSS_OF_PAY; a new statutory line is not NEW_COMPONENT")
    void refundAndNewStatutory() {
        PayslipResponse sep = slip(
                "2026-09",
                LONG_AGO,
                "30",
                "0",
                "40000",
                "38000",
                List.of(line("BASIC", "40000", "STRUCTURE")),
                List.of());
        PayslipResponse oct = slip(
                "2026-10",
                LONG_AGO,
                "30",
                "0",
                "42000",
                "39000",
                List.of(line("BASIC", "40000", "STRUCTURE"), line("LOP_REVERSAL", "2000", "LOP")),
                List.of(line("PF", "1800", "STATUTORY")));

        PayslipComparison.Result r = PayslipComparison.compare(oct, sep, false);

        assertThat(find(r, "LOP_REVERSAL").reasons()).containsExactly(Reason.LOSS_OF_PAY_REFUNDED);
        assertThat(find(r, "PF").reasons()).containsExactly(Reason.FOLLOWS_EARNINGS);
        PayslipComparison.Result back = PayslipComparison.compare(sep, oct, false);
        assertThat(back.gone())
                .filteredOn(l -> l.code().equals("LOP_REVERSAL"))
                .singleElement()
                .satisfies(l -> assertThat(l.reasons()).containsExactly(Reason.LOSS_OF_PAY_REFUNDED));
    }

    @Test
    @DisplayName("an off-cycle payslip is never compared: no comparedWith, nothing gone, its entries MONTH_ONLY_ENTRY")
    void offCycleStandsAlone() {
        PayslipResponse sep = slip(
                "2026-09",
                LONG_AGO,
                "30",
                "0",
                "40000",
                "38000",
                List.of(line("BASIC", "40000", "STRUCTURE")),
                List.of());
        PayslipResponse regular = slip(
                "2026-10", LONG_AGO, "30", "0", "5000", "5000", List.of(line("BONUS", "5000", "PAY_INPUT")), List.of());
        PayslipResponse bonus = new PayslipResponse(
                new PayslipResponse.RunSummary(UUID.randomUUID(), "OFF_CYCLE", "2026-10", null, null, "PAID"),
                regular.employer(),
                regular.employee(),
                regular.days(),
                regular.earnings(),
                regular.deductions(),
                regular.reimbursements(),
                regular.benefits(),
                regular.totals(),
                regular.link());

        PayslipComparison.Result r = PayslipComparison.compare(bonus, sep, false);

        assertThat(r.offCycle()).isTrue();
        assertThat(r.comparedWith()).isNull();
        assertThat(r.gone()).isEmpty();
        assertThat(find(r, "BONUS").reasons()).containsExactly(Reason.MONTH_ONLY_ENTRY);
        assertThat(find(r, "BONUS").previousAmount()).isNull();
    }

    @Test
    @DisplayName("a statutory line that moved with earnings follows them; with unchanged gross it is NOT_KNOWN")
    void statutory() {
        PayslipResponse sep = slip(
                "2026-09",
                LONG_AGO,
                "30",
                "0",
                "40000",
                "38000",
                List.of(line("BASIC", "40000", "STRUCTURE")),
                List.of(line("PF", "1800", "STATUTORY")));
        PayslipResponse octMoreGross = slip(
                "2026-10",
                LONG_AGO,
                "30",
                "0",
                "45000",
                "42800",
                List.of(line("BASIC", "45000", "STRUCTURE")),
                List.of(line("PF", "1900", "STATUTORY")));
        PayslipResponse octSameGross = slip(
                "2026-10",
                LONG_AGO,
                "30",
                "0",
                "40000",
                "37900",
                List.of(line("BASIC", "40000", "STRUCTURE")),
                List.of(line("PF", "1900", "STATUTORY")));

        assertThat(find(PayslipComparison.compare(octMoreGross, sep, true), "PF")
                        .reasons())
                .containsExactly(Reason.FOLLOWS_EARNINGS);
        assertThat(find(PayslipComparison.compare(octSameGross, sep, false), "PF")
                        .reasons())
                .containsExactly(Reason.NOT_KNOWN);
    }

    @Test
    @DisplayName("the first payslip: FIRST_PAYSLIP on every line and no previous amounts")
    void firstPayslip() {
        PayslipResponse oct = slip(
                "2026-10",
                LONG_AGO,
                "30",
                "0",
                "45000",
                "42000",
                List.of(line("BASIC", "45000", "STRUCTURE")),
                List.of());

        PayslipComparison.Result r = PayslipComparison.compare(oct, null, false);

        assertThat(r.firstPayslip()).isTrue();
        assertThat(r.comparedWith()).isNull();
        assertThat(find(r, "BASIC").reasons()).containsExactly(Reason.FIRST_PAYSLIP);
        assertThat(find(r, "BASIC").previousAmount()).isNull();
    }

    @Test
    @DisplayName("45000.00 - 43500.00 is \"1500.00\" in the answer, with INR once and no id")
    void differenceAsText() {
        PayslipResponse sep = slip(
                "2026-09",
                LONG_AGO,
                "30",
                "0",
                "43500.00",
                "40000.00",
                List.of(line("BASIC", "43500.00", "STRUCTURE")),
                List.of());
        PayslipResponse oct = slip(
                "2026-10",
                LONG_AGO,
                "30",
                "0",
                "45000.00",
                "41500.00",
                List.of(line("BASIC", "45000.00", "STRUCTURE")),
                List.of());

        JsonNode out = PayrollAiToolResults.explainPayslip(PayslipComparison.compare(oct, sep, true), null);

        JsonNode basic = out.path("lines").get(0);
        assertThat(basic.path("difference").asText()).isEqualTo("1500.00");
        assertThat(basic.path("previousAmount").asText()).isEqualTo("43500.00");
        assertThat(basic.path("reasons").get(0).asText()).isEqualTo("SALARY_REVISED");
        assertThat(out.path("totals").path("difference").path("net").asText()).isEqualTo("1500.00");
        assertThat(out.path("currency").asText()).isEqualTo("INR");
        assertThat(out.toString()).doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-");
    }
}
