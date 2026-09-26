package com.infinevo.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.test.EnabledIfDockerAvailable;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * W-09 §7 — the reference schema and its statutory seed.
 *
 * <p>Runs the shipped {@code V003}–{@code V005} through Flyway against a database of its own, then
 * asserts the tables, the permissions, the deliberate absence of tenancy, and every seeded
 * statutory value. The values are asserted rather than trusted because there was no legacy script
 * to port them from: the tax rules lived only as hand-typed rows in the running MySQL database
 * (spec §4, "Seed data has no legacy source script"), so this test is the only thing standing
 * between a mistyped slab and a wrong payslip.
 */
@EnabledIfDockerAvailable
class ReferenceSchemaIT {

    /** Its own database, so the annual-update simulation cannot disturb another test's run. */
    private static final String DATABASE = "infinevo_reference";

    /** Permission denied, and must be owner of table. Both arrive as SQL state 42501. */
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    private static final List<String> REFERENCE_TABLES = List.of(
            "country",
            "state",
            "currency",
            "bank",
            "tax_slab_master",
            "tax_slab_master_history",
            "tax_slab_detail_history",
            "hra_rule_master",
            "home_loan_rule_master",
            "let_out_property_rule_master",
            "other_income_rule_master",
            "standard_deduction_rule_master",
            "section6a_item_master",
            "section87a_rebate_rule_master",
            "cess_surcharge_rule_master",
            // W-11.1 — the action catalogue (V020__action.sql)
            "action");

    private static String jdbcUrl;

