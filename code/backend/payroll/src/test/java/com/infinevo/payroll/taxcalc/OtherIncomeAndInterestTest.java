package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.taxcalc.engine.OtherIncomeAndInterest;
import com.infinevo.payroll.taxcalc.engine.OtherIncomeAndInterest.OtherIncomeResult;
import com.infinevo.payroll.taxcalc.reader.model.OtherIncomeRule;
import com.infinevo.payroll.taxdeclaration.summary.EmployeeInvOtherIncome;
import com.infinevo.payroll.taxdeclaration.summary.OtherIncomeKind;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OtherIncomeAndInterest} (W-33.2 spec § 7).
 */
class OtherIncomeAndInterestTest {

    private final OtherIncomeRule rule80Tta = new OtherIncomeRule(
            "2025-2026",
            "80TTA",
            "Interest on savings account",
            "DEDUCTION",
            "OLD",
            Money.of("10000"),
            BigDecimal.valueOf(100),
            false,
            false);

    private final OtherIncomeRule rule80Ttb = new OtherIncomeRule(
            "2025-2026",
            "80TTB",
            "Interest income, senior citizen",
            "DEDUCTION",
            "OLD",
            Money.of("50000"),
            BigDecimal.valueOf(100),
            false,
            true);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID declId = UUID.randomUUID();

    private EmployeeInvOtherIncome createIncome(OtherIncomeKind kind, String amount) {
        EmployeeInvOtherIncome income = new EmployeeInvOtherIncome();
        income.setTenantId(tenantId);
        income.setDeclarationId(declId);
        income.setKind(kind);
        income.setAmount(new BigDecimal(amount));
        return income;
    }

    @Test
    @DisplayName("GENERAL category: savings interest 14,000 capped to 10,000 under 80TTA")
    void generalCategory80TtaCapped() {
        List<EmployeeInvOtherIncome> rows = List.of(
                createIncome(OtherIncomeKind.SAVINGS_INTEREST, "14000"),
                createIncome(OtherIncomeKind.FD_INTEREST, "30000"));

        OtherIncomeResult result = OtherIncomeAndInterest.calculate(rows, AgeCategory.GENERAL, rule80Tta, rule80Ttb);

        assertThat(result.totalOtherIncome()).isEqualTo(Money.of("44000"));
        assertThat(result.interestSectionCode()).isEqualTo("80TTA");
        assertThat(result.interestDeduction()).isEqualTo(Money.of("10000"));
    }

    @Test
    @DisplayName("SENIOR category: savings 14k + FD 60k = 74k capped to 50,000 under 80TTB")
    void seniorCategory80TtbCapped() {
        List<EmployeeInvOtherIncome> rows = List.of(
                createIncome(OtherIncomeKind.SAVINGS_INTEREST, "14000"),
                createIncome(OtherIncomeKind.FD_INTEREST, "60000"));

        OtherIncomeResult result = OtherIncomeAndInterest.calculate(rows, AgeCategory.SENIOR, rule80Tta, rule80Ttb);

        assertThat(result.totalOtherIncome()).isEqualTo(Money.of("74000"));
        assertThat(result.interestSectionCode()).isEqualTo("80TTB");
        assertThat(result.interestDeduction()).isEqualTo(Money.of("50000"));
    }

    @Test
    @DisplayName("SUPER_SENIOR category: savings 8k + FD 20k = 28k uncapped under 80TTB (within 50k limit)")
    void superSeniorWithinLimit() {
        List<EmployeeInvOtherIncome> rows = List.of(
                createIncome(OtherIncomeKind.SAVINGS_INTEREST, "8000"),
                createIncome(OtherIncomeKind.FD_INTEREST, "20000"));

        OtherIncomeResult result =
                OtherIncomeAndInterest.calculate(rows, AgeCategory.SUPER_SENIOR, rule80Tta, rule80Ttb);

        assertThat(result.totalOtherIncome()).isEqualTo(Money.of("28000"));
        assertThat(result.interestSectionCode()).isEqualTo("80TTB");
        assertThat(result.interestDeduction()).isEqualTo(Money.of("28000"));
    }
}
