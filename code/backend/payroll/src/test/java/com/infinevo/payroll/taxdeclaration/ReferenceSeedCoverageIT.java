package com.infinevo.payroll.taxdeclaration;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CI guard ensuring statutory reference rules are seeded for the current financial year.
 * Prevents build breakage when the calendar rolls over to a new financial year on 1 April.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class ReferenceSeedCoverageIT extends AbstractIntegrationTest {

    @BeforeAll
    static void applySchema() throws Exception {
        TaxDeclarationTestSchema.apply();
    }

    @AfterAll
    static void tearDown() throws SQLException {
        TaxDeclarationTestSchema.clearAll();
    }

    @Test
    @DisplayName("Statutory reference rules must exist for the current financial year")
    void currentFinancialYearHasActiveReferenceRules() throws SQLException {
        FinancialYear currentFy = FinancialYear.of(LocalDate.now());
        String fyLabel = currentFy.label();

        try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
            int hraCount = countActiveRules(conn, "reference.hra_rule_master", fyLabel, "OLD");
            assertThat(hraCount)
                    .withFailMessage(
                            "Required statutory reference rules for FY %s are missing from reference.hra_rule_master (regime=OLD). "
                                    + "A new reference migration is required for the new financial year.",
                            fyLabel)
                    .isGreaterThanOrEqualTo(1);

            int letOutCount = countActiveRules(conn, "reference.let_out_property_rule_master", fyLabel, "OLD");
            assertThat(letOutCount)
                    .withFailMessage(
                            "Required statutory reference rules for FY %s are missing from reference.let_out_property_rule_master (regime=OLD). "
                                    + "A new reference migration is required for the new financial year.",
                            fyLabel)
                    .isGreaterThanOrEqualTo(1);

            int homeLoanCount = countActiveRulesWithoutRegime(conn, "reference.home_loan_rule_master", fyLabel);
            assertThat(homeLoanCount)
                    .withFailMessage(
                            "Required statutory reference rules for FY %s are missing from reference.home_loan_rule_master. "
                                    + "A new reference migration is required for the new financial year.",
                            fyLabel)
                    .isGreaterThanOrEqualTo(1);

            int otherIncomeCount = countActiveRules(conn, "reference.other_income_rule_master", fyLabel, "OLD");
            assertThat(otherIncomeCount)
                    .withFailMessage(
                            "Required statutory reference rules for FY %s are missing from reference.other_income_rule_master (regime=OLD). "
                                    + "A new reference migration is required for the new financial year.",
                            fyLabel)
                    .isGreaterThanOrEqualTo(1);
        }
    }

    private static int countActiveRules(Connection conn, String qualifiedTable, String financialYear, String regime)
            throws SQLException {
        String sql = "SELECT count(*) FROM " + qualifiedTable
                + " WHERE financial_year = ? AND tax_regime = ? AND is_active = true";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, financialYear);
            ps.setString(2, regime);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    private static int countActiveRulesWithoutRegime(Connection conn, String qualifiedTable, String financialYear)
            throws SQLException {
        String sql = "SELECT count(*) FROM " + qualifiedTable + " WHERE financial_year = ? AND is_active = true";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, financialYear);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }
}
