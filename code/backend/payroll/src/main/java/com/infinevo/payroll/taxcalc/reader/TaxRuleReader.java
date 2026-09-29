package com.infinevo.payroll.taxcalc.reader;

import com.infinevo.payroll.taxcalc.AgeCategory;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxcalc.exception.TaxRulesMissingException;
import com.infinevo.payroll.taxcalc.model.TaxSlabDetail;
import com.infinevo.payroll.taxcalc.reader.model.CessSurchargeRule;
import com.infinevo.payroll.taxcalc.reader.model.HomeLoanRule;
import com.infinevo.payroll.taxcalc.reader.model.HraRule;
import com.infinevo.payroll.taxcalc.reader.model.LetOutRule;
import com.infinevo.payroll.taxcalc.reader.model.OtherIncomeRule;
import com.infinevo.payroll.taxcalc.reader.model.Section87aRebateRule;
import com.infinevo.payroll.taxcalc.reader.model.StandardDeductionRule;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-only statutory tax rule reader over PostgreSQL {@code reference} tables (W-33.1 spec § 4, W-33.2 spec § 4).
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

    /**
     * Reads statutory HRA exemption rule from {@code reference.hra_rule_master} for the old regime.
     */
    public HraRule hra(FinancialYear fy) {
        Objects.requireNonNull(fy, "fy must not be null");

        String sql =
                """
                SELECT basic_da_percent_threshold, metro_percent, non_metro_percent
                  FROM reference.hra_rule_master
                 WHERE financial_year = ? AND tax_regime = 'OLD' AND is_active = true
                 LIMIT 1
                """;

        List<HraRule> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new HraRule(
                        fy.label(),
                        rs.getBigDecimal("basic_da_percent_threshold"),
                        rs.getBigDecimal("metro_percent"),
                        rs.getBigDecimal("non_metro_percent")),
                fy.label());

        if (results.isEmpty()) {
            throw new TaxRulesMissingException("reference.hra_rule_master", fy.label());
        }

        return results.get(0);
    }

    /**
     * Reads a specific statutory home loan rule from {@code reference.home_loan_rule_master}.
     */
    public HomeLoanRule homeLoan(FinancialYear fy, String sectionCode, String component, String propertyType) {
        return findHomeLoan(fy, sectionCode, component, propertyType)
                .orElseThrow(() -> new TaxRulesMissingException("reference.home_loan_rule_master", fy.label()));
    }

    /**
     * Finds an optional statutory home loan rule from {@code reference.home_loan_rule_master}.
     */
    public Optional<HomeLoanRule> findHomeLoan(
            FinancialYear fy, String sectionCode, String component, String propertyType) {
        Objects.requireNonNull(fy, "fy must not be null");
        Objects.requireNonNull(sectionCode, "sectionCode must not be null");
        Objects.requireNonNull(component, "component must not be null");
        Objects.requireNonNull(propertyType, "propertyType must not be null");

        String sql =
                """
                SELECT section_name, max_limit, loan_sanction_from, loan_sanction_to, is_first_time_buyer
                  FROM reference.home_loan_rule_master
                 WHERE financial_year = ?
                   AND section_code = ?
                   AND component = ?
                   AND property_type = ?
                   AND is_active = true
                 LIMIT 1
                """;

        List<HomeLoanRule> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    String sectionName = rs.getString("section_name");
                    BigDecimal maxLimitRaw = rs.getBigDecimal("max_limit");
                    LocalDate sanctionFrom = rs.getObject("loan_sanction_from", LocalDate.class);
                    LocalDate sanctionTo = rs.getObject("loan_sanction_to", LocalDate.class);
                    boolean firstTimeBuyer = rs.getBoolean("is_first_time_buyer");
                    Money maxLimit = maxLimitRaw != null ? Money.of(maxLimitRaw) : null;

                    return new HomeLoanRule(
                            fy.label(),
                            sectionCode,
                            sectionName,
                            component,
                            propertyType,
                            maxLimit,
                            sanctionFrom,
                            sanctionTo,
                            firstTimeBuyer);
                },
                fy.label(),
                sectionCode,
                component,
                propertyType);

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Reads all active statutory home loan rules from {@code reference.home_loan_rule_master} for the year.
     */
    public List<HomeLoanRule> homeLoanRules(FinancialYear fy) {
        Objects.requireNonNull(fy, "fy must not be null");

        String sql =
                """
                SELECT section_code, section_name, component, property_type, max_limit,
                       loan_sanction_from, loan_sanction_to, is_first_time_buyer
                  FROM reference.home_loan_rule_master
                 WHERE financial_year = ? AND is_active = true
                 ORDER BY section_code ASC
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    String sectionCode = rs.getString("section_code");
                    String sectionName = rs.getString("section_name");
                    String component = rs.getString("component");
                    String propertyType = rs.getString("property_type");
                    BigDecimal maxLimitRaw = rs.getBigDecimal("max_limit");
                    LocalDate sanctionFrom = rs.getObject("loan_sanction_from", LocalDate.class);
                    LocalDate sanctionTo = rs.getObject("loan_sanction_to", LocalDate.class);
                    boolean firstTimeBuyer = rs.getBoolean("is_first_time_buyer");
                    Money maxLimit = maxLimitRaw != null ? Money.of(maxLimitRaw) : null;

                    return new HomeLoanRule(
                            fy.label(),
                            sectionCode,
                            sectionName,
                            component,
                            propertyType,
                            maxLimit,
                            sanctionFrom,
                            sanctionTo,
                            firstTimeBuyer);
                },
                fy.label());
    }

    /**
     * Reads statutory let-out property rules from {@code reference.let_out_property_rule_master}.
     */
    public LetOutRule letOut(FinancialYear fy) {
        Objects.requireNonNull(fy, "fy must not be null");

        String sql =
                """
                SELECT standard_deduction_percent, max_loss_setoff_limit,
                       is_home_loan_interest_allowed, is_loss_carry_forward_allowed
                  FROM reference.let_out_property_rule_master
                 WHERE financial_year = ? AND tax_regime = 'OLD' AND is_active = true
                 LIMIT 1
                """;

        List<LetOutRule> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    BigDecimal stdDed = rs.getBigDecimal("standard_deduction_percent");
                    BigDecimal lossCapRaw = rs.getBigDecimal("max_loss_setoff_limit");
                    boolean interestAllowed = rs.getBoolean("is_home_loan_interest_allowed");
                    boolean lossCarryAllowed = rs.getBoolean("is_loss_carry_forward_allowed");

                    return new LetOutRule(
                            fy.label(), "OLD", stdDed, Money.of(lossCapRaw), interestAllowed, lossCarryAllowed);
                },
                fy.label());

        if (results.isEmpty()) {
            throw new TaxRulesMissingException("reference.let_out_property_rule_master", fy.label());
        }

        return results.get(0);
    }

    /**
     * Reads statutory other income deduction rule from {@code reference.other_income_rule_master}.
     */
    public OtherIncomeRule otherIncomeRule(FinancialYear fy, String sectionCode) {
        Objects.requireNonNull(fy, "fy must not be null");
        Objects.requireNonNull(sectionCode, "sectionCode must not be null");

        String sql =
                """
                SELECT section_name, rule_type, tax_regime, max_limit, deduction_percent,
                       is_proof_required, is_conditional
                  FROM reference.other_income_rule_master
                 WHERE financial_year = ? AND section_code = ? AND is_active = true
                 LIMIT 1
                """;

        List<OtherIncomeRule> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    String sectionName = rs.getString("section_name");
                    String ruleType = rs.getString("rule_type");
                    String taxRegime = rs.getString("tax_regime");
                    BigDecimal maxLimitRaw = rs.getBigDecimal("max_limit");
                    BigDecimal deductionPercent = rs.getBigDecimal("deduction_percent");
                    boolean isProofRequired = rs.getBoolean("is_proof_required");
                    boolean isConditional = rs.getBoolean("is_conditional");
                    Money maxLimit = maxLimitRaw != null ? Money.of(maxLimitRaw) : null;

                    return new OtherIncomeRule(
                            fy.label(),
                            sectionCode,
                            sectionName,
                            ruleType,
                            taxRegime,
                            maxLimit,
                            deductionPercent,
                            isProofRequired,
                            isConditional);
                },
                fy.label(),
                sectionCode);

        if (results.isEmpty()) {
            throw new TaxRulesMissingException("reference.other_income_rule_master", fy.label());
        }

        return results.get(0);
    }
}
