package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.taxcalc.engine.ChapterViaDeductions;
import com.infinevo.payroll.taxcalc.engine.ChapterViaDeductions.ChapterViaResult;
import com.infinevo.payroll.taxcalc.engine.ChapterViaDeductions.DeclaredItem;
import com.infinevo.payroll.taxcalc.reader.model.HomeLoanRule;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoan;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ChapterViaDeductions} (W-33.2 spec ? 7).
 */
class ChapterViaDeductionsTest {

    private final Money group80cCap = Money.of("150000");
    private final Money nps1bCap = Money.of("50000");

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

    private final HomeLoanRule rule80Eea = new HomeLoanRule(
            "2025-2026",
            "80EEA",
            "Additional interest, 80EEA",
            "INTEREST",
            "SELF_OCCUPIED",
            Money.of("150000"),
            LocalDate.of(2019, 4, 1),
            LocalDate.of(2022, 3, 31),
            true);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID declId = UUID.randomUUID();

    @Test
    @DisplayName("80C rows 1,20,000 + PF 40,000 + loan principal 30,000 capped to 1,50,000")
    void group80cCombinedCapping() {
        DeclaredItem elss =
                new DeclaredItem("80C", "ELSS Mutual Fund", "80C_GROUP", Money.of("120000"), Money.of("150000"));
        EmployeeInvHomeLoan loan = new EmployeeInvHomeLoan(
                tenantId, declId, "SBI", null, new BigDecimal("30000"), BigDecimal.ZERO, false, null);

        ChapterViaResult result = ChapterViaDeductions.calculate(
                List.of(elss),
                Money.of("40000"), // PF
                Money.ZERO, // VPF
                Money.ZERO, // NPS
                nps1bCap,
                group80cCap,
                List.of(loan),
                rule24B,
                List.of(),
                Money.ZERO,
                Money.of("1000000"));

        // 1,20,000 + 40,000 + 30,000 = 1,90,000 capped to 1,50,000
        assertThat(result.group80cAllowed()).isEqualTo(Money.of("150000"));
        assertThat(result.chapterViaDeductions()).isEqualTo(Money.of("150000"));
    }

    @Test
    @DisplayName("80CCD(1B) 70,000 splits to 50,000 under 1B and 20,000 into 80C group")
    void npsSplitInto1bAnd80cGroup() {
        ChapterViaResult result = ChapterViaDeductions.calculate(
                List.of(),
                Money.ZERO,
                Money.ZERO,
                Money.of("70000"), // NPS 70k
                nps1bCap,
                group80cCap,
                List.of(),
                rule24B,
                List.of(),
                Money.ZERO,
                Money.of("1000000"));

        assertThat(result.nps1bAllowed()).isEqualTo(Money.of("50000"));
        assertThat(result.group80cAllowed()).isEqualTo(Money.of("20000"));
        assertThat(result.chapterViaDeductions()).isEqualTo(Money.of("70000"));
    }

    @Test
    @DisplayName("80D 30,000 allowed within 1,00,000 item cap")
    void item80dWithinCap() {
        DeclaredItem item80d = new DeclaredItem("80D", "Health insurance", null, Money.of("30000"), Money.of("100000"));

        ChapterViaResult result = ChapterViaDeductions.calculate(
                List.of(item80d),
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                nps1bCap,
                group80cCap,
                List.of(),
                rule24B,
                List.of(),
                Money.ZERO,
                Money.of("1000000"));

        assertThat(result.chapterViaDeductions()).isEqualTo(Money.of("30000"));
    }

    @Test
    @DisplayName("80E 3,00,000 allowed in full with null maxLimit")
    void item80eUncapped() {
        DeclaredItem item80e =
                new DeclaredItem("80E", "Higher education loan interest", null, Money.of("300000"), null);

        ChapterViaResult result = ChapterViaDeductions.calculate(
                List.of(item80e),
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                nps1bCap,
                group80cCap,
                List.of(),
                rule24B,
                List.of(),
                Money.ZERO,
                Money.of("1000000"));

        assertThat(result.chapterViaDeductions()).isEqualTo(Money.of("300000"));
    }

    @Test
    @DisplayName("80EEA on interest 3,20,000, first-time buyer sanctioned 2020-06-01 yields 1,20,000")
    void additionalInterest80EEAEligible() {
        // Interest 3,20,000: 24(b) takes 2,00,000, excess is 1,20,000
        EmployeeInvHomeLoan loan = new EmployeeInvHomeLoan(
                tenantId,
                declId,
                "SBI",
                null,
                BigDecimal.ZERO,
                new BigDecimal("320000"),
                true,
                LocalDate.of(2020, 6, 1));

        ChapterViaResult result = ChapterViaDeductions.calculate(
                List.of(),
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                nps1bCap,
                group80cCap,
                List.of(loan),
                rule24B,
                List.of(rule80Eea),
                Money.ZERO,
                Money.of("1000000"));

        assertThat(result.additionalHomeLoanInterest()).isEqualTo(Money.of("120000"));
    }

