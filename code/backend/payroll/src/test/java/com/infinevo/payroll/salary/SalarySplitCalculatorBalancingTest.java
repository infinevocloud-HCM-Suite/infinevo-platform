package com.infinevo.payroll.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.PercentageOf;
import com.infinevo.payroll.statutory.lines.DerivedStatutoryLine;
import com.infinevo.payroll.statutory.lines.StatutoryComponentCode;
import com.infinevo.payroll.statutory.settings.EpfSetting;
import com.infinevo.payroll.statutory.settings.EsiSetting;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-75.1 §7 — the balancing earning: its amount is what is left of the CTC after every other in-CTC component and
 * in-CTC employer statutory line, so the version always balances to the paisa.
 */
class SalarySplitCalculatorBalancingTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMPLOYEE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final LocalDate FROM = LocalDate.of(2026, 4, 1);

    private static final UUID BASIC_ID = UUID.randomUUID();
    private static final UUID HRA_ID = UUID.randomUUID();
    private static final UUID SPECIAL_ID = UUID.randomUUID();

    // ---- the ₹6,00,000 CTC cases: Basic 50% of CTC, HRA 50% of Basic, Special balancing ----

    @Test
    @DisplayName("6,00,000 CTC, employer EPF in CTC, no ceiling: Special = 9,500.00")
    void employerEpfInCtcNoCeiling() {
        SalarySplitCalculator.StatutoryInputs st = pfInputs(epf(true, false));

        SalarySplitCalculator.SplitOutput split = SalarySplitCalculator.resolveWithBalancing(
                new BigDecimal("600000.00"), standardEarnings(), List.of(), List.of(), st);

        assertThat(special(split).monthlyAmount()).isEqualByComparingTo("9500.00");
        assertThat(special(split).value()).isEqualByComparingTo("9500.00");
        assertBalances(new BigDecimal("600000.00"), split, st);
    }

    @Test
    @DisplayName("6,00,000 CTC, employer EPF in CTC, restricted to the 15,000 ceiling: Special = 10,700.00")
    void employerEpfInCtcWithCeiling() {
        SalarySplitCalculator.StatutoryInputs st = pfInputs(epf(true, true));

        SalarySplitCalculator.SplitOutput split = SalarySplitCalculator.resolveWithBalancing(
                new BigDecimal("600000.00"), standardEarnings(), List.of(), List.of(), st);

        assertThat(special(split).monthlyAmount()).isEqualByComparingTo("10700.00");
        assertBalances(new BigDecimal("600000.00"), split, st);
    }

    @Test
    @DisplayName("6,00,000 CTC, employer EPF not in CTC: Special = 12,500.00")
    void employerEpfNotInCtc() {
        SalarySplitCalculator.StatutoryInputs st = pfInputs(epf(false, false));

        SalarySplitCalculator.SplitOutput split = SalarySplitCalculator.resolveWithBalancing(
                new BigDecimal("600000.00"), standardEarnings(), List.of(), List.of(), st);

        assertThat(special(split).monthlyAmount()).isEqualByComparingTo("12500.00");
        assertBalances(new BigDecimal("600000.00"), split, st);
    }

    @Test
    @DisplayName("The sent value of the balancing earning is ignored")
    void sentValueIgnored() {
        List<SalarySplitCalculator.ComponentInput> earnings = new ArrayList<>(standardEarnings());
        earnings.set(2, flat(SPECIAL_ID, "SPECIAL", "ALLOWANCE", "99999.00", true));

        SalarySplitCalculator.SplitOutput split = SalarySplitCalculator.resolveWithBalancing(
                new BigDecimal("600000.00"), earnings, List.of(), List.of(), null);

        assertThat(special(split).monthlyAmount()).isEqualByComparingTo("12500.00");
    }

    // ---- ESI: gross contains B, so B is solved, then the ceiling is checked ----

    @Test
    @DisplayName("ESI in CTC, gross under 21,000: B = (M - others - r*E0) / (1 + r) and the version balances")
    void esiInCtcUnderCeiling() {
        // M = 21,600; E0 = Basic 10,800 + HRA 5,400 = 16,200; r = 3.25%
        // B = (21,600 - 16,200 - 526.50) / 1.0325 = 4,720.0969; gross 20,920.0969 <= 21,000
        SalarySplitCalculator.StatutoryInputs st = esiInputs();
        BigDecimal ctc = new BigDecimal("259200.00");

        SalarySplitCalculator.SplitOutput split =
                SalarySplitCalculator.resolveWithBalancing(ctc, standardEarnings(), List.of(), List.of(), st);

        assertThat(special(split).monthlyAmount()).isEqualByComparingTo("4720.0969");
        List<DerivedStatutoryLine> lines = SalarySplitCalculator.deriveStatutory(split, st);
        assertThat(lines)
                .filteredOn(l -> l.code() == StatutoryComponentCode.ESI_EMPLOYER)
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.monthlyAmount().raw()).isEqualByComparingTo("679.9031");
                    assertThat(l.includedInCtc()).isTrue();
                });
        assertBalances(ctc, split, st);
    }

    @Test
    @DisplayName("ESI in CTC, solved gross just over 21,000: ESI does not apply and B is recomputed without it")
    void esiInCtcJustOverCeiling() {
        // M = 21,700; E0 = 16,275; with ESI the gross would be 21,016.95 > 21,000, so B = 21,700 - 16,275 = 5,425
        SalarySplitCalculator.StatutoryInputs st = esiInputs();
        BigDecimal ctc = new BigDecimal("260400.00");

        SalarySplitCalculator.SplitOutput split =
                SalarySplitCalculator.resolveWithBalancing(ctc, standardEarnings(), List.of(), List.of(), st);

        assertThat(special(split).monthlyAmount()).isEqualByComparingTo("5425.00");
        assertThat(SalarySplitCalculator.deriveStatutory(split, st)).noneMatch(DerivedStatutoryLine::isEsi);
        assertBalances(ctc, split, st);
    }

    // ---- rounding ----

    @Test
    @DisplayName("Leftover paisa from rounding lands in B, so validateCtcBalance passes exactly")
    void leftoverPaisaLandsInBalancing() {
        // CTC 1,20,000.05: ten 1% allowances each round their monthly amount down, so the first B
        // (M - others = 4,000.0021) over-fills the year to 1,20,000.06. The leftover moves B to 4,000.0017.
        BigDecimal ctc = new BigDecimal("120000.05");
        List<SalarySplitCalculator.ComponentInput> earnings = new ArrayList<>();
        earnings.add(percent(BASIC_ID, "BASIC", "BASIC", "50", PercentageOf.CTC));
        for (int i = 0; i < 10; i++) {
            earnings.add(percent(UUID.randomUUID(), "ALW" + i, "ALLOWANCE", "1", PercentageOf.CTC));
        }
        earnings.add(flat(SPECIAL_ID, "SPECIAL", "ALLOWANCE", "0", true));

        List<SalarySplitCalculator.ComponentInput> naive = new ArrayList<>(earnings);
        naive.set(naive.size() - 1, flat(SPECIAL_ID, "SPECIAL", "ALLOWANCE", "4000.0021", false));
        SalarySplitCalculator.SplitOutput naiveSplit = SalarySplitCalculator.resolve(ctc, naive, List.of(), List.of());
        assertThatThrownBy(() -> SalarySplitCalculator.validateCtcBalance(ctc, naiveSplit, BigDecimal.ZERO))
                .isInstanceOf(SalaryValidationException.class);

        SalarySplitCalculator.SplitOutput split =
                SalarySplitCalculator.resolveWithBalancing(ctc, earnings, List.of(), List.of(), null);

        assertThat(special(split).monthlyAmount()).isEqualByComparingTo("4000.0017");
        assertBalances(ctc, split, null);
    }

    // ---- refusals ----

    @Test
    @DisplayName("Two balancing earnings are refused on earnings[i].balancing")
    void twoBalancingRowsRefused() {
        List<SalarySplitCalculator.ComponentInput> earnings = new ArrayList<>(standardEarnings());
        earnings.add(flat(UUID.randomUUID(), "CONV", "ALLOWANCE", "0", true));

        assertThatThrownBy(() -> SalarySplitCalculator.resolveWithBalancing(
                        new BigDecimal("600000.00"), earnings, List.of(), List.of(), null))
                .isInstanceOfSatisfying(SalaryValidationException.class, e -> assertThat(e.getFieldErrors())
                        .containsOnlyKeys("earnings[3].balancing"));
    }

    @Test
    @DisplayName("Basic as the balancing earning is refused")
    void basicAsBalancingRefused() {
        List<SalarySplitCalculator.ComponentInput> earnings =
                List.of(flat(BASIC_ID, "BASIC", "BASIC", "0", true), flat(HRA_ID, "HRA", "ALLOWANCE", "10000", false));

        assertThatThrownBy(() -> SalarySplitCalculator.resolveWithBalancing(
                        new BigDecimal("600000.00"), earnings, List.of(), List.of(), null))
                .isInstanceOfSatisfying(SalaryValidationException.class, e -> assertThat(e.getFieldErrors())
                        .containsEntry("earnings[0].balancing", "Basic cannot be the balancing earning"));
    }

    @Test
    @DisplayName("A percentage, out-of-CTC, FBP or variable earning cannot be the balancing one")
    void ineligibleBalancingRefused() {
        SalarySplitCalculator.ComponentInput basic = percent(BASIC_ID, "BASIC", "BASIC", "50", PercentageOf.CTC);
        SalarySplitCalculator.ComponentInput percentage = new SalarySplitCalculator.ComponentInput(
                SPECIAL_ID,
                "SPECIAL",
                "Special",
                CalculationType.PERCENTAGE,
                BigDecimal.TEN,
                PercentageOf.CTC,
                true,
                "ALLOWANCE",
                "MONTHLY",
                null,
                true,
                false,
                false);
        SalarySplitCalculator.ComponentInput outOfCtc = new SalarySplitCalculator.ComponentInput(
                SPECIAL_ID,
                "SPECIAL",
                "Special",
                CalculationType.FLAT,
                BigDecimal.ZERO,
                null,
                false,
                "ALLOWANCE",
                "MONTHLY",
                null,
                true,
                false,
                false);
        SalarySplitCalculator.ComponentInput fbp = new SalarySplitCalculator.ComponentInput(
                SPECIAL_ID,
                "SPECIAL",
                "Special",
                CalculationType.FLAT,
                BigDecimal.ZERO,
                null,
                true,
                "ALLOWANCE",
                "MONTHLY",
                null,
                true,
                true,
                false);
        SalarySplitCalculator.ComponentInput variable = new SalarySplitCalculator.ComponentInput(
                SPECIAL_ID,
                "SPECIAL",
                "Special",
                CalculationType.FLAT,
                BigDecimal.ZERO,
                null,
                true,
                "ALLOWANCE",
                "MONTHLY",
                null,
                true,
                false,
                true);

        for (SalarySplitCalculator.ComponentInput bad : List.of(percentage, outOfCtc, fbp, variable)) {
            assertThatThrownBy(() -> SalarySplitCalculator.resolveWithBalancing(
                            new BigDecimal("600000.00"), List.of(basic, bad), List.of(), List.of(), null))
                    .isInstanceOfSatisfying(SalaryValidationException.class, e -> assertThat(e.getFieldErrors())
                            .containsKey("earnings[1].balancing"));
        }
    }

    @Test
    @DisplayName("Negative B is refused on annualCtc with the amount by which the CTC is exceeded")
    void negativeBalanceRefused() {
        // Basic 25,000 + HRA 12,500 + Conveyance 20,000 = 57,500 a month against a 50,000 monthly CTC
        List<SalarySplitCalculator.ComponentInput> earnings = new ArrayList<>(standardEarnings());
        earnings.add(flat(UUID.randomUUID(), "CONV", "ALLOWANCE", "20000", false));

        assertThatThrownBy(() -> SalarySplitCalculator.resolveWithBalancing(
                        new BigDecimal("600000.00"), earnings, List.of(), List.of(), null))
                .isInstanceOfSatisfying(SalaryValidationException.class, e -> assertThat(e.getFieldErrors())
                        .containsEntry(
                                "annualCtc", "Components and employer contributions exceed the CTC by 90000.00"));
    }

    @Test
    @DisplayName("Without a balancing earning the result is exactly resolve()")
    void noBalancingUnchanged() {
        List<SalarySplitCalculator.ComponentInput> earnings = List.of(
                percent(BASIC_ID, "BASIC", "BASIC", "50", PercentageOf.CTC),
                percent(HRA_ID, "HRA", "ALLOWANCE", "50", PercentageOf.BASIC),
                flat(SPECIAL_ID, "SPECIAL", "ALLOWANCE", "1234.00", false));

        SalarySplitCalculator.SplitOutput expected =
                SalarySplitCalculator.resolve(new BigDecimal("600000.00"), earnings, List.of(), List.of());
        SalarySplitCalculator.SplitOutput actual = SalarySplitCalculator.resolveWithBalancing(
                new BigDecimal("600000.00"), earnings, List.of(), List.of(), pfInputs(epf(true, false)));

        assertThat(actual).isEqualTo(expected);
        assertThat(special(actual).monthlyAmount()).isEqualByComparingTo("1234.00");
    }

    // ---- helpers ----

    private static List<SalarySplitCalculator.ComponentInput> standardEarnings() {
        return List.of(
                percent(BASIC_ID, "BASIC", "BASIC", "50", PercentageOf.CTC),
                percent(HRA_ID, "HRA", "ALLOWANCE", "50", PercentageOf.BASIC),
                flat(SPECIAL_ID, "SPECIAL", "ALLOWANCE", "0", true));
    }

    private static SalarySplitCalculator.ComponentInput percent(
            UUID id, String code, String type, String value, PercentageOf of) {
        return new SalarySplitCalculator.ComponentInput(
                id, code, code, CalculationType.PERCENTAGE, new BigDecimal(value), of, true, type, "MONTHLY", null);
    }

    private static SalarySplitCalculator.ComponentInput flat(
            UUID id, String code, String type, String value, boolean balancing) {
        return new SalarySplitCalculator.ComponentInput(
                id,
                code,
                code,
                CalculationType.FLAT,
                new BigDecimal(value),
                null,
                true,
                type,
                "MONTHLY",
                null,
                balancing,
                false,
                false);
    }

    private static SalarySplitCalculator.CalculatedResult special(SalarySplitCalculator.SplitOutput split) {
        return split.earnings().stream()
                .filter(e -> SPECIAL_ID.equals(e.componentId()))
                .findFirst()
                .orElseThrow();
    }

    private static EpfSetting epf(boolean employerInCtc, boolean restrictEmployerToCeiling) {
        EpfSetting epf = new EpfSetting(TENANT);
        epf.setEnabled(true);
        epf.setRestrictEmployeeToCeiling(true);
        epf.setRestrictEmployerToCeiling(restrictEmployerToCeiling);
        epf.setIncludeEmployerInCtc(employerInCtc);
        epf.setIncludeEdliInCtc(false);
        epf.setIncludeAdminInCtc(false);
        return epf;
    }

    private static SalarySplitCalculator.StatutoryInputs pfInputs(EpfSetting epf) {
        EmployeeStatutoryProfile profile = new EmployeeStatutoryProfile(TENANT, EMPLOYEE, "system");
        profile.setEligibleForPf(true);
        profile.setEligibleForEps(true);
        return new SalarySplitCalculator.StatutoryInputs(profile, epf, null, null, FROM);
    }

    private static SalarySplitCalculator.StatutoryInputs esiInputs() {
        EmployeeStatutoryProfile profile = new EmployeeStatutoryProfile(TENANT, EMPLOYEE, "system");
        profile.setEligibleForEsi(true);
        EsiSetting esi = new EsiSetting(TENANT);
        esi.setEnabled(true);
        esi.setIncludeEmployerInCtc(true);
        return new SalarySplitCalculator.StatutoryInputs(profile, null, esi, null, FROM);
    }

    private static void assertBalances(
            BigDecimal ctc, SalarySplitCalculator.SplitOutput split, SalarySplitCalculator.StatutoryInputs st) {
        BigDecimal statutory =
                SalarySplitCalculator.includedStatutoryAnnual(SalarySplitCalculator.deriveStatutory(split, st));
        assertThatCode(() -> SalarySplitCalculator.validateCtcBalance(ctc, split, statutory))
                .doesNotThrowAnyException();
    }
}
