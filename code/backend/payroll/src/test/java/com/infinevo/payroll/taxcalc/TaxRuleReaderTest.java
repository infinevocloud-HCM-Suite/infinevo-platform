package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.infinevo.payroll.taxcalc.exception.TaxRulesMissingException;
import com.infinevo.payroll.taxcalc.model.TaxSlabDetail;
import com.infinevo.payroll.taxcalc.reader.TaxRuleReader;
import com.infinevo.payroll.taxcalc.reader.model.CessSurchargeRule;
import com.infinevo.payroll.taxcalc.reader.model.Section87aRebateRule;
import com.infinevo.payroll.taxcalc.reader.model.StandardDeductionRule;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Unit tests for {@link TaxRuleReader} (W-33.1 spec ? 4).
 */
class TaxRuleReaderTest {

    private static final FinancialYear FY_2025_2026 = FinancialYear.parse("2025-2026");

    private JdbcTemplate jdbcTemplate;
    private TaxRuleReader reader;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        reader = new TaxRuleReader(jdbcTemplate);
    }

    @Test
    @DisplayName("slabs throws TaxRulesMissingException naming reference.tax_slab_master when no master row exists")
    void slabsThrowsWhenMasterMissing() {
        given(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("2025-2026"), eq("NEW"), eq("GENERAL")))
                .willReturn(List.of());

        assertThatThrownBy(() -> reader.slabs(FY_2025_2026, TaxRegime.NEW, AgeCategory.GENERAL))
                .isInstanceOf(TaxRulesMissingException.class)
                .hasMessageContaining("reference.tax_slab_master")
                .hasMessageContaining("2025-2026");
    }

    @Test
    @DisplayName("slabs maps details correctly when master and detail rows exist")
    void slabsMapsDetails() {
        UUID masterId = UUID.randomUUID();
        given(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("2025-2026"), eq("NEW"), eq("GENERAL")))
                .willReturn(List.of(masterId));

        List<TaxSlabDetail> mockDetails = List.of(
                new TaxSlabDetail(Money.of("0"), Money.of("400000"), BigDecimal.valueOf(0), 1),
                new TaxSlabDetail(Money.of("400000"), null, BigDecimal.valueOf(30), 2));

        given(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(masterId)))
                .willReturn(mockDetails);

        List<TaxSlabDetail> result = reader.slabs(FY_2025_2026, TaxRegime.NEW, AgeCategory.GENERAL);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).fromAmount()).isEqualTo(Money.of("0"));
        assertThat(result.get(1).toAmount()).isNull();
    }

    @Test
    @DisplayName("standardDeduction throws TaxRulesMissingException when row missing")
    void standardDeductionThrowsWhenMissing() {
        given(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("2025-2026"), eq("NEW")))
                .willReturn(List.of());

        assertThatThrownBy(() -> reader.standardDeduction(FY_2025_2026, TaxRegime.NEW))
                .isInstanceOf(TaxRulesMissingException.class)
                .hasMessageContaining("reference.standard_deduction_rule_master")
                .hasMessageContaining("2025-2026");
    }

    @Test
    @DisplayName("standardDeduction maps rule correctly")
    void standardDeductionMapsCorrectly() {
        StandardDeductionRule expected =
                new StandardDeductionRule("2025-2026", "NEW", Money.of("75000"), "Standard deduction");
        given(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("2025-2026"), eq("NEW")))
                .willReturn(List.of(expected));

        StandardDeductionRule rule = reader.standardDeduction(FY_2025_2026, TaxRegime.NEW);
        assertThat(rule.amount()).isEqualTo(Money.of("75000"));
    }

    @Test
    @DisplayName("rebate throws TaxRulesMissingException when row missing")
    void rebateThrowsWhenMissing() {
        given(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("2025-2026"), eq("NEW")))
                .willReturn(List.of());

        assertThatThrownBy(() -> reader.rebate(FY_2025_2026, TaxRegime.NEW))
                .isInstanceOf(TaxRulesMissingException.class)
                .hasMessageContaining("reference.section87a_rebate_rule_master");
    }

    @Test
    @DisplayName("rebate maps rule correctly")
    void rebateMapsCorrectly() {
        Section87aRebateRule expected =
                new Section87aRebateRule("2025-2026", "NEW", Money.of("1200000"), Money.of("60000"), true, "Rebate");
        given(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("2025-2026"), eq("NEW")))
                .willReturn(List.of(expected));

        Section87aRebateRule rule = reader.rebate(FY_2025_2026, TaxRegime.NEW);
        assertThat(rule.incomeThreshold()).isEqualTo(Money.of("1200000"));
        assertThat(rule.isFullRebate()).isTrue();
    }

    @Test
    @DisplayName("surchargeBands throws TaxRulesMissingException when list empty")
    void surchargeBandsThrowsWhenEmpty() {
        given(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("2025-2026"), eq("NEW")))
                .willReturn(List.of());

        assertThatThrownBy(() -> reader.surchargeBands(FY_2025_2026, TaxRegime.NEW))
                .isInstanceOf(TaxRulesMissingException.class)
                .hasMessageContaining("reference.cess_surcharge_rule_master");
    }

    @Test
    @DisplayName("cess maps rule correctly")
    void cessMapsCorrectly() {
        CessSurchargeRule expected =
                new CessSurchargeRule("2025-2026", "CESS", "BOTH", null, null, BigDecimal.valueOf(4.00), false, "Cess");
        given(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("2025-2026"), eq("NEW")))
                .willReturn(List.of(expected));

        CessSurchargeRule rule = reader.cess(FY_2025_2026, TaxRegime.NEW);
        assertThat(rule.rate()).isEqualByComparingTo("4.00");
    }

    @Test
    @DisplayName("hra maps rule correctly when row exists")
    void hraMapsCorrectly() {
        com.infinevo.payroll.taxcalc.reader.model.HraRule expected =
                new com.infinevo.payroll.taxcalc.reader.model.HraRule(
                        "2025-2026", BigDecimal.valueOf(10), BigDecimal.valueOf(50), BigDecimal.valueOf(40));
        given(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("2025-2026")))
                .willReturn(List.of(expected));

        com.infinevo.payroll.taxcalc.reader.model.HraRule rule = reader.hra(FY_2025_2026);
        assertThat(rule.basicDaPercentThreshold()).isEqualByComparingTo("10");
        assertThat(rule.metroPercent()).isEqualByComparingTo("50");
        assertThat(rule.nonMetroPercent()).isEqualByComparingTo("40");
    }

    @Test
    @DisplayName("homeLoan throws when row missing")
    void homeLoanThrowsWhenMissing() {
        given(jdbcTemplate.query(
                        anyString(),
                        any(RowMapper.class),
                        eq("2025-2026"),
                        eq("24B"),
                        eq("INTEREST"),
                        eq("SELF_OCCUPIED")))
                .willReturn(List.of());

        assertThatThrownBy(() -> reader.homeLoan(FY_2025_2026, "24B", "INTEREST", "SELF_OCCUPIED"))
                .isInstanceOf(TaxRulesMissingException.class)
                .hasMessageContaining("reference.home_loan_rule_master");
    }

    @Test
    @DisplayName("letOut maps rule correctly")
    void letOutMapsCorrectly() {
        com.infinevo.payroll.taxcalc.reader.model.LetOutRule expected =
                new com.infinevo.payroll.taxcalc.reader.model.LetOutRule(
                        "2025-2026", "OLD", BigDecimal.valueOf(30), Money.of("200000"), true, true);
        given(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("2025-2026")))
                .willReturn(List.of(expected));

        com.infinevo.payroll.taxcalc.reader.model.LetOutRule rule = reader.letOut(FY_2025_2026);
        assertThat(rule.standardDeductionPercent()).isEqualByComparingTo("30");
        assertThat(rule.maxLossSetoffLimit()).isEqualTo(Money.of("200000"));
    }

    @Test
    @DisplayName("otherIncomeRule maps rule correctly")
    void otherIncomeRuleMapsCorrectly() {
        com.infinevo.payroll.taxcalc.reader.model.OtherIncomeRule expected =
                new com.infinevo.payroll.taxcalc.reader.model.OtherIncomeRule(
                        "2025-2026",
                        "80TTA",
                        "Interest on savings",
                        "DEDUCTION",
                        "OLD",
                        Money.of("10000"),
                        BigDecimal.valueOf(100),
                        false,
                        false);
        given(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("2025-2026"), eq("80TTA")))
                .willReturn(List.of(expected));

        com.infinevo.payroll.taxcalc.reader.model.OtherIncomeRule rule = reader.otherIncomeRule(FY_2025_2026, "80TTA");
        assertThat(rule.maxLimit()).isEqualTo(Money.of("10000"));
    }
}
