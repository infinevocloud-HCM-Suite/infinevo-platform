package com.infinevo.payroll.taxcalc.reader;

import com.infinevo.payroll.taxcalc.AgeCategory;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxcalc.exception.TaxRulesMissingException;
import com.infinevo.payroll.taxcalc.model.TaxSlabDetail;
import com.infinevo.payroll.taxcalc.reader.model.CessSurchargeRule;
import com.infinevo.payroll.taxcalc.reader.model.Section87aRebateRule;
import com.infinevo.payroll.taxcalc.reader.model.StandardDeductionRule;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-only statutory tax rule reader over PostgreSQL {@code reference} tables (W-33.1 spec § 4).
 *
 * <p>Every query is scoped by financial year and regime. Throws {@link TaxRulesMissingException}
 * naming the exact missing table if statutory reference data has not been seeded for the year.
 */
@Component
public class TaxRuleReader {

    private final JdbcTemplate jdbcTemplate;

    public TaxRuleReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * Reads progressive tax slab brackets for the financial year, regime and age category.
     */
    public List<TaxSlabDetail> slabs(FinancialYear fy, TaxRegime regime, AgeCategory ageCategory) {
        Objects.requireNonNull(fy, "fy must not be null");
        Objects.requireNonNull(regime, "regime must not be null");
        Objects.requireNonNull(ageCategory, "ageCategory must not be null");

        // NEW regime has no age rows by design (V027:8); always uses GENERAL
        String effectiveAgeCategory = (regime == TaxRegime.NEW) ? AgeCategory.GENERAL.name() : ageCategory.name();

        String masterSql =
                """
                SELECT id
                  FROM reference.tax_slab_master
                 WHERE financial_year = ? AND regime = ? AND age_category = ?
                """;

        List<UUID> masterIds = jdbcTemplate.query(
                masterSql, (rs, rowNum) -> (UUID) rs.getObject("id"), fy.label(), regime.name(), effectiveAgeCategory);

        if (masterIds.size() != 1) {
            throw new TaxRulesMissingException("reference.tax_slab_master", fy.label());
        }

        UUID masterId = masterIds.get(0);

        String detailSql =
                """
                SELECT from_amount, to_amount, tax_rate_percent, slab_order
                  FROM reference.tax_slab_detail_history
                 WHERE slab_master_id = ?
                 ORDER BY slab_order ASC
                """;

        List<TaxSlabDetail> details = jdbcTemplate.query(
                detailSql,
                (rs, rowNum) -> {
                    BigDecimal fromRaw = rs.getBigDecimal("from_amount");
                    BigDecimal toRaw = rs.getBigDecimal("to_amount");
                    BigDecimal rate = rs.getBigDecimal("tax_rate_percent");
                    int order = rs.getInt("slab_order");

                    Money from = Money.of(fromRaw);
                    Money to = toRaw != null ? Money.of(toRaw) : null;
                    return new TaxSlabDetail(from, to, rate, order);
                },
                masterId);

        if (details.isEmpty()) {
            throw new TaxRulesMissingException("reference.tax_slab_detail_history", fy.label());
        }

        return details;
    }

