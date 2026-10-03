package com.infinevo.payroll.statutory.payrun;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatutoryLineRounderTest {

    @Test
    @DisplayName("PF line 1,249.5000 rounds to 1,250 via HALF_UP")
    void pfRoundsHalfUp() {
        Money amount = Money.of("1249.5000");
        Money rounded = StatutoryLineRounder.roundPf(amount);
        assertThat(rounded).isEqualTo(Money.of("1250.0000"));

        Money roundedByCode = StatutoryLineRounder.round("EPS_EMPLOYER", amount);
        assertThat(roundedByCode).isEqualTo(Money.of("1250.0000"));
    }

    @Test
    @DisplayName("Employer EPF: 1,800 total - 1,250 EPS = 550 so they add up to 1,800")
    void employerPfDifferenceRule() {
        Money employerTotal = Money.of("1800.0000");
        Money eps = Money.of("1249.5000");

        Money erPf = StatutoryLineRounder.roundEmployerPf(employerTotal, eps);
        assertThat(erPf).isEqualTo(Money.of("550.0000"));

        Money erEps = StatutoryLineRounder.roundPf(eps);
        assertThat(erPf.add(erEps)).isEqualTo(Money.of("1800.0000"));
    }

    @Test
    @DisplayName("ESI lines round UP to the next rupee using CEILING: 168.7500 -> 169, 168.0001 -> 169")
    void esiRoundsCeiling() {
        Money val1 = Money.of("168.7500");
        Money val2 = Money.of("168.0001");

        assertThat(StatutoryLineRounder.roundEsi(val1)).isEqualTo(Money.of("169.0000"));
        assertThat(StatutoryLineRounder.roundEsi(val2)).isEqualTo(Money.of("169.0000"));

        assertThat(StatutoryLineRounder.round("ESI_EMPLOYEE", val1)).isEqualTo(Money.of("169.0000"));
        assertThat(StatutoryLineRounder.round("ESI_EMPLOYER", val2)).isEqualTo(Money.of("169.0000"));
    }

    @Test
    @DisplayName("Professional tax passes through unmodified")
    void ptPassesThrough() {
        Money pt = Money.of("200.0000");
        assertThat(StatutoryLineRounder.roundPt(pt)).isEqualTo(Money.of("200.0000"));
        assertThat(StatutoryLineRounder.round("PROFESSIONAL_TAX", pt)).isEqualTo(Money.of("200.0000"));
    }
}
