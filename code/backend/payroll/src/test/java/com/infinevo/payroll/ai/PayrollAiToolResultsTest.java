package com.infinevo.payroll.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.ai.AiMaskingFilter;
import com.infinevo.payroll.payslip.PayslipLineResponse;
import com.infinevo.payroll.payslip.PayslipResponse;
import com.infinevo.payroll.payslip.PayslipSummaryResponse;
import com.infinevo.payroll.taxdeclaration.DeclarationStatus;
import com.infinevo.payroll.taxdeclaration.summary.dto.ComputedTaxSummaryResponse;
import com.infinevo.payroll.taxdeclaration.summary.dto.DeclaredTotals;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-114.7 spec, Tests: {@link PayrollAiToolResults} - amounts are two-decimal plain text with the payslip's rounding,
 * {@code "currency": "INR"} once, no UUID in any result, and only the fixed list of payslip fields. And rule 6's second
 * guard: core's {@link AiMaskingFilter}, which the chat loop runs on every tool result, hides a PAN, bank account, IFSC
 * or UAN that reaches the result while leaving the amounts alone.
 */
class PayrollAiToolResultsTest {

    private static final Pattern ANY_UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final UUID runId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @Test
    @DisplayName("45000 is \"45000.00\"; 1234567.5 is \"1234567.50\", never 1.2345675E6")
    void amounts_twoDecimalPlainText() {
        assertThat(PayrollAiToolResults.amount(new BigDecimal("45000"))).isEqualTo("45000.00");
        assertThat(PayrollAiToolResults.amount(new BigDecimal("1234567.5"))).isEqualTo("1234567.50");
        assertThat(PayrollAiToolResults.amount(new BigDecimal("1.2345675E6"))).isEqualTo("1234567.50");
        assertThat(PayrollAiToolResults.amount(new BigDecimal("4.5E4"))).isEqualTo("45000.00");
        assertThat(PayrollAiToolResults.amount(null)).isNull();
    }

    @Test
    @DisplayName("0.005 rounds as the payslip does: HALF_UP to 0.01 (PayslipServiceImpl.scale2)")
    void halfAmount_roundsAsPayslip() {
        BigDecimal half = new BigDecimal("0.005");
        assertThat(PayrollAiToolResults.amount(half)).isEqualTo("0.01");
        assertThat(PayrollAiToolResults.amount(half))
                .isEqualTo(half.setScale(2, RoundingMode.HALF_UP).toPlainString());
        assertThat(PayrollAiToolResults.amount(new BigDecimal("0.004"))).isEqualTo("0.00");
        assertThat(PayrollAiToolResults.amount(new BigDecimal("-0.005"))).isEqualTo("-0.01");
    }

    @Test
    @DisplayName("a payslip: the fixed fields, amounts as text, currency once, no id, no employee block")
    void payslip_fixedFields() {
        JsonNode out = PayrollAiToolResults.payslip(payslip("Basic"));

        assertThat(out.path("currency").asText()).isEqualTo("INR");
        assertThat(count(out.toString(), "\"currency\"")).isEqualTo(1);
        assertThat(out.path("period").asText()).isEqualTo("2026-10");
        assertThat(out.path("paidOn").asText()).isEqualTo("2026-10-31");
        assertThat(out.path("paidDays").asText()).isEqualTo("30.00");
        JsonNode basic = out.path("earnings").get(0);
        assertThat(basic.path("code").asText()).isEqualTo("BASIC");
        assertThat(basic.path("name").asText()).isEqualTo("Basic");
        assertThat(basic.path("amount").isTextual()).isTrue();
        assertThat(basic.path("amount").asText()).isEqualTo("1234567.50");
        assertThat(basic.path("taxable").asBoolean()).isTrue();
        assertThat(out.path("deductions").get(0).path("amount").asText()).isEqualTo("200.00");
        assertThat(out.path("reimbursements").get(0).path("amount").asText()).isEqualTo("0.01");
        assertThat(out.path("grossEarnings").asText()).isEqualTo("1234567.50");
        assertThat(out.path("totalDeductions").asText()).isEqualTo("200.00");
        assertThat(out.path("netPay").asText()).isEqualTo("1234367.51");
        // The employee block (number, PF and UAN numbers), the employer, benefits and the link stay out.
        assertThat(out.has("employee")).isFalse();
        assertThat(out.toString())
                .doesNotContain("UAN100200300400")
                .doesNotContain("MH/BAN/1234567")
                .doesNotContain("EMP-01")
                .doesNotContain("Acme")
                .doesNotContain("E6")
                .doesNotContain("E+");
        assertNoUuid(out);
    }

