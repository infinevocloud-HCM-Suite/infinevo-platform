package com.infinevo.core.employee.detail;

import static com.infinevo.core.employee.EmployeeTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.audit.CoreAuditTestApp;
import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmployeeTestSchema;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-13.2 — the audit opt-in actually captures, and the secrets in it are redacted (spec section 2,
 * the founder's addition).
 *
 * <p>This is the test the ticket exists for. W-22.1 shipped the capture mechanism and it recorded
 * <strong>nothing</strong> for as long as it was in the tree, because no production class carried
 * {@code @Audited} — its own spec section 2 records why {@code core.tenant} and
 * {@code core.user_tenant} could not be the proof: both are reached by raw JDBC, and a Hibernate
 * listener cannot observe a write it never sees. {@code core.employee_bank} is the first that can be,
 * and it is also the worst one to get wrong.
 *
 * <p>Three things are asserted about one bank write.
 *
 * <ul>
 *   <li>A row reaches {@code core.audit_log} at all — the opt-in works.
 *   <li>{@code bank_account_number} and {@code ifsc_code} are {@code ***} in {@code new_values} —
 *       both are matched by {@code AuditWriter.REDACTED_FRAGMENTS}. <strong>Renaming either column
 *       silently un-redacts it</strong>, and this test is what would notice.
 *   <li>{@code bank_name} is the real value — a trail that redacted everything would pass the second
 *       assertion and be useless.
 * </ul>
 *
 * <p>The row is read as the schema owner, in raw SQL, rather than through {@code AuditQueryService}:
 * the question is what is stored in the {@code jsonb}, and a service that could format it differently
 * on the way out would make the assertion about the wrong thing.
 *
 * <p>Runs in {@link CoreAuditTestApp}, not {@code CoreFeatureTestApp} — that class says why.
 *
 * <p>{@code AbstractIntegrationTest} carries {@code @EnabledIfDockerAvailable}, so with no Docker this
 * class fails rather than reporting green having run nothing (#117).
 */
@SpringBootTest(classes = CoreAuditTestApp.class)
class EmployeeDetailAuditIT extends AbstractIntegrationTest {

    private static final String ACCOUNT_NUMBER = "0012345678";
    private static final String IFSC = "SBIN0001234";
    private static final String BANK_NAME = "State Bank of India";

    @Autowired
    private EmployeeBankService bankService;

    @Autowired
    private EmployeeService employeeService;

    private UUID employeeOfA;

    @BeforeAll
    static void applySchema() throws Exception {
        EmployeeDetailTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        EmployeeDetailTestSchema.clearAll();
        EmployeeDetailTestSchema.clearAudit();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        EmployeeTestSchema.seedTenants();
        EmployeeDetailTestSchema.clearAll();
        EmployeeDetailTestSchema.clearAudit();
        employeeOfA = EmployeeTestSchema.seedEmployee(TENANT_A, "A-001", "Asha");
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Writing a bank section captures an audit row, with the account number and IFSC redacted")
    void bankWriteIsCapturedAndRedacted() throws SQLException {
        TenantContext.set(TENANT_A);
        bankService.put(
                employeeOfA,
                new EmployeeBankRequest(
                        PaymentMode.BANK_TRANSFER,
                        "Asha Rao",
                        BANK_NAME,
                        IFSC,
                        ACCOUNT_NUMBER,
                        BankAccountType.SAVINGS));

        Map<String, String> newValues = newValuesOf("employee_bank", "INSERT");

        assertThat(newValues)
                .as("the @Audited opt-in must actually capture — W-22.1 shipped the mechanism and it "
                        + "had recorded nothing because no production class carried the annotation")
                .isNotEmpty();
        assertThat(newValues).containsEntry("bank_account_number", "***");
        assertThat(newValues).containsEntry("ifsc_code", "***");
        assertThat(newValues)
                .as("and the ordinary columns stay readable, or the trail is useless")
                .containsEntry("bank_name", BANK_NAME);
        assertThat(newValues).containsEntry("payment_mode", "BANK_TRANSFER");
        assertThat(newValues.values())
                .as("no captured value may be the account number or the IFSC, under any column name")
                .doesNotContain(ACCOUNT_NUMBER, IFSC);

        // W-13.2 review, F-1, end to end against a real session. The association holds the Employee
        // object in the event state, not the foreign key, and it maps to one column - so it resolves,
        // is not withheld, and was being written as String.valueOf(entity). Employee declares no
        // toString, so the row carried "com.infinevo.core.employee.Employee@1b6d3586" and named
        // nobody: entity_id on an audit row is the SECTION row's id, not the employee's.
        assertThat(newValues)
                .as("the audit row must say whose bank account this is")
                .containsEntry("employee_id", employeeOfA.toString());
        assertThat(newValues.values())
                .as("a JVM identity string names nobody and may carry the object's own fields")
                .noneMatch(value -> value != null && value.contains("com.infinevo"));
    }

    @Test
    @DisplayName("Replacing the section captures an UPDATE naming only the columns that changed")
    void replacingTheSectionCapturesTheDiff() throws SQLException {
        TenantContext.set(TENANT_A);
        bankService.put(
                employeeOfA,
                new EmployeeBankRequest(
                        PaymentMode.BANK_TRANSFER,
                        "Asha Rao",
                        BANK_NAME,
                        IFSC,
                        ACCOUNT_NUMBER,
                        BankAccountType.SAVINGS));
        EmployeeDetailTestSchema.clearAudit();

        bankService.put(
                employeeOfA,
                new EmployeeBankRequest(
                        PaymentMode.BANK_TRANSFER,
                        "Asha Rao",
                        "HDFC Bank",
                        IFSC,
                        ACCOUNT_NUMBER,
                        BankAccountType.SAVINGS));

        Map<String, String> newValues = newValuesOf("employee_bank", "UPDATE");
        assertThat(newValues).containsEntry("bank_name", "HDFC Bank");
        assertThat(newValues)
                .as("the account number did not change, so it is not in the diff at all")
                .doesNotContainKey("bank_account_number");
    }

    @Test
    @DisplayName("The root employee record is audited too — W-13.2 annotated it as well")
    void theEmployeeRootIsAuditedToo() throws SQLException {
        TenantContext.set(TENANT_A);
        // The seeded employee was inserted by raw JDBC, which no Hibernate listener can see - the
        // exact reason W-22.1 could not prove capture with core.tenant. So the write has to go
        // through the service.
        employeeService.update(
                employeeOfA,
                new EmployeeRequest(
                        "A-001",
                        "Asha",
                        null,
                        "Rao",
                        null,
                        LocalDate.of(2026, 4, 1),
                        null,
                        EmploymentStatus.ACTIVE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(countAuditRows("employee"))
                .as("Employee carries @Audited as of W-13.2, so an update to it is captured")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Writing a section does not restate the employee row")
    void aSectionWriteDoesNotAuditTheEmployee() throws SQLException {
        TenantContext.set(TENANT_A);
        bankService.put(employeeOfA, new EmployeeBankRequest(PaymentMode.CASH, null, null, null, null, null));

        assertThat(countAuditRows("employee_bank")).isEqualTo(1);
        assertThat(countAuditRows("employee"))
                .as("nothing on the employee changed, so nothing about it is captured")
                .isZero();
    }

    /** The {@code new_values} of the one audit row for this table and operation, read as the owner. */
    private static Map<String, String> newValuesOf(String table, String operation) throws SQLException {
        Map<String, String> values = new LinkedHashMap<>();
        try (Connection conn = EmployeeTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        SELECT kv.key, kv.value
                          FROM core.audit_log a,
                               LATERAL jsonb_each_text(a.new_values) AS kv
                         WHERE a.tenant_id = ? AND a.entity_schema = 'core'
                           AND a.entity_table = ? AND a.operation = ?
                        """)) {
            ps.setObject(1, TENANT_A);
            ps.setString(2, table);
            ps.setString(3, operation);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    values.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return values;
    }

    /** How many audit rows exist for one table in tenant A, read as the owner. */
    private static int countAuditRows(String table) throws SQLException {
        try (Connection conn = EmployeeTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM core.audit_log WHERE tenant_id = ? AND entity_table = ?")) {
            ps.setObject(1, TENANT_A);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
