package com.infinevo.payroll.template;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.setup.SetupStepChecker;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.component.EarningService;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-73.9 §7, the payroll half: India's {@code V162} payload, applied as {@code app_user} with the tenant bound,
 * gives the tenant at least 20 salary components, EPF and ESI settings disabled with the statutory rates, and a
 * monthly pay schedule; a second run changes nothing; the four setup steps read pre-filled until a save; and
 * another tenant gets nothing. Rows are read as the schema owner, past RLS.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayrollTemplateContributorIT extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private SalaryComponentTemplateContributor components;

    @Autowired
    private StatutoryTemplateContributor statutory;

    @Autowired
    private PayScheduleTemplateContributor paySchedule;

    @Autowired
    private List<SetupStepChecker> checkers;

    @Autowired
    private EarningService earningService;

    @Autowired
    private PayScheduleService payScheduleService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeAll
    static void initSchema() throws Exception {
        PayRunTestSchema.apply();
        PayrollTestSchema.seedTenants();
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            if (!PayrollTestSchema.tableExists(conn, "reference", "country_template")) {
                PayrollTestSchema.executeResource(conn, "db/migration/reference/V162__country_template_in.sql");
            }
        }
    }

    @BeforeEach
    void clean() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
        for (String table : List.of("payroll.pay_schedule", "payroll.epf_setting", "payroll.esi_setting")) {
            owner("DELETE FROM " + table + " WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        }
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("India's template gives >= 20 components, disabled EPF and ESI with rates, a monthly pay schedule")
    void indiaTemplate_writesThePayrollDefaults() throws Exception {
        assertThat(applyAll(TENANT_A)).containsExactly(true, true, true);

        assertThat(count("SELECT count(*) FROM payroll.earning WHERE tenant_id = ? AND created_by = 'template'"))
                .isGreaterThanOrEqualTo(17);
        assertThat(count("SELECT count(*) FROM payroll.earning WHERE tenant_id = ?")
                        + count("SELECT count(*) FROM payroll.deduction WHERE tenant_id = ?"))
                .isGreaterThanOrEqualTo(20);
        assertThat(row("SELECT earning_type, calculation_type, default_value::text, percentage_of,"
                        + " is_included_in_epf::text, epf_inclusion_type FROM payroll.earning"
                        + " WHERE tenant_id = ? AND code = 'BASIC'"))
                .containsExactly("Basic", "PERCENTAGE", "50.0000", "CTC", "true", "ALWAYS");
        assertThat(strings("SELECT code FROM payroll.deduction WHERE tenant_id = ?"))
                .contains("LOAN_EMI", "SALARY_ADVANCE")
                .doesNotContain("PF", "ESI", "PT", "TDS");

        assertThat(row("SELECT is_enabled::text, employee_rate::text, employer_rate::text, eps_rate::text,"
                        + " wage_ceiling::text, created_by, updated_by FROM payroll.epf_setting WHERE tenant_id = ?"))
                .containsExactly("false", "12.0000", "12.0000", "8.3300", "15000.0000", "template", "template");
        assertThat(row("SELECT is_enabled::text, employee_rate::text, employer_rate::text, wage_ceiling::text"
                        + " FROM payroll.esi_setting WHERE tenant_id = ?"))
                .containsExactly("false", "0.7500", "3.2500", "21000.0000");

        assertThat(row("SELECT frequency, pay_day_rule, working_days::text, input_cutoff_day::text,"
                        + " first_period_start::text FROM payroll.pay_schedule WHERE tenant_id = ?"))
                .containsExactly("MONTHLY", "LAST_WORKING_DAY", "{1,2,3,4,5}", "25", "2026-10-01");
    }

    @Test
    @DisplayName("a second run writes nothing; another tenant got nothing")
    void secondRun_changesNothing() throws Exception {
        applyAll(TENANT_A);
        String before = fingerprint(TENANT_A);

        assertThat(applyAll(TENANT_A)).containsExactly(false, false, false);

        assertThat(fingerprint(TENANT_A)).isEqualTo(before);
        assertThat(fingerprint(TENANT_B)).isEqualTo("0/0/0/0/0");
    }

    @Test
    @DisplayName("a tenant with an earning of its own keeps its catalogue; the rest is still applied")
    void ownComponents_areLeftAlone() throws Exception {
        owner(
                "INSERT INTO payroll.earning (tenant_id, code, name, earning_type) VALUES (?, 'MINE', 'Mine', 'Basic')",
                TENANT_A);

        assertThat(applyAll(TENANT_A)).containsExactly(false, true, true);
        assertThat(strings("SELECT code FROM payroll.earning WHERE tenant_id = ?"))
                .containsExactly("MINE");
    }

    @Test
    @DisplayName("the four steps read pre-filled until a save replaces the template as the last writer")
    void prefilledUntilSaved() throws Exception {
        applyAll(TENANT_A);
        Map<String, SetupStepChecker> byCode =
                checkers.stream().collect(Collectors.toMap(SetupStepChecker::code, c -> c, (a, b) -> a));

        for (String code : List.of("PAY_SCHEDULE", "SALARY_COMPONENTS", "EPF", "ESI")) {
            assertThat(asTenant(TENANT_A, () -> byCode.get(code).isComplete(TENANT_A)))
                    .as(code + " complete")
                    .isTrue();
            assertThat(asTenant(TENANT_A, () -> byCode.get(code).isPrefilled(TENANT_A)))
                    .as(code + " pre-filled")
                    .isTrue();
        }

        UUID basic =
                UUID.fromString(strings("SELECT id::text FROM payroll.earning WHERE tenant_id = ? AND code = 'BASIC'")
                        .get(0));
        asTenant(TENANT_A, () -> earningService.updateActive(basic, true));
        asTenant(
                TENANT_A,
                () -> payScheduleService.upsert(new PayScheduleRequest(
                        List.of(1, 2, 3, 4, 5, 6), PayDayRule.LAST_WORKING_DAY, null, 25, LocalDate.of(2026, 10, 1))));

        assertThat(asTenant(TENANT_A, () -> byCode.get("SALARY_COMPONENTS").isPrefilled(TENANT_A)))
                .as("an earning was saved")
                .isFalse();
        assertThat(asTenant(TENANT_A, () -> byCode.get("PAY_SCHEDULE").isPrefilled(TENANT_A)))
                .as("the pay schedule was saved")
                .isFalse();
        assertThat(asTenant(TENANT_A, () -> byCode.get("EPF").isPrefilled(TENANT_A)))
                .as("EPF untouched")
                .isTrue();
    }

    private List<Boolean> applyAll(UUID tenantId) throws Exception {
        JsonNode componentsPayload = payload("salary_components");
        JsonNode statutoryPayload = payload("statutory");
        JsonNode schedulePayload = payload("pay_schedule");
        return asTenant(
                tenantId,
                () -> List.of(
                        components.apply(tenantId, componentsPayload, TODAY),
                        statutory.apply(tenantId, statutoryPayload, TODAY),
                        paySchedule.apply(tenantId, schedulePayload, TODAY)));
    }

    private <T> T asTenant(UUID tenantId, Supplier<T> work) {
        TenantContext.set(tenantId);
        try {
            return new TransactionTemplate(transactionManager).execute(status -> work.get());
        } finally {
            TenantContext.clear();
        }
    }

    private static JsonNode payload(String section) throws Exception {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT payload::text FROM reference.country_template"
                        + " WHERE country_code = 'IN' AND section = ?")) {
            ps.setString(1, section);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("IN/" + section + " seeded").isTrue();
                return JSON.readTree(rs.getString(1));
            }
        }
    }

    private static String fingerprint(UUID tenantId) throws SQLException {
        StringBuilder out = new StringBuilder();
        for (String table : List.of(
                "payroll.earning",
                "payroll.deduction",
                "payroll.epf_setting",
                "payroll.esi_setting",
                "payroll.pay_schedule")) {
            if (!out.isEmpty()) {
                out.append('/');
            }
            out.append(countFor("SELECT count(*) FROM " + table + " WHERE tenant_id = ?", tenantId));
        }
        return out.toString();
    }

    private static long count(String sql) throws SQLException {
        return countFor(sql, TENANT_A);
    }

    private static long countFor(String sql, UUID tenantId) throws SQLException {
        return Long.parseLong(query(sql, tenantId).get(0).get(0));
    }

    private static List<String> row(String sql) throws SQLException {
        List<List<String>> rows = query(sql, TENANT_A);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private static List<String> strings(String sql) throws SQLException {
        return query(sql, TENANT_A).stream().map(r -> r.get(0)).toList();
    }

    private static List<List<String>> query(String sql, UUID tenantId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            List<List<String>> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                int columns = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    List<String> r = new ArrayList<>();
                    for (int i = 1; i <= columns; i++) {
                        r.add(rs.getString(i));
                    }
                    out.add(r);
                }
            }
            return out;
        }
    }

    private static void owner(String sql, Object... params) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }
}
