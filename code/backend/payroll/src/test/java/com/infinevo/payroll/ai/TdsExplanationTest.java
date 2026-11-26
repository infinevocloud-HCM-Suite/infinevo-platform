package com.infinevo.payroll.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.payroll.ai.TdsExplanation.Recalculation;
import com.infinevo.payroll.payslip.PayslipLineResponse;
import com.infinevo.payroll.payslip.PayslipResponse;
import com.infinevo.payroll.taxcalc.recalc.TaxTrigger;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-114.14: the TDS answer lists only the recalculations between the two payslips, and re-derives nothing. */
class TdsExplanationTest {

    private static final TdsExplanation.YearRecord YEAR = new TdsExplanation.YearRecord(
            "2026-2027",
            "NEW",
            "DECLARATION",
            new BigDecimal("1200000"),
            new BigDecimal("1125000"),
            new BigDecimal("60000"),
            "2026-04",
            new BigDecimal("30000"),
            new BigDecimal("30000"));

    private static final List<Recalculation> HISTORY = List.of(
            new Recalculation(LocalDate.of(2026, 9, 10), TaxTrigger.DECLARATION_SUBMITTED, new BigDecimal("50000")),
            new Recalculation(LocalDate.of(2026, 4, 2), TaxTrigger.SALARY_DEFAULT, new BigDecimal("45000")),
            new Recalculation(LocalDate.of(2026, 10, 3), TaxTrigger.SALARY_REVISION, new BigDecimal("60000")),
            new Recalculation(LocalDate.of(2026, 11, 2), TaxTrigger.OFFICER, new BigDecimal("61000")));

    @Test
    @DisplayName(
            "only recalculations after September's month end and up to October's are listed, before from the record before")
    void window() {
        TdsExplanation.Result r = TdsExplanation.explain(
                YearMonth.of(2026, 10),
                YearMonth.of(2026, 9),
                new BigDecimal("6000"),
                new BigDecimal("4500"),
                YEAR,
                HISTORY);

        assertThat(r.recalculations()).hasSize(1);
        TdsExplanation.Listed only = r.recalculations().get(0);
        assertThat(only.cause()).isEqualTo(TaxTrigger.SALARY_REVISION);
        assertThat(only.annualTaxBefore()).isEqualByComparingTo("50000");
        assertThat(only.annualTaxAfter()).isEqualByComparingTo("60000");
        assertThat(r.difference()).isEqualByComparingTo("1500");
        assertThat(r.noRecord()).isFalse();
    }

    @Test
    @DisplayName("no active record gives noRecord and the plain sentence; no TDS line gives 0.00")
    void noRecordNoLine() {
        PayslipResponse noTax = new PayslipResponse(
                null,
                null,
                null,
                null,
                List.of(),
                List.of(new PayslipLineResponse("PF", "PF", BigDecimal.TEN, "STATUTORY", false)),
                List.of(),
                List.of(),
                null,
                null);

        TdsExplanation.Result r = TdsExplanation.explain(
                YearMonth.of(2026, 10), null, TdsExplanation.tdsOf(noTax), null, null, List.of());
        JsonNode out = PayrollAiToolResults.explainTds(r);

        assertThat(out.path("noRecord").asBoolean()).isTrue();
        assertThat(out.path("message").asText()).isEqualTo("There is no tax record for this year yet.");
        assertThat(out.path("tdsThisMonth").asText()).isEqualTo("0.00");
        assertThat(out.has("tdsLastMonth")).isFalse();
        assertThat(out.path("rule").asText()).isEqualTo(TdsExplanation.RULE);
    }

    @Test
    @DisplayName("April uses the new financial year and March the old one")
    void financialYear() {
        assertThat(TdsExplanation.financialYearOf(YearMonth.of(2027, 4)).label())
                .isEqualTo("2027-2028");
        assertThat(TdsExplanation.financialYearOf(YearMonth.of(2027, 3)).label())
                .isEqualTo("2026-2027");
        TdsExplanation.Result april =
                TdsExplanation.explain(YearMonth.of(2026, 4), null, BigDecimal.ZERO, null, YEAR, HISTORY);
        // No earlier payslip: from the start of the year to April's end.
        assertThat(april.recalculations())
                .extracting(TdsExplanation.Listed::cause)
                .containsExactly(TaxTrigger.SALARY_DEFAULT);
        assertThat(april.financialYear()).isEqualTo("2026-2027");
    }

    @Test
    @DisplayName("the answer: amounts as text, year record, causes by name, INR once, no id")
    void answerShape() {
        JsonNode out = PayrollAiToolResults.explainTds(TdsExplanation.explain(
                YearMonth.of(2026, 10),
                YearMonth.of(2026, 9),
                new BigDecimal("6000"),
                new BigDecimal("4500"),
                YEAR,
                HISTORY));

        assertThat(out.path("tdsThisMonth").asText()).isEqualTo("6000.00");
        assertThat(out.path("tdsLastMonth").asText()).isEqualTo("4500.00");
        assertThat(out.path("difference").asText()).isEqualTo("1500.00");
        assertThat(out.path("yearRecord").path("annualTax").asText()).isEqualTo("60000.00");
        assertThat(out.path("yearRecord").path("remaining").asText()).isEqualTo("30000.00");
        assertThat(out.path("recalculations").get(0).path("cause").asText()).isEqualTo("SALARY_REVISION");
        assertThat(out.path("currency").asText()).isEqualTo("INR");
    }
}
