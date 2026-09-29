package com.infinevo.payroll.statutory.pt;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PtSlabMatcherTest {

    @Test
    @DisplayName("Karnataka slabs from worked table in §8")
    void karnatakaWorkedTable() {
        // Karnataka statutory slabs: 0 to 24999 -> 0, 24999 to null -> 200
        List<PtSlabDto> slabs = List.of(
                new PtSlabDto(
                        new BigDecimal("0.0000"), new BigDecimal("24999.0000"), new BigDecimal("0.0000"), false, null),
                new PtSlabDto(new BigDecimal("24999.0000"), null, new BigDecimal("200.0000"), false, null));

        // 24,999.0000 | July | any -> 0
        Money pt1 = PtSlabMatcher.match(Money.of("24999.0000"), "male", LocalDate.of(2025, 7, 31), slabs);
        assertThat(pt1).isEqualTo(Money.ZERO);

        // 25,000.0000 | July | male -> 200
        Money pt2 = PtSlabMatcher.match(Money.of("25000.0000"), "male", LocalDate.of(2025, 7, 31), slabs);
        assertThat(pt2).isEqualTo(Money.of("200.0000"));

        // 60,000.0000 | February | female -> 200 (no female exemption in Karnataka)
        Money pt3 = PtSlabMatcher.match(Money.of("60000.0000"), "female", LocalDate.of(2025, 2, 28), slabs);
        assertThat(pt3).isEqualTo(Money.of("200.0000"));
    }

    @Test
    @DisplayName("Maharashtra check in §8 with female exemption and February balancing")
    void maharashtraWorkedCheck() {
        // Maharashtra seeded slabs from V064 (Schedule I of Maharashtra Act):
        // 0 to 7500 -> 0 (all months)
        // 7500 to 10000 -> 175, female exempt (all months)
        // 10000 to 25000 -> 200, female exempt (months 1, 3..12)
        // 10000 to 25000 -> 300, female exempt (month 2)
        // 25000 to null -> 200, not female exempt (months 1, 3..12)
        // 25000 to null -> 300, not female exempt (month 2)
        List<PtSlabDto> slabs = List.of(
                new PtSlabDto(
                        new BigDecimal("0.0000"), new BigDecimal("7500.0000"), new BigDecimal("0.0000"), false, null),
                new PtSlabDto(
                        new BigDecimal("7500.0000"),
                        new BigDecimal("10000.0000"),
                        new BigDecimal("175.0000"),
                        true, // is_female_exempt
                        null),
                new PtSlabDto(
                        new BigDecimal("10000.0000"),
                        new BigDecimal("25000.0000"),
                        new BigDecimal("200.0000"),
                        true, // is_female_exempt
                        List.of(1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)),
                new PtSlabDto(
                        new BigDecimal("10000.0000"),
                        new BigDecimal("25000.0000"),
                        new BigDecimal("300.0000"),
                        true, // is_female_exempt
                        List.of(2)),
                new PtSlabDto(
                        new BigDecimal("25000.0000"),
                        null,
                        new BigDecimal("200.0000"),
                        false, // not female exempt above 25k
                        List.of(1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)),
                new PtSlabDto(
                        new BigDecimal("25000.0000"),
                        null,
                        new BigDecimal("300.0000"),
                        false, // not female exempt above 25k
                        List.of(2)));

        // 24,000 female in March -> 0 (is_female_exempt in 10000-25000 slab)
        Money female24k = PtSlabMatcher.match(Money.of("24000.0000"), "female", LocalDate.of(2025, 3, 31), slabs);
        assertThat(female24k).isEqualTo(Money.ZERO);

        // 24,000 male in March -> 200 (in 10000-25000 slab, non-Feb)
        Money male24k = PtSlabMatcher.match(Money.of("24000.0000"), "male", LocalDate.of(2025, 3, 31), slabs);
        assertThat(male24k).isEqualTo(Money.of("200.0000"));

        // 8,000 male in March -> 175 (in 7500-10000 slab)
        Money male8k = PtSlabMatcher.match(Money.of("8000.0000"), "male", LocalDate.of(2025, 3, 31), slabs);
        assertThat(male8k).isEqualTo(Money.of("175.0000"));

        // 7,500 male -> 0 (boundary lands in lower slab 0-7500)
        Money male7500 = PtSlabMatcher.match(Money.of("7500.0000"), "male", LocalDate.of(2025, 3, 31), slabs);
        assertThat(male7500).isEqualTo(Money.ZERO);

        // 30,000 male in February -> 300 (in 25000+ slab, Feb balancing)
        Money male30kFeb = PtSlabMatcher.match(Money.of("30000.0000"), "male", LocalDate.of(2025, 2, 28), slabs);
        assertThat(male30kFeb).isEqualTo(Money.of("300.0000"));

        // 30,000 male in March -> 200 (in 25000+ slab, non-Feb)
        Money male30kMar = PtSlabMatcher.match(Money.of("30000.0000"), "male", LocalDate.of(2025, 3, 31), slabs);
        assertThat(male30kMar).isEqualTo(Money.of("200.0000"));

        // 30,000 female in March -> 200 (above 25,000 female is not exempt)
        Money female30kMar = PtSlabMatcher.match(Money.of("30000.0000"), "female", LocalDate.of(2025, 3, 31), slabs);
        assertThat(female30kMar).isEqualTo(Money.of("200.0000"));
    }

    @Test
    @DisplayName("Open-ended last slab covers high gross salaries")
    void openEndedLastSlab() {
        List<PtSlabDto> slabs = List.of(
                new PtSlabDto(
                        new BigDecimal("0.0000"), new BigDecimal("10000.0000"), new BigDecimal("0.0000"), false, null),
                new PtSlabDto(new BigDecimal("10000.0000"), null, new BigDecimal("200.0000"), false, null));

        Money pt = PtSlabMatcher.match(Money.of("1000000.0000"), "any", LocalDate.of(2025, 5, 31), slabs);
        assertThat(pt).isEqualTo(Money.of("200.0000"));
    }

    @Test
    @DisplayName("deduction_months '3,9' pays in March and September only")
    void halfYearlyDeductionMonths() {
        List<PtSlabDto> slabs = List.of(
                new PtSlabDto(
                        new BigDecimal("0.0000"),
                        new BigDecimal("20000.0000"),
                        new BigDecimal("0.0000"),
                        false,
                        List.of(3, 9)),
                new PtSlabDto(new BigDecimal("20000.0000"), null, new BigDecimal("1250.0000"), false, List.of(3, 9)));

        // March (month 3) -> pays 1250
        Money march = PtSlabMatcher.match(Money.of("50000.0000"), "male", LocalDate.of(2025, 3, 31), slabs);
        assertThat(march).isEqualTo(Money.of("1250.0000"));

        // September (month 9) -> pays 1250
        Money sept = PtSlabMatcher.match(Money.of("50000.0000"), "male", LocalDate.of(2025, 9, 30), slabs);
        assertThat(sept).isEqualTo(Money.of("1250.0000"));

        // July (month 7) -> no deduction
        Money july = PtSlabMatcher.match(Money.of("50000.0000"), "male", LocalDate.of(2025, 7, 31), slabs);
        assertThat(july).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("Female exempt on flagged slab, not on unflagged one")
    void femaleExemptFlag() {
        List<PtSlabDto> slabs = List.of(
                new PtSlabDto(
                        new BigDecimal("0.0000"),
                        new BigDecimal("10000.0000"),
                        new BigDecimal("100.0000"),
                        true, // female exempt
                        null),
                new PtSlabDto(
                        new BigDecimal("10000.0000"),
                        null,
                        new BigDecimal("200.0000"),
                        false, // NOT female exempt
                        null));

        // In lower slab: female is exempt -> 0
        Money femaleLow = PtSlabMatcher.match(Money.of("8000.0000"), "female", LocalDate.of(2025, 4, 30), slabs);
        assertThat(femaleLow).isEqualTo(Money.ZERO);

        // In lower slab: male is not exempt -> 100
        Money maleLow = PtSlabMatcher.match(Money.of("8000.0000"), "male", LocalDate.of(2025, 4, 30), slabs);
        assertThat(maleLow).isEqualTo(Money.of("100.0000"));

        // In upper slab: female is NOT exempt -> 200
        Money femaleHigh = PtSlabMatcher.match(Money.of("15000.0000"), "female", LocalDate.of(2025, 4, 30), slabs);
        assertThat(femaleHigh).isEqualTo(Money.of("200.0000"));
    }

    @Test
    @DisplayName("Gross exactly on a boundary lands in the lower slab")
    void boundaryLandsInLowerSlab() {
        List<PtSlabDto> slabs = List.of(
                new PtSlabDto(
                        new BigDecimal("0.0000"), new BigDecimal("10000.0000"), new BigDecimal("50.0000"), false, null),
                new PtSlabDto(
                        new BigDecimal("10000.0000"),
                        new BigDecimal("20000.0000"),
                        new BigDecimal("150.0000"),
                        false,
                        null),
                new PtSlabDto(new BigDecimal("20000.0000"), null, new BigDecimal("200.0000"), false, null));

        // Exactly 10,000.0000 lands in slab 1 (50.0000)
        Money at10k = PtSlabMatcher.match(Money.of("10000.0000"), "male", LocalDate.of(2025, 6, 30), slabs);
        assertThat(at10k).isEqualTo(Money.of("50.0000"));

        // Exactly 20,000.0000 lands in slab 2 (150.0000)
        Money at20k = PtSlabMatcher.match(Money.of("20000.0000"), "male", LocalDate.of(2025, 6, 30), slabs);
        assertThat(at20k).isEqualTo(Money.of("150.0000"));

        // Above 20,000.0000 lands in slab 3 (200.0000)
        Money at20001 = PtSlabMatcher.match(Money.of("20001.0000"), "male", LocalDate.of(2025, 6, 30), slabs);
        assertThat(at20001).isEqualTo(Money.of("200.0000"));
    }
}
