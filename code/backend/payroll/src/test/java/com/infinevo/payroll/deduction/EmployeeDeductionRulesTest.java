package com.infinevo.payroll.deduction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-35.2 §7 — the per-line rules and the batch size, with no Spring context. */
class EmployeeDeductionRulesTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    @Test
    @DisplayName("A good line parses to its typed fields; reason and remarks are trimmed, blank remarks are null")
    void goodLineParses() {
        UUID employee = UUID.randomUUID();
        UUID document = UUID.randomUUID();
        List<EmployeeDeductionRules.ParsedLine> parsed = EmployeeDeductionRules.parseBatch(
                List.of(new EmployeeDeductionLineRequest(
                        employee,
                        "2026-10",
                        "ADVANCE_RECOVERY",
                        new BigDecimal("1500.50"),
                        " Advance ",
                        " ",
                        document)),
                OCTOBER);

        assertThat(parsed).singleElement().satisfies(line -> {
            assertThat(line.employeeId()).isEqualTo(employee);
            assertThat(line.period()).isEqualTo(OCTOBER);
            assertThat(line.deductionType()).isEqualTo(DeductionType.ADVANCE_RECOVERY);
            assertThat(line.amount()).isEqualByComparingTo("1500.50");
            assertThat(line.reason()).isEqualTo("Advance");
            assertThat(line.remarks()).isNull();
            assertThat(line.documentId()).isEqualTo(document);
        });
    }

    @Test
    @DisplayName("Zero, negative and three-decimal amounts are refused; 100.00 and 0.01 are fine")
    void amounts() {
        refused(line("2026-10", "OTHER", "0", "r"), "amount");
        refused(line("2026-10", "OTHER", "-1", "r"), "amount");
        refused(line("2026-10", "OTHER", "10.005", "r"), "decimal");
        refused(line("2026-10", "OTHER", null, "r"), "amount");
        assertThat(parse(line("2026-10", "OTHER", "100.00", "r")).amount()).isEqualByComparingTo("100");
        assertThat(parse(line("2026-10", "OTHER", "0.01", "r")).amount()).isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("A bad period string is refused; next month is fine, two months ahead is refused; the past is fine")
    void periods() {
        refused(line("2026-13", "OTHER", "10", "r"), "YYYY-MM");
        refused(line("October", "OTHER", "10", "r"), "YYYY-MM");
        refused(line(null, "OTHER", "10", "r"), "period");
        assertThat(parse(line("2026-11", "OTHER", "10", "r")).period()).isEqualTo(YearMonth.of(2026, 11));
        refused(line("2026-12", "OTHER", "10", "r"), "more than one month");
        assertThat(parse(line("2025-04", "OTHER", "10", "r")).period()).isEqualTo(YearMonth.of(2025, 4));
    }

    @Test
    @DisplayName("An unknown or missing deduction type is refused")
    void types() {
        refused(line("2026-10", "BONUS_CLAWBACK", "10", "r"), "deduction_type");
        refused(line("2026-10", null, "10", "r"), "deduction_type");
        assertThat(parse(line("2026-10", "LOAN_RECOVERY", "10", "r")).deductionType())
                .isEqualTo(DeductionType.LOAN_RECOVERY);
    }

    @Test
    @DisplayName("An empty or 256-character reason is refused; 501 characters of remarks are refused")
    void reasonAndRemarks() {
        refused(line("2026-10", "OTHER", "10", ""), "reason");
        refused(line("2026-10", "OTHER", "10", "   "), "reason");
        refused(line("2026-10", "OTHER", "10", "r".repeat(256)), "reason");
        assertThat(parse(line("2026-10", "OTHER", "10", "r".repeat(255))).reason())
                .hasSize(255);
        refused(
                new EmployeeDeductionLineRequest(
                        UUID.randomUUID(), "2026-10", "OTHER", BigDecimal.TEN, "r", "x".repeat(501), null),
                "remarks");
    }

    @Test
    @DisplayName("No employee_id is refused")
    void employeeRequired() {
        refused(
                new EmployeeDeductionLineRequest(null, "2026-10", "OTHER", BigDecimal.TEN, "r", null, null),
                "employee_id");
    }

    @Test
    @DisplayName("An empty batch and a batch of 501 are refused; 500 is fine")
    void batchSize() {
        assertThatThrownBy(() -> EmployeeDeductionRules.parseBatch(List.of(), OCTOBER))
                .isInstanceOf(EmployeeDeductionValidationException.class);
        assertThatThrownBy(() -> EmployeeDeductionRules.parseBatch(null, OCTOBER))
                .isInstanceOf(EmployeeDeductionValidationException.class);
        assertThatThrownBy(() -> EmployeeDeductionRules.parseBatch(lines(501), OCTOBER))
                .isInstanceOf(EmployeeDeductionValidationException.class)
                .hasMessageContaining("500");
        assertThat(EmployeeDeductionRules.parseBatch(lines(500), OCTOBER)).hasSize(500);
    }

    @Test
    @DisplayName("The first failing line's index is reported, zero-based")
    void failingLineIndex() {
        List<EmployeeDeductionLineRequest> batch = new ArrayList<>(lines(2));
        batch.add(line("2026-10", "OTHER", "-5", "r"));
        batch.add(line("bad", "OTHER", "10", "r"));

        assertThatThrownBy(() -> EmployeeDeductionRules.parseBatch(batch, OCTOBER))
                .isInstanceOfSatisfying(EmployeeDeductionValidationException.class, e -> {
                    assertThat(e.line()).isEqualTo(2);
                    assertThat(e.getMessage()).startsWith("Line 2:");
                });
    }

    @Test
    @DisplayName("A reversal needs a reason of 1 to 255 characters")
    void reversalReason() {
        assertThat(EmployeeDeductionRules.reversalReason(" entered twice ")).isEqualTo("entered twice");
        assertThatThrownBy(() -> EmployeeDeductionRules.reversalReason(" "))
                .isInstanceOf(EmployeeDeductionValidationException.class);
        assertThatThrownBy(() -> EmployeeDeductionRules.reversalReason(null))
                .isInstanceOf(EmployeeDeductionValidationException.class);
        assertThatThrownBy(() -> EmployeeDeductionRules.reversalReason("r".repeat(256)))
                .isInstanceOf(EmployeeDeductionValidationException.class);
    }

    private static List<EmployeeDeductionLineRequest> lines(int n) {
        List<EmployeeDeductionLineRequest> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            lines.add(line("2026-10", "OTHER", "10", "r" + i));
        }
        return lines;
    }

    private static EmployeeDeductionLineRequest line(String period, String type, String amount, String reason) {
        return new EmployeeDeductionLineRequest(
                UUID.randomUUID(), period, type, amount == null ? null : new BigDecimal(amount), reason, null, null);
    }

    private static EmployeeDeductionRules.ParsedLine parse(EmployeeDeductionLineRequest line) {
        return EmployeeDeductionRules.parseBatch(List.of(line), OCTOBER).get(0);
    }

    private static void refused(EmployeeDeductionLineRequest line, String messagePart) {
        assertThatThrownBy(() -> EmployeeDeductionRules.parseBatch(List.of(line), OCTOBER))
                .isInstanceOfSatisfying(EmployeeDeductionValidationException.class, e -> {
                    assertThat(e.line()).isZero();
                    assertThat(e.getMessage()).contains(messagePart);
                });
    }
}
