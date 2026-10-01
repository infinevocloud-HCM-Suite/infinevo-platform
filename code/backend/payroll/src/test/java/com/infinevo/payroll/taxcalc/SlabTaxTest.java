package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.taxcalc.engine.SlabTax;
import com.infinevo.payroll.taxcalc.model.SlabTaxResult;
import com.infinevo.payroll.taxcalc.model.TaxSlabDetail;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SlabTax} progressive bracket calculation (W-33.1 spec ? 7).
 */
class SlabTaxTest {

    /**
     * FY 2025-2026 New Regime slabs from {@code reference.tax_slab_detail_history} (V005:76-82):
     * 1: 0 to 4,00,000 @ 0%
     * 2: 4,00,000 to 8,00,000 @ 5%
     * 3: 8,00,000 to 12,00,000 @ 10%
     * 4: 12,00,000 to 16,00,000 @ 15%
     * 5: 16,00,000 to 20,00,000 @ 20%
     * 6: 20,00,000 to 24,00,000 @ 25%
     * 7: 24,00,000 to NULL @ 30%
     */
    private static final List<TaxSlabDetail> NEW_REGIME_2025_2026_SLABS = List.of(
            new TaxSlabDetail(Money.of("0"), Money.of("400000"), BigDecimal.valueOf(0), 1),
            new TaxSlabDetail(Money.of("400000"), Money.of("800000"), BigDecimal.valueOf(5), 2),
            new TaxSlabDetail(Money.of("800000"), Money.of("1200000"), BigDecimal.valueOf(10), 3),
            new TaxSlabDetail(Money.of("1200000"), Money.of("1600000"), BigDecimal.valueOf(15), 4),
            new TaxSlabDetail(Money.of("1600000"), Money.of("2000000"), BigDecimal.valueOf(20), 5),
            new TaxSlabDetail(Money.of("2000000"), Money.of("2400000"), BigDecimal.valueOf(25), 6),
            new TaxSlabDetail(Money.of("2400000"), null, BigDecimal.valueOf(30), 7));

    @Test
    @DisplayName("FY 2025-2026 NEW on 10,00,000 -> 40,000 (0 + 20,000 + 20,000)")
    void calculatesTaxOn10Lakh() {
        Money income = Money.of("1000000");
        SlabTaxResult result = SlabTax.of(income, NEW_REGIME_2025_2026_SLABS);

        assertThat(result.totalTax()).isEqualTo(Money.of("40000"));
        assertThat(result.lines()).hasSize(7);

        // Slab 1: 0 to 4L @ 0% -> 4L taxable, 0 tax
        assertThat(result.lines().get(0).taxableAmount()).isEqualTo(Money.of("400000"));
        assertThat(result.lines().get(0).taxAmount()).isEqualTo(Money.ZERO);

        // Slab 2: 4L to 8L @ 5% -> 4L taxable, 20,000 tax
        assertThat(result.lines().get(1).taxableAmount()).isEqualTo(Money.of("400000"));
        assertThat(result.lines().get(1).taxAmount()).isEqualTo(Money.of("20000"));

        // Slab 3: 8L to 12L @ 10% -> 2L taxable, 20,000 tax
        assertThat(result.lines().get(2).taxableAmount()).isEqualTo(Money.of("200000"));
        assertThat(result.lines().get(2).taxAmount()).isEqualTo(Money.of("20000"));

        // Slab 4+: 0 taxable, 0 tax
        assertThat(result.lines().get(3).taxableAmount()).isEqualTo(Money.ZERO);
        assertThat(result.lines().get(3).taxAmount()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("FY 2025-2026 NEW on 15,00,000 -> 1,05,000 (0 + 20k + 40k + 45k)")
    void calculatesTaxOn15Lakh() {
        Money income = Money.of("1500000");
        SlabTaxResult result = SlabTax.of(income, NEW_REGIME_2025_2026_SLABS);

        // 0 + 20,000 (4L-8L) + 40,000 (8L-12L) + 45,000 (3L in 12L-16L @ 15%) = 1,05,000
        assertThat(result.totalTax()).isEqualTo(Money.of("105000"));
        assertThat(result.lines().get(3).taxableAmount()).isEqualTo(Money.of("300000"));
        assertThat(result.lines().get(3).taxAmount()).isEqualTo(Money.of("45000"));
    }

    @Test
    @DisplayName("FY 2025-2026 NEW on 3,99,999 -> 0 (below first taxable threshold)")
    void calculatesTaxOn399999() {
        Money income = Money.of("399999");
        SlabTaxResult result = SlabTax.of(income, NEW_REGIME_2025_2026_SLABS);

        assertThat(result.totalTax()).isEqualTo(Money.ZERO);
        assertThat(result.lines().get(0).taxableAmount()).isEqualTo(Money.of("399999"));
        assertThat(result.lines().get(0).taxAmount()).isEqualTo(Money.ZERO);
        assertThat(result.lines().get(1).taxableAmount()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("FY 2025-2026 NEW open-ended top bracket on 1,00,00,000 (1 crore)")
    void calculatesTaxOn1Crore() {
        Money income = Money.of("10000000");
        SlabTaxResult result = SlabTax.of(income, NEW_REGIME_2025_2026_SLABS);

        // Slabs 1-6 up to 24L:
        // 0 + 20,000 + 40,000 + 60,000 + 80,000 + 1,00,000 = 3,00,000
        // Slab 7 (24L to 1Cr = 76L @ 30%) = 22,80,000
        // Total = 25,80,000
        assertThat(result.totalTax()).isEqualTo(Money.of("2580000"));

        TaxSlabDetail topSlab = NEW_REGIME_2025_2026_SLABS.get(6);
        assertThat(topSlab.toAmount()).isNull();

        assertThat(result.lines().get(6).taxableAmount()).isEqualTo(Money.of("7600000"));
        assertThat(result.lines().get(6).taxAmount()).isEqualTo(Money.of("2280000"));
    }

    @Test
    @DisplayName("Zero or negative income returns ZERO tax")
    void handlesZeroOrNegativeIncome() {
        assertThat(SlabTax.of(Money.ZERO, NEW_REGIME_2025_2026_SLABS).totalTax())
                .isEqualTo(Money.ZERO);
        assertThat(SlabTax.of(Money.of("-50000"), NEW_REGIME_2025_2026_SLABS).totalTax())
                .isEqualTo(Money.ZERO);
    }
}
