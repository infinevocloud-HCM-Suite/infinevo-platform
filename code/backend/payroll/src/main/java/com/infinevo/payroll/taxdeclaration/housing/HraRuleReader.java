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

    public BigDecimal getPanMandatoryThreshold(String financialYear, String taxRegime) {
        if (financialYear == null || taxRegime == null) {
            throw new ReferenceDataMissingException("reference.hra_rule_master", financialYear, taxRegime);
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
        if (results.isEmpty() || results.get(0) == null) {
            throw new ReferenceDataMissingException("reference.hra_rule_master", financialYear, taxRegime);
        }
        return results.get(0);
    }
}
