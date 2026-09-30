package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.StructureFixtures.JULY;
import static com.infinevo.payroll.payrun.StructureFixtures.TENANT;
import static com.infinevo.payroll.payrun.StructureFixtures.context;
import static com.infinevo.payroll.payrun.StructureFixtures.earning;
import static com.infinevo.payroll.payrun.StructureFixtures.fixed;
import static com.infinevo.payroll.payrun.StructureFixtures.item;
import static com.infinevo.payroll.payrun.StructureFixtures.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.shared.money.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-29.2 §7 — the STRUCTURE contributor turns a version into the §3 lines. */
class StructureLineContributorTest {

    private EarningRepository earningRepository;
    private StructureLineContributor contributor;

    @BeforeEach
    void setUp() {
        earningRepository = mock(EarningRepository.class);
        contributor = new StructureLineContributor(earningRepository);
    }

    @Test
    @DisplayName("Three fixed, one variable, one benefit, one reimbursement, one FBP at half its pool")
    void fullStructure() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID variable = UUID.randomUUID();
        UUID fbp = UUID.randomUUID();
        List<Earning> catalogue = List.of(
                earning(a, false, true),
                earning(b, false, true),
                earning(c, false, false),
                earning(variable, true, true),
                earning(fbp, false, true));
        when(earningRepository.findAllByTenantIdAndDeletedFalse(TENANT)).thenReturn(catalogue);
        var version = version(
                List.of(
                        fixed(a, "BASIC", "Basic", "20000.0000"),
                        fixed(b, "HRA", "HRA", "8000.0000"),
                        fixed(c, "CONV", "Conveyance", "1600.0000"),
                        item(variable, "QBONUS", "Quarterly bonus", "0", "12000.0000", true, "Quarterly", false, null),
                        item(fbp, "MEAL", "Meal card", "2000.0000", "24000.0000", true, null, true, "1000.0000")),
                List.of(fixed(UUID.randomUUID(), "GRATUITY", "Gratuity", "900.0000")),
                List.of(fixed(UUID.randomUUID(), "PHONE", "Phone", "500.0000")));

        List<PayLine> lines = contributor.contribute(context(version, JULY, LocalDate.of(2024, 1, 1)));

        assertThat(lines)
                .extracting(
                        PayLine::kind,
                        PayLine::componentCode,
                        l -> l.amount().raw().toPlainString(),
                        PayLine::taxable)
                .containsExactly(
                        tuple(LineKind.EARNING, "BASIC", "20000.0000", true),
                        tuple(LineKind.EARNING, "HRA", "8000.0000", true),
                        tuple(LineKind.EARNING, "CONV", "1600.0000", false),
                        tuple(LineKind.EARNING, "QBONUS", "3000.0000", true),
                        tuple(LineKind.EARNING, "MEAL", "1000.0000", false),
                        tuple(LineKind.EARNING, "MEAL", "1000.0000", true),
                        tuple(LineKind.BENEFIT, "GRATUITY", "900.0000", false),
                        tuple(LineKind.REIMBURSEMENT, "PHONE", "500.0000", false));
        assertThat(lines).allMatch(l -> l.source() == LineSource.STRUCTURE);

        PayRunTotals totals = PayRunTotals.of(lines);
        assertThat(totals.totalBenefits()).isEqualTo(Money.of("900"));
        // Benefit outside net: 20000 + 8000 + 1600 + 3000 + 2000 earnings + 500 reimbursement.
        assertThat(totals.netPay()).isEqualByComparingTo("35100.00");
    }

    @Test
    @DisplayName("A disabled line, and a zero amount, produce nothing")
    void disabledAndZeroProduceNothing() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<Earning> catalogue = List.of(earning(a, false, true), earning(b, false, true));
        when(earningRepository.findAllByTenantIdAndDeletedFalse(TENANT)).thenReturn(catalogue);
        var version = version(
                List.of(
                        item(a, "OFF", "Disabled", "1000.0000", "12000.0000", false, null, false, null),
                        fixed(b, "ZERO", "Zero", "0.0000")),
                List.of(item(UUID.randomUUID(), "B", "B", "100.0000", "1200.0000", false, null, false, null)),
                List.of());

        assertThat(contributor.contribute(context(version, JULY, LocalDate.of(2024, 1, 1))))
                .isEmpty();
    }

    @Test
    @DisplayName("A variable earning outside its month writes no line; a MONTHLY one writes every month")
    void variableEarnings() {
        UUID yearly = UUID.randomUUID();
        UUID monthly = UUID.randomUUID();
        List<Earning> catalogue = List.of(earning(yearly, true, true), earning(monthly, true, true));
        when(earningRepository.findAllByTenantIdAndDeletedFalse(TENANT)).thenReturn(catalogue);
        var version = version(
                List.of(
                        item(yearly, "YB", "Yearly bonus", "1000.0000", "12000.0000", true, "YEARLY", false, null),
                        item(
                                monthly,
                                "MI",
                                "Monthly incentive",
                                "700.0000",
                                "8400.0000",
                                true,
                                "MONTHLY",
                                false,
                                null)),
                List.of(),
                List.of());

        assertThat(contributor.contribute(context(version, JULY, LocalDate.of(2024, 1, 1))))
                .extracting(PayLine::componentCode)
                .containsExactly("MI");
        assertThat(contributor.contribute(context(version, YearMonth.of(2027, 1), LocalDate.of(2024, 1, 1))))
                .extracting(PayLine::componentCode)
                .containsExactly("YB", "MI");
    }

    @Test
    @DisplayName("A component the catalogue lost is an error for the employee, not a silent zero")
    void deletedComponentThrows() {
        UUID gone = UUID.randomUUID();
        List<Earning> catalogue = List.of();
        when(earningRepository.findAllByTenantIdAndDeletedFalse(TENANT)).thenReturn(catalogue);
        var version = version(List.of(fixed(gone, "UNKNOWN", "Unknown", "1000.0000")), List.of(), List.of());

        assertThatThrownBy(() -> contributor.contribute(context(version, JULY, LocalDate.of(2024, 1, 1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no longer in the catalogue");
    }

    @Test
    @DisplayName("A variable earning with no readable frequency, and an FBP declared above its pool, are errors")
    void unreadableConfigurationThrows() {
        UUID variable = UUID.randomUUID();
        UUID fbp = UUID.randomUUID();
        List<Earning> catalogue = List.of(earning(variable, true, true), earning(fbp, false, true));
        when(earningRepository.findAllByTenantIdAndDeletedFalse(TENANT)).thenReturn(catalogue);

        var noFrequency = version(
                List.of(item(variable, "V", "V", "0", "1200.0000", true, null, false, null)), List.of(), List.of());
        assertThatThrownBy(() -> contributor.contribute(context(noFrequency, JULY, LocalDate.of(2024, 1, 1))))
                .hasMessageContaining("no recognisable earning frequency");

        var overDeclared = version(
                List.of(item(fbp, "MEAL", "Meal", "1000.0000", "12000.0000", true, null, true, "1500.0000")),
                List.of(),
                List.of());
        assertThatThrownBy(() -> contributor.contribute(context(overDeclared, JULY, LocalDate.of(2024, 1, 1))))
                .hasMessageContaining("above its pool");
    }
}
