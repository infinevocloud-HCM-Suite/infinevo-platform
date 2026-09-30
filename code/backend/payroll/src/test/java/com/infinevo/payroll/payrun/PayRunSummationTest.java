package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.StructureFixtures.JULY;
import static com.infinevo.payroll.payrun.StructureFixtures.TENANT;
import static com.infinevo.payroll.payrun.StructureFixtures.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-29.2 §7 — the hand calculation of §8, to the rupee, and rounding once, on net pay only. */
class PayRunSummationTest {

    @Test
    @DisplayName("The §8 worked example: gross 45,000, reimbursements 2,000, benefits 1,800, net 47,000.00")
    void workedExample() {
        StructureFixtures.WorkedExample example = StructureFixtures.workedExample();
        EarningRepository earningRepository = mock(EarningRepository.class);
        when(earningRepository.findAllByTenantIdAndDeletedFalse(TENANT)).thenReturn(example.catalogue());

        List<PayLine> lines = new StructureLineContributor(earningRepository)
                .contribute(context(example.version(), JULY, LocalDate.of(2023, 4, 1)));

        assertThat(lines)
                .extracting(
                        PayLine::componentCode, PayLine::kind, l -> l.amount().raw(), PayLine::taxable)
                .containsExactly(
                        tuple("BASIC", LineKind.EARNING, new BigDecimal("25000.0000"), true),
                        tuple("HRA", LineKind.EARNING, new BigDecimal("10000.0000"), true),
                        tuple("SPECIAL", LineKind.EARNING, new BigDecimal("7500.0000"), true),
                        tuple("MEAL", LineKind.EARNING, new BigDecimal("1500.0000"), false),
                        tuple("MEAL", LineKind.EARNING, new BigDecimal("1000.0000"), true),
                        tuple("EMPLOYER_PF", LineKind.BENEFIT, new BigDecimal("1800.0000"), false),
                        tuple("FUEL", LineKind.REIMBURSEMENT, new BigDecimal("2000.0000"), false));

        PayRunTotals totals = PayRunTotals.of(lines);
        assertThat(totals.grossEarnings().raw()).isEqualByComparingTo("45000.0000");
        assertThat(totals.totalReimbursements().raw()).isEqualByComparingTo("2000.0000");
        assertThat(totals.totalBenefits().raw()).isEqualByComparingTo("1800.0000");
        assertThat(totals.totalDeductions().raw()).isEqualByComparingTo("0");
        assertThat(totals.netPay()).isEqualTo(new BigDecimal("47000.00"));
    }

    @Test
    @DisplayName("Lines are summed at scale 4 and net pay is rounded once: 3 × 0.3333 is 1.00, not 0.99")
    void roundsOnceOnNetPay() {
        List<PayLine> lines = List.of(line("A", "0.3333"), line("B", "0.3333"), line("C", "0.3333"));

        PayRunTotals totals = PayRunTotals.of(lines);

        assertThat(totals.grossEarnings().raw()).isEqualByComparingTo("0.9999");
        assertThat(totals.netPay()).isEqualTo(new BigDecimal("1.00"));
    }

    @Test
    @DisplayName("Deductions come off net; benefits never enter it")
    void deductionsAndBenefits() {
        List<PayLine> lines = List.of(
                line("BASIC", "10000.0000"),
                new PayLine(LineKind.DEDUCTION, LineSource.STRUCTURE, null, "LOAN", "Loan", Money.of("1500"), false),
                new PayLine(
                        LineKind.BENEFIT, LineSource.STRUCTURE, null, "PF", "Employer PF", Money.of("1200"), false));

        PayRunTotals totals = PayRunTotals.of(lines);

        assertThat(totals.totalDeductions().raw()).isEqualByComparingTo("1500");
        assertThat(totals.totalBenefits().raw()).isEqualByComparingTo("1200");
        assertThat(totals.netPay()).isEqualTo(new BigDecimal("8500.00"));
    }

    private static PayLine line(String code, String amount) {
        return new PayLine(
                LineKind.EARNING, LineSource.STRUCTURE, UUID.randomUUID(), code, code, Money.of(amount), true);
    }
}
