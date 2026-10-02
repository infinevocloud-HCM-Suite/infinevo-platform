package com.infinevo.payroll.taxdeclaration.housing;

import com.infinevo.payroll.taxdeclaration.exception.ReferenceDataMissingException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reader for statutory Section 10(13A) HRA rules from {@code reference.hra_rule_master} (W-32.2).
 */
@Component
public class HraRuleReader {

    private final JdbcTemplate jdbcTemplate;

    public HraRuleReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The annual rent above which a landlord PAN is mandatory, as the house-rent save validates it.
     *
     * @throws ReferenceDataMissingException when no active rule row exists for the year
     */
    public BigDecimal getPanMandatoryThreshold(String financialYear, String taxRegime) {
        BigDecimal threshold = findPanMandatoryThreshold(financialYear, taxRegime);
        if (threshold == null) {
            throw new ReferenceDataMissingException("reference.hra_rule_master", financialYear, taxRegime);
        }
        return threshold;
    }

    /**
     * Same lookup as {@link #getPanMandatoryThreshold}, but {@code null} when no active rule row exists — for
     * the declaration header, which tells the screen the threshold the server will enforce (W-47.3).
     */
    public BigDecimal findPanMandatoryThreshold(String financialYear, String taxRegime) {
        if (financialYear == null || taxRegime == null) {
            return null;
        }
        String sql =
                """
                SELECT pan_mandatory_threshold
                  FROM reference.hra_rule_master
                 WHERE financial_year = ? AND is_active = true
                 ORDER BY CASE WHEN tax_regime = ? THEN 0 ELSE 1 END
                 LIMIT 1
                """;
        // The rule is statutory and the seed carries it under OLD only; a NEW-regime header must
        // still find it, so the regime orders the rows rather than filtering them.
        List<BigDecimal> results =
                jdbcTemplate.query(sql, (rs, rowNum) -> rs.getBigDecimal(1), financialYear, taxRegime);
        return results.isEmpty() ? null : results.get(0);
    }
}