    @BeforeAll
    static void applyShippedMigrations() {
        jdbcUrl = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);
        MigrationApplication.launch(
                "--DB_URL=" + jdbcUrl,
                "--DB_MIGRATION_USERNAME=" + PostgresTestContainerInitializer.MIGRATION_USER,
                "--DB_MIGRATION_PASSWORD=" + PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    // ── 1. the tables exist and migration_user owns them

    @Test
    void referenceTablesExist_andOwnedByMigrationUser() throws SQLException {
        Map<String, String> owners = new LinkedHashMap<>();
        try (Connection conn = migrationUserConnection();
                ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT tablename, tableowner FROM pg_tables WHERE schemaname = 'reference'")) {
            while (rs.next()) {
                owners.put(rs.getString(1), rs.getString(2));
            }
        }
        assertThat(owners.keySet()).containsExactlyInAnyOrderElementsOf(REFERENCE_TABLES);
        assertThat(owners.values())
                .as("every reference table is owned by migration_user")
                .containsOnly(PostgresTestContainerInitializer.MIGRATION_USER);
    }

    // ── 2. app_user can read all sixteen

    @Test
    void appUser_hasSelectPermission_onAllReferenceTables() throws SQLException {
        try (Connection conn = appUserConnection()) {
            for (String table : REFERENCE_TABLES) {
                try (ResultSet rs = conn.createStatement().executeQuery("SELECT count(*) FROM reference." + table)) {
                    assertThat(rs.next()).as("SELECT on reference.%s", table).isTrue();
                }
            }
        }
    }

    // ── 3. and cannot write or drop

    @Test
    void appUser_refusedWriteAndDropPermissions_onReferenceSchema() throws SQLException {
        try (Connection conn = appUserConnection()) {
            assertThatThrownBy(() -> conn.createStatement()
                            .execute("INSERT INTO reference.country (code, alpha3, numeric_code, name, dial_code,"
                                    + " default_currency) VALUES ('ZZ', 'ZZZ', '999', 'Nowhere', '+0', 'INR')"))
                    .as("INSERT into a reference table")
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(INSUFFICIENT_PRIVILEGE);

            assertThatThrownBy(() -> conn.createStatement()
                            .execute("UPDATE reference.country SET name = 'Renamed' WHERE code = 'IN'"))
                    .as("UPDATE of a reference table")
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(INSUFFICIENT_PRIVILEGE);

            assertThatThrownBy(() -> conn.createStatement().execute("DELETE FROM reference.country"))
                    .as("DELETE from a reference table")
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(INSUFFICIENT_PRIVILEGE);

            // Ownership, not a grant, is what refuses this one: DROP cannot be granted away.
            assertThatThrownBy(() -> conn.createStatement().execute("DROP TABLE reference.country"))
                    .as("DROP of a reference table")
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    // ── 4. the tenancy exemption is real, not just intended (D-08, CONVENTIONS.md Rule 7)

    @Test
    void referenceTables_haveNoTenantIdColumn_andNoRLS() throws SQLException {
        try (Connection conn = migrationUserConnection()) {
            try (ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT count(*) FROM information_schema.columns"
                            + " WHERE table_schema = 'reference' AND column_name = 'tenant_id'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("tenant_id columns in the reference schema")
                        .isZero();
            }
            try (ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                            + " WHERE n.nspname = 'reference' AND c.relrowsecurity")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("tables with RLS enabled in the reference schema")
                        .isZero();
            }
            try (ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT count(*) FROM pg_policies WHERE schemaname = 'reference'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).as("policies in the reference schema").isZero();
            }
        }
    }

    // ── 5. generic lookups

    @Test
    void genericLookups_seededCorrectly() throws SQLException {
        try (Connection conn = appUserConnection()) {
            assertThat(strings(conn, "SELECT code FROM reference.country ORDER BY code"))
                    .contains("IN", "US", "GB", "AE");
            assertThat(strings(conn, "SELECT code FROM reference.currency ORDER BY code"))
                    .contains("INR", "USD", "EUR", "GBP");

            assertThat(count(conn, "SELECT count(*) FROM reference.state WHERE NOT is_union_territory"))
                    .as("Indian states")
                    .isEqualTo(28);
            assertThat(count(conn, "SELECT count(*) FROM reference.state WHERE is_union_territory"))
                    .as("union territories")
                    .isEqualTo(8);

            // Every GST state code is exactly two digits: it is the prefix of every GSTIN.
            assertThat(count(conn, "SELECT count(*) FROM reference.state WHERE gst_state_code !~ '^[0-9]{2}$'"))
                    .as("state rows with a malformed GST code")
                    .isZero();

            assertThat(strings(conn, "SELECT gst_state_code FROM reference.state ORDER BY gst_state_code"))
                    .contains("27", "29", "33", "07");

            assertThat(count(conn, "SELECT count(*) FROM reference.bank"))
                    .as("scheduled commercial banks")
                    .isGreaterThanOrEqualTo(10);
            // An IFSC prefix is four characters; the branch code follows it after a zero.
            assertThat(count(conn, "SELECT count(*) FROM reference.bank WHERE ifsc_prefix !~ '^[A-Z]{4}$'"))
                    .as("banks with a malformed IFSC prefix")
                    .isZero();
            assertThat(strings(conn, "SELECT ifsc_prefix FROM reference.bank ORDER BY ifsc_prefix"))
                    .contains("SBIN", "HDFC", "ICIC", "UTIB");
        }
    }

    // ── 6. slabs, per financial year and regime

    @Test
    void taxSlabMasterAndDetails_seededPerFinancialYear() throws SQLException {
        try (Connection conn = appUserConnection()) {
            // Scoped to the three seeded years rather than counting the whole table:
            // annualUpdateSimulation adds FY 2026-2027 to this same database, and JUnit
            // does not promise an order, so an unscoped count passes or fails depending on
            // which test ran first.
            assertThat(count(
                            conn,
                            "SELECT count(*) FROM reference.tax_slab_master"
                                    + " WHERE financial_year IN ('2023-2024', '2024-2025', '2025-2026')"))
                    .as(
                            "three financial years: GENERAL (2 regimes × 3) + SENIOR/SUPER_SENIOR (OLD × 3 × 2) = 12 headers")
                    .isEqualTo(12);

            // The old regime has not moved across the three years for GENERAL category.
            for (String fy : List.of("2023-2024", "2024-2025", "2025-2026")) {
                assertThat(brackets(conn, fy, "OLD", "GENERAL"))
                        .as("old regime GENERAL brackets for FY %s", fy)
                        .containsExactly(
                                bracket("0", "250000", "0.00"),
                                bracket("250000", "500000", "5.00"),
                                bracket("500000", "1000000", "20.00"),
                                bracket("1000000", null, "30.00"));
            }

            // Senior citizens (60-79) old regime: nil band up to 3,00,000.
            for (String fy : List.of("2023-2024", "2024-2025", "2025-2026")) {
                assertThat(brackets(conn, fy, "OLD", "SENIOR"))
                        .as("old regime SENIOR brackets for FY %s", fy)
                        .containsExactly(
                                bracket("0", "300000", "0.00"),
                                bracket("300000", "500000", "5.00"),
                                bracket("500000", "1000000", "20.00"),
                                bracket("1000000", null, "30.00"));
            }

            // Super senior citizens (80+) old regime: nil band up to 5,00,000.
            for (String fy : List.of("2023-2024", "2024-2025", "2025-2026")) {
                assertThat(brackets(conn, fy, "OLD", "SUPER_SENIOR"))
                        .as("old regime SUPER_SENIOR brackets for FY %s", fy)
                        .containsExactly(
                                bracket("0", "500000", "0.00"),
                                bracket("500000", "1000000", "20.00"),
                                bracket("1000000", null, "30.00"));
            }

            // No NEW regime header with a non-GENERAL age category exists.
            assertThat(count(
                            conn,
                            "SELECT count(*) FROM reference.tax_slab_master"
                                    + " WHERE regime = 'NEW' AND age_category != 'GENERAL'"))
                    .as("no NEW regime headers for non-GENERAL age categories")
                    .isEqualTo(0);

            assertThat(brackets(conn, "2023-2024", "NEW"))
                    .containsExactly(
                            bracket("0", "300000", "0.00"),
                            bracket("300000", "600000", "5.00"),
                            bracket("600000", "900000", "10.00"),
                            bracket("900000", "1200000", "15.00"),
                            bracket("1200000", "1500000", "20.00"),
                            bracket("1500000", null, "30.00"));

            assertThat(brackets(conn, "2024-2025", "NEW"))
                    .containsExactly(
                            bracket("0", "300000", "0.00"),
                            bracket("300000", "700000", "5.00"),
                            bracket("700000", "1000000", "10.00"),
                            bracket("1000000", "1200000", "15.00"),
                            bracket("1200000", "1500000", "20.00"),
                            bracket("1500000", null, "30.00"));

            // Seven bands, and the 25% band that no earlier year had.
            assertThat(brackets(conn, "2025-2026", "NEW"))
                    .containsExactly(
                            bracket("0", "400000", "0.00"),
                            bracket("400000", "800000", "5.00"),
                            bracket("800000", "1200000", "10.00"),
                            bracket("1200000", "1600000", "15.00"),
                            bracket("1600000", "2000000", "20.00"),
                            bracket("2000000", "2400000", "25.00"),
                            bracket("2400000", null, "30.00"));

            // Every header has a version-1 history row to supersede when the law changes.
            assertThat(count(conn, "SELECT count(*) FROM reference.tax_slab_master_history WHERE version = 1"))
                    .isEqualTo(12);
        }
    }

    // ── 7. standard deduction and the 87A rebate, per financial year

    @Test
    void standardDeductionAndRebate_seededPerFinancialYear() throws SQLException {
        try (Connection conn = appUserConnection()) {
            assertThat(standardDeduction(conn, "2023-2024", "OLD")).isEqualByComparingTo("50000.0000");
            assertThat(standardDeduction(conn, "2023-2024", "NEW")).isEqualByComparingTo("50000.0000");
            assertThat(standardDeduction(conn, "2024-2025", "OLD")).isEqualByComparingTo("50000.0000");
            assertThat(standardDeduction(conn, "2024-2025", "NEW")).isEqualByComparingTo("75000.0000");
            assertThat(standardDeduction(conn, "2025-2026", "OLD")).isEqualByComparingTo("50000.0000");
            assertThat(standardDeduction(conn, "2025-2026", "NEW")).isEqualByComparingTo("75000.0000");

            assertThat(rebate(conn, "2023-2024", "OLD")).containsExactly("500000.0000", "12500.0000");
            assertThat(rebate(conn, "2023-2024", "NEW")).containsExactly("700000.0000", "25000.0000");
            assertThat(rebate(conn, "2024-2025", "OLD")).containsExactly("500000.0000", "12500.0000");
            assertThat(rebate(conn, "2024-2025", "NEW")).containsExactly("700000.0000", "25000.0000");
            assertThat(rebate(conn, "2025-2026", "OLD")).containsExactly("500000.0000", "12500.0000");
            // The Union Budget 2025 change, and the value most likely to be got wrong.
            assertThat(rebate(conn, "2025-2026", "NEW")).containsExactly("1200000.0000", "60000.0000");
        }
    }

    // ── 8. the annual update runbook, executed rather than described

    @Test
    void annualUpdateSimulation_oneMigrationAppliesCleanly() throws SQLException {
        // Exactly the runbook in spec §4: one new versioned script in the reference folder,
        // applied by the same runner. V099 sits far above the shipped sequence so it cannot
        // collide with a real migration added later.
        MigrationApplication.launch(
                "--DB_URL=" + jdbcUrl,
                "--DB_MIGRATION_USERNAME=" + PostgresTestContainerInitializer.MIGRATION_USER,
                "--DB_MIGRATION_PASSWORD=" + PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD,
                "--spring.flyway.locations="
                        + "classpath:db/migration/reference"
                        + ",classpath:db/migration/core"
                        + ",classpath:db/migration/hrms"
                        + ",classpath:db/migration/payroll"
                        + ",classpath:db/migration-annual/reference");

        try (Connection conn = appUserConnection()) {
            assertThat(count(conn, "SELECT count(*) FROM reference.tax_slab_master WHERE financial_year = '2026-2027'"))
                    .as("the new financial year is readable straight after the migration")
                    .isEqualTo(1);
            assertThat(brackets(conn, "2026-2027", "NEW"))
                    .as("and so are its brackets")
                    .containsExactly(
                            bracket("0", "500000", "0.00"),
                            bracket("500000", "1000000", "10.00"),
                            bracket("1000000", null, "30.00"));
        }
    }

    // ── 9. the two masters that fail silently when empty

    @Test
    void silentMasters_seededWithStatutoryRows() throws SQLException {
        try (Connection conn = appUserConnection()) {
            assertThat(strings(conn, "SELECT section_code FROM reference.section6a_item_master ORDER BY display_order"))
                    .containsExactly(
                            "80C",
                            "80CCC",
                            "80CCD(1)",
                            "80CCD(1B)",
                            "80CCD(2)",
                            "80D",
                            "80DD",
                            "80DDB",
                            "80E",
                            "80G",
                            "80GGA",
                            "80GGC",
                            "80U");

            assertThat(decimal(
                            conn, "SELECT max_limit FROM reference.section6a_item_master WHERE section_code = '80C'"))
                    .isEqualByComparingTo("150000.0000");
            assertThat(count(
                            conn,
                            "SELECT count(*) FROM reference.section6a_item_master"
                                    + " WHERE section_code = '80C' AND is_80c"))
                    .isEqualTo(1);
            assertThat(count(
                            conn,
                            "SELECT count(*) FROM reference.section6a_item_master"
                                    + " WHERE section_code = '80D' AND is_80d"))
                    .isEqualTo(1);
            // The 1,50,000 ceiling is shared, so the three sections under it must say so.
            assertThat(strings(
                            conn,
                            "SELECT section_code FROM reference.section6a_item_master"
                                    + " WHERE category_group_code = '80C_GROUP' ORDER BY display_order"))
                    .containsExactly("80C", "80CCC", "80CCD(1)");

            for (String fy : List.of("2023-2024", "2024-2025", "2025-2026")) {
                assertThat(decimal(
                                conn,
                                "SELECT rate FROM reference.cess_surcharge_rule_master"
                                        + " WHERE rule_type = 'CESS' AND financial_year = '" + fy + "'"))
                        .as("health and education cess, FY %s", fy)
                        .isEqualByComparingTo("4.00");

                assertThat(strings(
                                conn,
                                "SELECT DISTINCT income_from::text FROM reference.cess_surcharge_rule_master"
                                        + " WHERE rule_type = 'SURCHARGE' AND financial_year = '" + fy + "'"
                                        + " ORDER BY 1"))
                        .as("surcharge thresholds, FY %s", fy)
                        .containsExactlyInAnyOrder("5000000.0000", "10000000.0000", "20000000.0000", "50000000.0000");

                // The top band is the only place the regimes differ, so it is asserted as a
                // row that must exist rather than as a maximum. A max() over NEW and BOTH
                // is already satisfied by the BOTH 2Cr-5Cr row at 25.00, so deleting the
                // NEW above-5Cr row - the row this test exists to prove - left it passing.
                assertThat(decimal(
                                conn,
                                "SELECT rate FROM reference.cess_surcharge_rule_master"
                                        + " WHERE rule_type = 'SURCHARGE' AND financial_year = '" + fy + "'"
                                        + " AND tax_regime = 'NEW' AND income_from = 50000000.0000"))
                        .as("new regime surcharge above 5,00,00,000, FY %s", fy)
                        .isEqualByComparingTo("25.00");

                assertThat(decimal(
                                conn,
                                "SELECT rate FROM reference.cess_surcharge_rule_master"
                                        + " WHERE rule_type = 'SURCHARGE' AND financial_year = '" + fy + "'"
                                        + " AND tax_regime = 'OLD' AND income_from = 50000000.0000"))
                        .as("old regime surcharge above 5,00,00,000, FY %s", fy)
                        .isEqualByComparingTo("37.00");

                // And no row anywhere lets the new regime exceed the 25% cap.
                assertThat(count(
                                conn,
                                "SELECT count(*) FROM reference.cess_surcharge_rule_master"
                                        + " WHERE rule_type = 'SURCHARGE' AND financial_year = '" + fy + "'"
                                        + " AND tax_regime IN ('NEW', 'BOTH') AND rate > 25.00"))
                        .as("new regime surcharge rows above the cap, FY %s", fy)
                        .isZero();
            }
        }
    }

    // ── 10. the remaining rule masters, per financial year

    @Test
    void remainingRuleMasters_seededPerFinancialYear() throws SQLException {
        try (Connection conn = appUserConnection()) {
            for (String fy : List.of("2023-2024", "2024-2025", "2025-2026")) {
                assertThat(count(
                                conn,
                                "SELECT count(*) FROM reference.hra_rule_master WHERE is_active"
                                        + " AND financial_year = '" + fy + "' AND metro_percent = 50.00"
                                        + " AND non_metro_percent = 40.00 AND basic_da_percent_threshold = 10.00"
                                        + " AND pan_mandatory_threshold = 100000.0000"))
                        .as("HRA rule, FY %s", fy)
                        .isEqualTo(1);

                assertThat(decimal(
                                conn,
                                "SELECT max_limit FROM reference.home_loan_rule_master"
                                        + " WHERE financial_year = '" + fy + "' AND section_code = '24B'"
                                        + " AND property_type = 'SELF_OCCUPIED'"))
                        .as("Section 24(b) self-occupied interest cap, FY %s", fy)
                        .isEqualByComparingTo("200000.0000");

                assertThat(count(
                                conn,
                                "SELECT count(*) FROM reference.let_out_property_rule_master WHERE is_active"
                                        + " AND financial_year = '" + fy + "'"
                                        + " AND standard_deduction_percent = 30.00"
                                        + " AND max_loss_setoff_limit = 200000.0000"))
                        .as("let-out property rule, FY %s", fy)
                        .isEqualTo(1);

                assertThat(decimal(
                                conn,
                                "SELECT max_limit FROM reference.other_income_rule_master" + " WHERE financial_year = '"
                                        + fy + "' AND section_code = '80TTA'"))
                        .as("Section 80TTA, FY %s", fy)
                        .isEqualByComparingTo("10000.0000");

                assertThat(decimal(
                                conn,
                                "SELECT max_limit FROM reference.other_income_rule_master" + " WHERE financial_year = '"
                                        + fy + "' AND section_code = '80TTB'"))
                        .as("Section 80TTB, FY %s", fy)
                        .isEqualByComparingTo("50000.0000");
            }
        }
    }

    // ── helpers

    /** A slab bracket rendered as one comparable string: from|to|rate, with "-" for an open top. */
    private static String bracket(String from, String to, String rate) {
        return from + "|" + (to == null ? "-" : to) + "|" + rate;
    }

    private static List<String> brackets(Connection conn, String financialYear, String regime) throws SQLException {
        return brackets(conn, financialYear, regime, "GENERAL");
    }

    private static List<String> brackets(Connection conn, String financialYear, String regime, String ageCategory)
            throws SQLException {
        List<String> found = new ArrayList<>();
        String sql = "SELECT d.from_amount, d.to_amount, d.tax_rate_percent"
                + " FROM reference.tax_slab_detail_history d"
                + " JOIN reference.tax_slab_master m ON m.id = d.slab_master_id"
                + " WHERE m.financial_year = '" + financialYear + "' AND m.regime = '" + regime + "'"
                + " AND m.age_category = '" + ageCategory + "'"
                + " ORDER BY d.slab_order";
        try (ResultSet rs = conn.createStatement().executeQuery(sql)) {
            while (rs.next()) {
                BigDecimal to = rs.getBigDecimal(2);
                found.add(bracket(
                        rs.getBigDecimal(1).stripTrailingZeros().toPlainString(),
                        to == null ? null : to.stripTrailingZeros().toPlainString(),
                        rs.getBigDecimal(3).toPlainString()));
            }
        }
        return found;
    }

    private static BigDecimal standardDeduction(Connection conn, String financialYear, String regime)
            throws SQLException {
        return decimal(
                conn,
                "SELECT amount FROM reference.standard_deduction_rule_master WHERE financial_year = '" + financialYear
                        + "' AND regime = '" + regime + "'");
    }

    /**
     * The threshold and the rebate, in that order. Read as two columns of one row rather than
     * as a UNION ALL, whose row order SQL does not guarantee — the pair would have compared
     * equal either way round often enough to look fine and then swap.
     */
    private static List<String> rebate(Connection conn, String financialYear, String regime) throws SQLException {
        String sql = "SELECT income_threshold::text, max_rebate_amount::text"
                + " FROM reference.section87a_rebate_rule_master"
                + " WHERE financial_year = '" + financialYear + "' AND regime = '" + regime + "'";
        try (ResultSet rs = conn.createStatement().executeQuery(sql)) {
            assertThat(rs.next())
                    .as("a rebate row exists for FY %s %s", financialYear, regime)
                    .isTrue();
            return List.of(rs.getString(1), rs.getString(2));
        }
    }

    private static List<String> strings(Connection conn, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (ResultSet rs = conn.createStatement().executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }

    private static int count(Connection conn, String sql) throws SQLException {
        try (ResultSet rs = conn.createStatement().executeQuery(sql)) {
            assertThat(rs.next()).as("query returned a row: %s", sql).isTrue();
            return rs.getInt(1);
        }
    }

    private static BigDecimal decimal(Connection conn, String sql) throws SQLException {
        try (ResultSet rs = conn.createStatement().executeQuery(sql)) {
            assertThat(rs.next()).as("query returned a row: %s", sql).isTrue();
            BigDecimal value = rs.getBigDecimal(1);
            assertThat(value).as("value is present: %s", sql).isNotNull();
            return value;
        }
    }

    private static Connection migrationUserConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    private static Connection appUserConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl, PostgresTestContainerInitializer.APP_USER, PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }
}