    @Test
    @DisplayName("the payslip list and tax summary: amounts as text, currency once, no id")
    void listAndSummary_noIds() {
        JsonNode list = PayrollAiToolResults.payslips(List.of(
                new PayslipSummaryResponse(runId, "2026-10", LocalDate.of(2026, 10, 31), new BigDecimal("45000"))));
        assertThat(list.path("payslips").get(0).path("netPay").asText()).isEqualTo("45000.00");
        assertThat(count(list.toString(), "\"currency\"")).isEqualTo(1);
        assertNoUuid(list);

        JsonNode summary = PayrollAiToolResults.taxSummary(new TaxSummaryResponse(
                "2026-2027",
                "OLD",
                DeclarationStatus.SUBMITTED,
                new DeclaredTotals(
                        new BigDecimal("240000"),
                        null,
                        null,
                        null,
                        Map.of("80C", new BigDecimal("150000")),
                        new BigDecimal("150000"),
                        null,
                        null,
                        null,
                        BigDecimal.ZERO),
                new ComputedTaxSummaryResponse(
                        new BigDecimal("1200000.5"),
                        null,
                        new BigDecimal("117000"),
                        new BigDecimal("58500"),
                        new BigDecimal("58500"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        5,
                        Instant.parse("2026-11-01T00:00:00Z"))));
        assertThat(summary.path("declared").path("houseRentAnnual").asText()).isEqualTo("240000.00");
        assertThat(summary.path("declared").path("section6aByGroup").path("80C").asText())
                .isEqualTo("150000.00");
        assertThat(summary.path("computed").path("taxableIncome").asText()).isEqualTo("1200000.50");
        assertThat(summary.path("computed").path("remainingMonths").asInt()).isEqualTo(5);
        assertThat(count(summary.toString(), "\"currency\"")).isEqualTo(1);
        assertNoUuid(summary);
    }

    @Test
    @DisplayName("rule 6: core's masker hides a PAN, bank account, IFSC or UAN in a payslip result; amounts pass")
    void masker_hidesIdentifiers_keepsAmounts() {
        AiMaskingFilter masker = new AiMaskingFilter();
        // A line name is free text an officer types; it is the one place on the result such a number could arrive.
        JsonNode out = PayrollAiToolResults.payslip(
                payslip("Arrears ABCDE1234F to a/c 123456789012 at HDFC0001234, UAN 100200300400"));

        JsonNode masked = masker.mask(out);

        String name = masked.path("earnings").get(0).path("name").asText();
        assertThat(name)
                .contains("[PAN]")
                .contains("[IFSC]")
                .doesNotContain("ABCDE1234F")
                .doesNotContain("123456789012")
                .doesNotContain("HDFC0001234")
                .doesNotContain("100200300400");
        // The masker changes nothing else: amounts, dates and codes reach the model as the mapper wrote them.
        assertThat(masked.path("netPay").asText()).isEqualTo("1234367.51");
        assertThat(masked.path("earnings").get(0).path("amount").asText()).isEqualTo("1234567.50");
        assertThat(masked.path("paidOn").asText()).isEqualTo("2026-10-31");
        assertThat(masked.path("period").asText()).isEqualTo("2026-10");
        assertThat(masked.path("earnings").get(0).path("code").asText()).isEqualTo("BASIC");

        // And by field name, had the mapper let one of the service's identifier fields through.
        JsonNode withIdentifiers = ((com.fasterxml.jackson.databind.node.ObjectNode) out.deepCopy())
                .put("pan", "ABCDE1234F")
                .put("uan_number", "100200300400")
                .put("bankAccountNumber", "123456789012")
                .put("ifsc", "HDFC0001234");
        JsonNode maskedFields = masker.mask(withIdentifiers);
        for (String field : List.of("pan", "uan_number", "bankAccountNumber", "ifsc")) {
            assertThat(maskedFields.path(field).asText()).as(field).isEqualTo("[hidden]");
        }
    }

    // ---- helpers ----

    private PayslipResponse payslip(String earningName) {
        return new PayslipResponse(
                new PayslipResponse.RunSummary(
                        runId, "REGULAR", "2026-10", LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31), "PAID"),
                new PayslipResponse.EmployerSummary("Acme"),
                new PayslipResponse.EmployeeSummary(
                        employeeId,
                        "EMP-01",
                        "Asha Rao",
                        "Engineer",
                        "Platform",
                        LocalDate.of(2024, 1, 1),
                        "ACTIVE",
                        "MH/BAN/1234567",
                        "UAN100200300400"),
                new PayslipResponse.DaysSummary(
                        new BigDecimal("30.00"), new BigDecimal("30"), BigDecimal.ZERO, BigDecimal.ZERO),
                List.of(new PayslipLineResponse("BASIC", earningName, new BigDecimal("1234567.5"), "STRUCTURE", true)),
                List.of(new PayslipLineResponse("PT", "Professional Tax", new BigDecimal("200"), "STATUTORY", false)),
                List.of(new PayslipLineResponse("FUEL", "Fuel", new BigDecimal("0.005"), "CLAIM", false)),
                List.of(new PayslipLineResponse("EPF_ER", "Employer PF", new BigDecimal("1800"), "STATUTORY", false)),
                new PayslipResponse.TotalsSummary(
                        new BigDecimal("1234567.5"),
                        new BigDecimal("200"),
                        new BigDecimal("0.005"),
                        new BigDecimal("1800"),
                        new BigDecimal("1234367.505")),
                new PayslipResponse.LinkSummary(Instant.parse("2026-11-30T00:00:00Z")));
    }

    private static int count(String text, String part) {
        Matcher m = Pattern.compile(Pattern.quote(part)).matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static void assertNoUuid(JsonNode data) {
        assertThat(ANY_UUID.matcher(data.toString()).find()).as(data.toString()).isFalse();
    }
}