    @Test
    @DisplayName("80EEA loan sanctioned 2023-01-01 outside window yields zero")
    void additionalInterest80EEAOutsideWindow() {
        EmployeeInvHomeLoan loan = new EmployeeInvHomeLoan(
                tenantId,
                declId,
                "SBI",
                null,
                BigDecimal.ZERO,
                new BigDecimal("320000"),
                true,
                LocalDate.of(2023, 1, 1));

        ChapterViaResult result = ChapterViaDeductions.calculate(
                List.of(),
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                nps1bCap,
                group80cCap,
                List.of(loan),
                rule24B,
                List.of(rule80Eea),
                Money.ZERO,
                Money.of("1000000"));

        assertThat(result.additionalHomeLoanInterest()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("total Chapter VI-A deductions capped at gross total income under Section 80A(2)")
    void cappedAtGrossTotalIncome() {
        DeclaredItem elss = new DeclaredItem("80C", "ELSS", "80C_GROUP", Money.of("150000"), Money.of("150000"));
        DeclaredItem item80d = new DeclaredItem("80D", "Health", null, Money.of("50000"), Money.of("100000"));

        // Total deductions = 2,00,000, but Gross Total Income is only 1,20,000
        ChapterViaResult result = ChapterViaDeductions.calculate(
                List.of(elss, item80d),
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                nps1bCap,
                group80cCap,
                List.of(),
                rule24B,
                List.of(),
                Money.ZERO,
                Money.of("120000"));

        assertThat(result.totalAllowed()).isEqualTo(Money.of("120000"));
    }

    @Test
    @DisplayName("80CCD(1B) unified cap across pre-tax employeeNps and declared 80CCD(1B) row")
    void unifiedNps1bCapAcrossPreTaxAndDeclaredItem() {
        DeclaredItem declaredNps1b =
                new DeclaredItem("80CCD(1B)", "NPS Tier 1 (80CCD(1B))", null, Money.of("40000"), Money.of("50000"));

        ChapterViaResult result = ChapterViaDeductions.calculate(
                List.of(declaredNps1b),
                Money.ZERO,
                Money.ZERO,
                Money.of("35000"), // Pre-tax NPS 35k + declared 40k = 75k total
                nps1bCap,
                group80cCap,
                List.of(),
                rule24B,
                List.of(),
                Money.ZERO,
                Money.of("1000000"));

        // Combined 75,000: 50,000 under 80CCD(1B) and 25,000 spilled into 80C group
        assertThat(result.nps1bAllowed()).isEqualTo(Money.of("50000"));
        assertThat(result.group80cAllowed()).isEqualTo(Money.of("25000"));
        assertThat(result.chapterViaDeductions()).isEqualTo(Money.of("75000"));
    }

    @Test
    @DisplayName("80EEA two eligible loans aggregate interest exceeding 24(b) is capped once at 1,50,000")
    void twoLoansAggregate80EEACappedAt150k() {
        EmployeeInvHomeLoan loan1 = new EmployeeInvHomeLoan(
                tenantId,
                declId,
                "SBI",
                null,
                BigDecimal.ZERO,
                new BigDecimal("280000"),
                true,
                LocalDate.of(2020, 6, 1));
        EmployeeInvHomeLoan loan2 = new EmployeeInvHomeLoan(
                tenantId,
                declId,
                "HDFC",
                null,
                BigDecimal.ZERO,
                new BigDecimal("140000"),
                true,
                LocalDate.of(2021, 1, 15));

        ChapterViaResult result = ChapterViaDeductions.calculate(
                List.of(),
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                nps1bCap,
                group80cCap,
                List.of(loan1, loan2),
                rule24B,
                List.of(rule80Eea),
                Money.ZERO,
                Money.of("1500000"));

        // Total interest = 4,20,000; excess over 2,00,000 24(b) = 2,20,000; capped at 1,50,000
        assertThat(result.additionalHomeLoanInterest()).isEqualTo(Money.of("150000"));
    }

    @Test
    @DisplayName("Dynamic group cap applies to non-80C category group")
    void dynamicSecondGroupCapEnforced() {
        DeclaredItem item1 =
                new DeclaredItem("80D_SELF", "80D Self & Family", "80D_GROUP", Money.of("40000"), Money.of("50000"));
        DeclaredItem item2 =
                new DeclaredItem("80D_PARENTS", "80D Parents", "80D_GROUP", Money.of("50000"), Money.of("50000"));

        ChapterViaResult result = ChapterViaDeductions.calculate(
                List.of(item1, item2),
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                nps1bCap,
                java.util.Map.of("80C_GROUP", group80cCap, "80D_GROUP", Money.of("75000")),
                List.of(),
                rule24B,
                List.of(),
                Money.ZERO,
                Money.of("1000000"));

        // 40,000 + 50,000 = 90,000 capped to 75,000 by 80D_GROUP cap
        assertThat(result.chapterViaDeductions()).isEqualTo(Money.of("75000"));
    }
}
