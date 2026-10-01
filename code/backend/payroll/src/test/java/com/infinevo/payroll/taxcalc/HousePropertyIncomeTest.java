package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.taxcalc.engine.HousePropertyIncome;
import com.infinevo.payroll.taxcalc.engine.HousePropertyIncome.HousePropertyResult;
import com.infinevo.payroll.taxcalc.reader.model.HomeLoanRule;
import com.infinevo.payroll.taxcalc.reader.model.LetOutRule;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoan;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutProperty;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HousePropertyIncome} (W-33.2 spec ? 7).
 */
class HousePropertyIncomeTest {

    private final HomeLoanRule rule24B = new HomeLoanRule(
            "2025-2026",
            "24B",
            "Interest on borrowed capital",
            "INTEREST",
            "SELF_OCCUPIED",
            Money.of("200000"),
            null,
            null,
            false);

    private final LetOutRule letOutRule =
            new LetOutRule("2025-2026", "OLD", BigDecimal.valueOf(30.00), Money.of("200000"), true, true);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID declId = UUID.randomUUID();

    @Test
    @DisplayName("self-occupied interest 2,50,000 capped to -2,00,000 under Section 24(b)")
    void selfOccupiedInterestCappedAt24B() {
        EmployeeInvHomeLoan loan = new EmployeeInvHomeLoan(
                tenantId, declId, "HDFC", null, BigDecimal.ZERO, new BigDecimal("250000"), false, null);

        HousePropertyResult result =
                HousePropertyIncome.calculate(true, false, rule24B, letOutRule, List.of(loan), List.of());

        assertThat(result.selfOccupiedInterest()).isEqualTo(Money.of("200000"));
        assertThat(result.letOutNet()).isEqualTo(Money.ZERO);
        assertThat(result.housePropertyIncome()).isEqualTo(Money.of("-200000"));
        assertThat(result.lossCapApplied()).isFalse(); // Capped by 24(b) individual cap, not combined 71(3A)
    }

    @Test
    @DisplayName("let-out loss -1.5L + self 1.0L = -2.5L capped to -2,00,000 with lossCapApplied = true")
    void combinedLossExceedingLimitIsCapped() {
        EmployeeInvHomeLoan loan = new EmployeeInvHomeLoan(
                tenantId, declId, "HDFC", null, BigDecimal.ZERO, new BigDecimal("100000"), false, null);
        EmployeeInvLetOutProperty letOut =
                new EmployeeInvLetOutProperty(tenantId, declId, "Apt 1", "Address", new BigDecimal("-150000"));

        HousePropertyResult result =
                HousePropertyIncome.calculate(true, true, rule24B, letOutRule, List.of(loan), List.of(letOut));

        assertThat(result.selfOccupiedInterest()).isEqualTo(Money.of("100000"));
        assertThat(result.letOutNet()).isEqualTo(Money.of("-150000"));
        assertThat(result.housePropertyIncome()).isEqualTo(Money.of("-200000"));
        assertThat(result.lossCapApplied()).isTrue();
    }

    @Test
    @DisplayName("let-out net +60k minus self 2.0L = -1.4L uncapped with lossCapApplied = false")
    void combinedLossWithinLimitIsUncapped() {
        EmployeeInvHomeLoan loan = new EmployeeInvHomeLoan(
                tenantId, declId, "HDFC", null, BigDecimal.ZERO, new BigDecimal("200000"), false, null);
        EmployeeInvLetOutProperty letOut =
                new EmployeeInvLetOutProperty(tenantId, declId, "Apt 1", "Address", new BigDecimal("60000"));

        HousePropertyResult result =
                HousePropertyIncome.calculate(true, true, rule24B, letOutRule, List.of(loan), List.of(letOut));

        assertThat(result.selfOccupiedInterest()).isEqualTo(Money.of("200000"));
        assertThat(result.letOutNet()).isEqualTo(Money.of("60000"));
        assertThat(result.housePropertyIncome()).isEqualTo(Money.of("-140000"));
        assertThat(result.lossCapApplied()).isFalse();
    }

    @Test
    @DisplayName("flag isRepayingSelfOccupiedLoan false ignores self-occupied loans")
    void flagFalseIgnoresSelfOccupied() {
        EmployeeInvHomeLoan loan = new EmployeeInvHomeLoan(
                tenantId, declId, "HDFC", null, BigDecimal.ZERO, new BigDecimal("200000"), false, null);
        EmployeeInvLetOutProperty letOut =
                new EmployeeInvLetOutProperty(tenantId, declId, "Apt 1", "Address", new BigDecimal("50000"));

        HousePropertyResult result =
                HousePropertyIncome.calculate(false, true, rule24B, letOutRule, List.of(loan), List.of(letOut));

        assertThat(result.selfOccupiedInterest()).isEqualTo(Money.ZERO);
        assertThat(result.letOutNet()).isEqualTo(Money.of("50000"));
        assertThat(result.housePropertyIncome()).isEqualTo(Money.of("50000"));
    }
}
