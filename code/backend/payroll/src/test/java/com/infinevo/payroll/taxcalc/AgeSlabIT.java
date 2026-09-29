package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.taxcalc.model.TaxSlabDetail;
import com.infinevo.payroll.taxcalc.reader.TaxRuleReader;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test validating statutory age category slabs read from PostgreSQL reference tables (W-33.2 spec § 7).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class AgeSlabIT extends AbstractIntegrationTest {

    @Autowired
    private TaxRuleReader taxRuleReader;

    private final FinancialYear fy2025_2026 = FinancialYear.parse("2025-2026");

    @Test
    @DisplayName("GENERAL age category has first bracket to_amount of 2,50,000")
    void generalOldSlabsFirstBand250k() {
        List<TaxSlabDetail> slabs = taxRuleReader.slabs(fy2025_2026, TaxRegime.OLD, AgeCategory.GENERAL);

        assertThat(slabs).hasSize(4);
        assertThat(slabs.get(0).fromAmount()).isEqualTo(Money.of("0"));
        assertThat(slabs.get(0).toAmount()).isEqualTo(Money.of("250000"));
        assertThat(slabs.get(0).taxRatePercent()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("SENIOR age category has first bracket to_amount of 3,00,000")
    void seniorOldSlabsFirstBand300k() {
        List<TaxSlabDetail> slabs = taxRuleReader.slabs(fy2025_2026, TaxRegime.OLD, AgeCategory.SENIOR);

        assertThat(slabs).hasSize(4);
        assertThat(slabs.get(0).fromAmount()).isEqualTo(Money.of("0"));
        assertThat(slabs.get(0).toAmount()).isEqualTo(Money.of("300000"));
        assertThat(slabs.get(0).taxRatePercent()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("SUPER_SENIOR age category has first bracket to_amount of 5,00,000 and 3 bands")
    void superSeniorOldSlabsFirstBand500k() {
        List<TaxSlabDetail> slabs = taxRuleReader.slabs(fy2025_2026, TaxRegime.OLD, AgeCategory.SUPER_SENIOR);

        assertThat(slabs).hasSize(3);
        assertThat(slabs.get(0).fromAmount()).isEqualTo(Money.of("0"));
        assertThat(slabs.get(0).toAmount()).isEqualTo(Money.of("500000"));
        assertThat(slabs.get(0).taxRatePercent()).isEqualByComparingTo("0.00");
    }
}