    /**
     * Reads statutory standard deduction rule from {@code reference.standard_deduction_rule_master}.
     */
    public StandardDeductionRule standardDeduction(FinancialYear fy, TaxRegime regime) {
        Objects.requireNonNull(fy, "fy must not be null");
        Objects.requireNonNull(regime, "regime must not be null");

        String sql =
                """
                SELECT amount, description
                  FROM reference.standard_deduction_rule_master
                 WHERE financial_year = ? AND regime = ? AND is_active = true
                 LIMIT 1
                """;

        List<StandardDeductionRule> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    BigDecimal amountRaw = rs.getBigDecimal("amount");
                    String desc = rs.getString("description");
                    return new StandardDeductionRule(fy.label(), regime.name(), Money.of(amountRaw), desc);
                },
                fy.label(),
                regime.name());

        if (results.isEmpty()) {
            throw new TaxRulesMissingException("reference.standard_deduction_rule_master", fy.label());
        }

        return results.get(0);
    }

    /**
     * Reads statutory Section 87A rebate rule from {@code reference.section87a_rebate_rule_master}.
     */
    public Section87aRebateRule rebate(FinancialYear fy, TaxRegime regime) {
        Objects.requireNonNull(fy, "fy must not be null");
        Objects.requireNonNull(regime, "regime must not be null");

        String sql =
                """
                SELECT income_threshold, max_rebate_amount, is_full_rebate, remarks
                  FROM reference.section87a_rebate_rule_master
                 WHERE financial_year = ? AND regime = ? AND is_active = true
                 LIMIT 1
                """;

        List<Section87aRebateRule> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    BigDecimal thresholdRaw = rs.getBigDecimal("income_threshold");
                    BigDecimal maxRebateRaw = rs.getBigDecimal("max_rebate_amount");
                    boolean isFull = rs.getBoolean("is_full_rebate");
                    String remarks = rs.getString("remarks");
                    return new Section87aRebateRule(
                            fy.label(), regime.name(), Money.of(thresholdRaw), Money.of(maxRebateRaw), isFull, remarks);
                },
                fy.label(),
                regime.name());

        if (results.isEmpty()) {
            throw new TaxRulesMissingException("reference.section87a_rebate_rule_master", fy.label());
        }

        return results.get(0);
    }

    /**
     * Reads statutory high-income surcharge bands from {@code reference.cess_surcharge_rule_master}.
     */
    public List<CessSurchargeRule> surchargeBands(FinancialYear fy, TaxRegime regime) {
        Objects.requireNonNull(fy, "fy must not be null");
        Objects.requireNonNull(regime, "regime must not be null");

        String sql =
                """
                SELECT rule_type, tax_regime, income_from, income_to, rate, is_marginal_relief_applicable, remarks
                  FROM reference.cess_surcharge_rule_master
                 WHERE financial_year = ?
                   AND rule_type = 'SURCHARGE'
                   AND tax_regime IN (?, 'BOTH')
                   AND is_active = true
                 ORDER BY income_from ASC
                """;

        List<CessSurchargeRule> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    String ruleType = rs.getString("rule_type");
                    String taxRegime = rs.getString("tax_regime");
                    BigDecimal fromRaw = rs.getBigDecimal("income_from");
                    BigDecimal toRaw = rs.getBigDecimal("income_to");
                    BigDecimal rate = rs.getBigDecimal("rate");
                    boolean marginalRelief = rs.getBoolean("is_marginal_relief_applicable");
                    String remarks = rs.getString("remarks");

                    Money from = fromRaw != null ? Money.of(fromRaw) : null;
                    Money to = toRaw != null ? Money.of(toRaw) : null;

                    return new CessSurchargeRule(
                            fy.label(), ruleType, taxRegime, from, to, rate, marginalRelief, remarks);
                },
                fy.label(),
                regime.name());

        if (results.isEmpty()) {
            throw new TaxRulesMissingException("reference.cess_surcharge_rule_master", fy.label());
        }

        return results;
    }

    /**
     * Reads statutory health and education cess from {@code reference.cess_surcharge_rule_master}.
     */
    public CessSurchargeRule cess(FinancialYear fy, TaxRegime regime) {
        Objects.requireNonNull(fy, "fy must not be null");
        Objects.requireNonNull(regime, "regime must not be null");

        String sql =
                """
                SELECT rule_type, tax_regime, rate, remarks
                  FROM reference.cess_surcharge_rule_master
                 WHERE financial_year = ?
                   AND rule_type = 'CESS'
                   AND tax_regime IN (?, 'BOTH')
                   AND is_active = true
                 LIMIT 1
                """;

        List<CessSurchargeRule> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    String ruleType = rs.getString("rule_type");
                    String taxRegime = rs.getString("tax_regime");
                    BigDecimal rate = rs.getBigDecimal("rate");
                    String remarks = rs.getString("remarks");

                    return new CessSurchargeRule(fy.label(), ruleType, taxRegime, null, null, rate, false, remarks);
                },
                fy.label(),
                regime.name());

        if (results.isEmpty()) {
            throw new TaxRulesMissingException("reference.cess_surcharge_rule_master", fy.label());
        }

        return results.get(0);
    }
}
