package com.infinevo.payroll;

import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Schema, seed data and connection helpers for payroll integration tests (W-26.1).
 */
public final class PayrollTestSchema {

    public static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public static com.infinevo.core.employee.EmployeeResponse createTestEmployee(
            UUID id, UUID tenantId, String code, String first, String last, String email) {
        return new com.infinevo.core.employee.EmployeeResponse(
                id,
                tenantId,
                code,
                first,
                null,
                last,
                "MALE",
                java.time.LocalDate.now(),
                null,
                null,
                email,
                null,
                false,
                null,
                null,
                null,
                null,
                java.time.Instant.now(),
                java.time.Instant.now());
    }

    private PayrollTestSchema() {}

    public static Connection migrationConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    public static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    public static void apply() throws Exception {
        try (Connection conn = migrationConnection()) {
            if (!tableExists(conn, "core", "tenant")) {
                executeResource(conn, "db/migration/core/V001__tenant.sql");
            }
            if (!tableExists(conn, "core", "employee")) {
                executeResource(conn, "db/migration/core/V010__employee.sql");
            }
            if (!tableExists(conn, "core", "employee_personal")) {
                executeResource(conn, "db/migration/core/V015__employee_personal.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE core.employee_personal NO FORCE ROW LEVEL SECURITY");
                }
            }
            if (!tableExists(conn, "payroll", "earning")) {
                executeResource(conn, "db/migration/payroll/V042__earning.sql");
            }
            if (!tableExists(conn, "payroll", "deduction")) {
                executeResource(conn, "db/migration/payroll/V043__deduction.sql");
            }
            if (!tableExists(conn, "payroll", "benefit")) {
                executeResource(conn, "db/migration/payroll/V044__benefit.sql");
            }
            if (!tableExists(conn, "payroll", "reimbursement")) {
                executeResource(conn, "db/migration/payroll/V045__reimbursement.sql");
            }
            if (!tableExists(conn, "payroll", "ctc_structure")) {
                executeResource(conn, "db/migration/payroll/V046__ctc_structure.sql");
            }
            if (!tableExists(conn, "payroll", "employee_earning")) {
                executeResource(conn, "db/migration/payroll/V047__employee_earning.sql");
            }
            if (!tableExists(conn, "payroll", "employee_benefit")) {
                executeResource(conn, "db/migration/payroll/V048__employee_benefit.sql");
            }
            if (!tableExists(conn, "payroll", "employee_reimbursement")) {
                executeResource(conn, "db/migration/payroll/V049__employee_reimbursement.sql");
            }
            if (!tableExists(conn, "payroll", "employee_statutory_profile")) {
                executeResource(conn, "db/migration/payroll/V050__employee_statutory_profile.sql");
            }
            if (!tableExists(conn, "payroll", "fbp")) {
                executeResource(conn, "db/migration/payroll/V051__fbp.sql");
            }
            if (!tableExists(conn, "payroll", "employee_fbp_component")) {
                executeResource(conn, "db/migration/payroll/V053__employee_fbp_component.sql");
            }
            if (!tableExists(conn, "payroll", "epf_setting")) {
                executeResource(conn, "db/migration/payroll/V062__epf_setting.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE payroll.epf_setting NO FORCE ROW LEVEL SECURITY");
                }
            }
            // D-39: V153 splits the merged EDLI/admin switches into four.
            if (!columnExists(conn, "payroll", "epf_setting", "include_edli_in_ctc")) {
                executeResource(conn, "db/migration/payroll/V153__epf_edli_admin_split.sql");
            }
            if (!tableExists(conn, "payroll", "esi_setting")) {
                executeResource(conn, "db/migration/payroll/V063__esi_setting.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE payroll.esi_setting NO FORCE ROW LEVEL SECURITY");
                }
            }
            if (!tableExists(conn, "reference", "state")) {
                executeResource(conn, "db/migration/reference/V003__reference_lookups.sql");
            }
            if (!tableExists(conn, "core", "department")) {
                executeResource(conn, "db/migration/core/V011__department.sql");
            }
            if (!tableExists(conn, "core", "designation")) {
                executeResource(conn, "db/migration/core/V012__designation.sql");
            }
            if (!tableExists(conn, "core", "work_location")) {
                executeResource(conn, "db/migration/core/V013__work_location.sql");
            }
            if (!columnExists(conn, "core", "employee", "work_location_id")) {
                if (!tableExists(conn, "core", "department")) {
                    executeResource(conn, "db/migration/core/V011__department.sql");
                }
                if (!tableExists(conn, "core", "designation")) {
                    executeResource(conn, "db/migration/core/V012__designation.sql");
                }
                executeResource(conn, "db/migration/core/V014__employee_org_columns.sql");
            }
            // W-38.1 reads employees through core's JPA Employee, which maps every column of
            // core.employee; user_account_id (V026) references core.user_account (V009).
            if (!tableExists(conn, "core", "user_account")) {
                executeResource(conn, "db/migration/core/V009__user_account.sql");
            }
            if (!columnExists(conn, "core", "employee", "user_account_id")) {
                executeResource(conn, "db/migration/core/V026__employee_user_account.sql");
            }
            // W-38.3: the status reads core.tenant_setup_step (V035, needs only core.tenant).
            if (!tableExists(conn, "core", "tenant_setup_step")) {
                executeResource(conn, "db/migration/core/V035__tenant_setup_step.sql");
            }
            if (!tableExists(conn, "reference", "pt_state")) {
                executeResource(conn, "db/migration/reference/V064__pt_state_and_slab.sql");
            }
            if (!tableExists(conn, "payroll", "org_pt_override")) {
                executeResource(conn, "db/migration/payroll/V065__org_pt_override.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE payroll.org_pt_override NO FORCE ROW LEVEL SECURITY");
                }
            }
            if (!tableExists(conn, "payroll", "org_pt_override_slab")) {
                executeResource(conn, "db/migration/payroll/V066__org_pt_override_slab.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE payroll.org_pt_override_slab NO FORCE ROW LEVEL SECURITY");
                }
            }
            if (!tableExists(conn, "payroll", "pt_history")) {
                executeResource(conn, "db/migration/payroll/V067__pt_history.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE payroll.pt_history NO FORCE ROW LEVEL SECURITY");
                }
            }
            if (!tableExists(conn, "reference", "action")) {
                executeResource(conn, "db/migration/reference/V020__action.sql");
            }
            if (!tableExists(conn, "core", "role")) {
                executeResource(conn, "db/migration/core/V021__role.sql");
            }
            if (!tableExists(conn, "core", "role_action")) {
                executeResource(conn, "db/migration/core/V022__role_action.sql");
            }
            if (!actionExists(conn, "core.document.read_own")) {
                executeResource(conn, "db/migration/core/V025__catalogue_correction.sql");
            }
            if (!actionExists(conn, "payroll.fbp.read")) {
                executeResource(conn, "db/migration/reference/V052__fbp_actions.sql");
            }
            executeResource(conn, "db/migration/reference/V097__reimbursement_claim_actions.sql");
            if (!tableExists(conn, "core", "pay_input")) {
                executeResource(conn, "db/migration/core/V031__pay_input.sql");
            }
            if (!tableExists(conn, "core", "pay_input_period_lock")) {
                executeResource(conn, "db/migration/core/V032__pay_input_period_lock.sql");
            }
            if (!columnExists(conn, "core", "pay_input_period_lock", "run_ref")) {
                executeResource(conn, "db/migration/core/V060__pay_input_run_ref.sql");
            }
            if (!tableExists(conn, "core", "document")) {
                executeResource(conn, "db/migration/core/V037__document.sql");
            }
            if (!tableExists(conn, "core", "approval_definition")) {
                executeResource(conn, "db/migration/core/V089__approval_definition.sql");
            }
            if (!tableExists(conn, "core", "approval_instance")) {
                executeResource(conn, "db/migration/core/V090__approval_instance.sql");
            }
            if (!tableExists(conn, "core", "approval_step")) {
                executeResource(conn, "db/migration/core/V091__approval_step.sql");
            }
            if (!tableExists(conn, "core", "approval_delegation")) {
                executeResource(conn, "db/migration/core/V092__approval_delegation.sql");
            }
            if (!tableExists(conn, "payroll", "employee_reimbursement_request")) {
                executeResource(conn, "db/migration/payroll/V098__employee_reimbursement_request.sql");
            }
            // W-35.2: the deduction action codes and grants, after V097 whose seed function they extend.
            executeResource(conn, "db/migration/reference/V100__employee_deduction_actions.sql");
            if (!tableExists(conn, "payroll", "employee_deduction")) {
                executeResource(conn, "db/migration/payroll/V101__employee_deduction.sql");
            }
            if (!tableExists(conn, "payroll", "ctc_epf_component")) {
                executeResource(conn, "db/migration/payroll/V068__ctc_epf_component.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE payroll.ctc_epf_component NO FORCE ROW LEVEL SECURITY");
                }
            }
            if (!tableExists(conn, "payroll", "ctc_esi_component")) {
                executeResource(conn, "db/migration/payroll/V069__ctc_esi_component.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE payroll.ctc_esi_component NO FORCE ROW LEVEL SECURITY");
                }
            }
            if (!tableExists(conn, "payroll", "tax_deductor")) {
                executeResource(conn, "db/migration/payroll/V107__tax_deductor.sql");
            }
            if (!tableExists(conn, "payroll", "prior_payroll_import_log")) {
                executeResource(conn, "db/migration/payroll/V140__prior_payroll.sql");
            }
            // W-36.5: the PAN lookup reads core.employee_identification (V017); core.document must accept
            // FORM16_PART_A (V108); payroll.form16_part_a (V109) references both.
            if (!tableExists(conn, "core", "employee_identification")) {
                executeResource(conn, "db/migration/core/V017__employee_identification.sql");
            }
            if (!documentKindAccepts(conn, "FORM16_PART_A")) {
                executeResource(conn, "db/migration/core/V108__document_kind_form16.sql");
            }
            if (!tableExists(conn, "payroll", "form16_part_a")) {
                executeResource(conn, "db/migration/payroll/V109__form16_part_a.sql");
            }
            try (Statement st = conn.createStatement()) {
                st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA core TO app_user");
                st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA payroll TO app_user");
                st.execute("GRANT SELECT ON ALL TABLES IN SCHEMA reference TO app_user");
            }
        }
    }

    public static void seedTenants() throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, TENANT_A);
            ps.setString(2, "Acme Manufacturing");
            ps.executeUpdate();
            ps.setObject(1, TENANT_B);
            ps.setString(2, "Globex Corporation");
            ps.executeUpdate();
        }
        try (Connection conn = migrationConnection();
                Statement st = conn.createStatement()) {
            if (tableExists(conn, "core", "approval_definition")) {
                st.execute("SELECT core.seed_approval_definitions('" + TENANT_A + "'::uuid)");
                st.execute("SELECT core.seed_approval_definitions('" + TENANT_B + "'::uuid)");
            }
        }
    }

    public static void cleanTables() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement st = conn.createStatement()) {
            if (tableExists(conn, "core", "tenant_setup_step")) {
                st.execute("DELETE FROM core.tenant_setup_step");
            }
            st.execute(
                    "TRUNCATE TABLE payroll.employee_fbp_component, payroll.fbp, payroll.ctc_structure, payroll.employee_statutory_profile, "
                            + "payroll.earning, payroll.deduction, payroll.benefit, payroll.reimbursement CASCADE");
            if (tableExists(conn, "payroll", "employee_investment_declaration")) {
                st.execute("TRUNCATE TABLE payroll.employee_investment_declaration CASCADE");
            }
            if (tableExists(conn, "payroll", "employee_reimbursement_request")) {
                st.execute("DELETE FROM payroll.employee_reimbursement_request");
            }
            // W-35.2: before core.document, core.pay_input and core.employee, which it references.
            if (tableExists(conn, "payroll", "employee_deduction")) {
                st.execute("DELETE FROM payroll.employee_deduction");
            }
            if (tableExists(conn, "payroll", "tax_deductor")) {
                st.execute("DELETE FROM payroll.tax_deductor");
            }
            if (tableExists(conn, "core", "approval_step")) {
                st.execute("DELETE FROM core.approval_step");
            }
            if (tableExists(conn, "core", "approval_instance")) {
                st.execute("DELETE FROM core.approval_instance");
            }
            if (tableExists(conn, "core", "pay_input_period_lock")) {
                st.execute("DELETE FROM core.pay_input_period_lock");
            }
            if (tableExists(conn, "core", "pay_input")) {
                st.execute("DELETE FROM core.pay_input");
            }
            if (tableExists(conn, "payroll", "prior_payroll_month")) {
                st.execute("DELETE FROM payroll.prior_payroll_month");
            }
            if (tableExists(conn, "payroll", "prior_payroll_import_log")) {
                st.execute("DELETE FROM payroll.prior_payroll_import_log");
            }
            // W-36.5: before core.document and core.employee, which it references.
            if (tableExists(conn, "payroll", "form16_part_a")) {
                st.execute("DELETE FROM payroll.form16_part_a");
            }
            if (tableExists(conn, "core", "document")) {
                st.execute("DELETE FROM core.document");
            }
            if (tableExists(conn, "payroll", "income_tax_declaration")) {
                st.execute("TRUNCATE TABLE payroll.income_tax_declaration CASCADE");
            }
            if (tableExists(conn, "payroll", "ctc_epf_component")) {
                st.execute("DELETE FROM payroll.ctc_epf_component");
            }
            if (tableExists(conn, "payroll", "ctc_esi_component")) {
                st.execute("DELETE FROM payroll.ctc_esi_component");
            }
            if (tableExists(conn, "payroll", "epf_setting")) {
                st.execute("DELETE FROM payroll.epf_setting");
            }
            if (tableExists(conn, "payroll", "esi_setting")) {
                st.execute("DELETE FROM payroll.esi_setting");
            }
            if (tableExists(conn, "payroll", "org_pt_override_slab")) {
                st.execute("DELETE FROM payroll.org_pt_override_slab");
            }
            if (tableExists(conn, "payroll", "org_pt_override")) {
                st.execute("DELETE FROM payroll.org_pt_override");
            }
            if (tableExists(conn, "payroll", "pt_history")) {
                st.execute("DELETE FROM payroll.pt_history");
            }
            if (tableExists(conn, "core", "attendance")) {
                st.execute("DELETE FROM core.attendance");
            }
            if (tableExists(conn, "core", "employee_personal")) {
                st.execute("DELETE FROM core.employee_personal");
            }
            // W-29.1 / W-29.2: pay run lines and rows reference core.employee, so they go before it.
            if (tableExists(conn, "payroll", "employee_payrun_line")) {
                st.execute("DELETE FROM payroll.employee_payrun_line");
            }
            if (tableExists(conn, "payroll", "employee_payrun")) {
                st.execute("DELETE FROM payroll.employee_payrun");
            }
            if (tableExists(conn, "payroll", "payrun")) {
                st.execute("DELETE FROM payroll.payrun");
            }
            if (tableExists(conn, "payroll", "employee_tds")) {
                st.execute("DELETE FROM payroll.employee_tds");
            }
            if (tableExists(conn, "core", "employee_bank")) {
                st.execute("DELETE FROM core.employee_bank");
            }
            if (tableExists(conn, "core", "employee_identification")) {
                st.execute("DELETE FROM core.employee_identification");
            }
            st.execute("DELETE FROM core.employee");
            if (tableExists(conn, "core", "work_location")) {
                st.execute("DELETE FROM core.work_location");
            }
            if (tableExists(conn, "core", "department")) {
                st.execute("DELETE FROM core.department");
            }
            if (tableExists(conn, "core", "designation")) {
                st.execute("DELETE FROM core.designation");
            }
        }
        PayrollTestApp.TEST_DOCUMENTS.clear();
        PayrollTestApp.TEST_DOCUMENT_CONTENTS.clear();
    }

    public static UUID insertDocument(UUID tenantId, String fileName, byte[] content) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.document
                            (id, tenant_id, employee_id, kind, file_name, content_type, size_bytes,
                             blob_container, blob_path, checksum_sha256)
                        VALUES (?, ?, NULL, 'EXPORT', ?, 'text/csv', ?, 'documents', ?, ?)
                        """)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, fileName);
            ps.setLong(4, content.length);
            ps.setString(5, tenantId + "/test/EXPORT/" + id);
            ps.setString(6, "0".repeat(64));
            ps.executeUpdate();
        }
        PayrollTestApp.TEST_DOCUMENT_CONTENTS.put(id, content);
        PayrollTestApp.TEST_DOCUMENTS.put(
                id,
                new com.infinevo.core.document.DocumentResponse(
                        id,
                        null,
                        com.infinevo.core.document.DocumentKind.EXPORT,
                        fileName,
                        "text/csv",
                        (long) content.length,
                        "test_checksum",
                        java.time.Instant.now(),
                        "system"));
        return id;
    }

    public static UUID insertEmployee(UUID tenantId, String employeeNumber, LocalDate dateOfJoining)
            throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee
                            (id, tenant_id, employee_number, first_name, last_name, date_of_joining, status,
                             created_by, updated_by)
                        VALUES (gen_random_uuid(), ?, ?, 'Test', 'Employee', ?, 'ACTIVE', 'test', 'test')
                        RETURNING id
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, employeeNumber);
            ps.setDate(3, java.sql.Date.valueOf(dateOfJoining));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return (UUID) rs.getObject(1);
            }
        }
    }

    public static void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement bind = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, false)")) {
            bind.setString(1, tenantId.toString());
            bind.execute();
        }
    }

    public static void clearTenant(Connection conn) throws SQLException {
        try (PreparedStatement clear = conn.prepareStatement("SELECT set_config('app.current_tenant_id', '', false)")) {
            clear.execute();
        }
    }

    /** True when {@code core.document}'s kind check already lists {@code kind} (V108 applied). */
    public static boolean documentKindAccepts(Connection conn, String kind) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'document_kind_check'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1).contains(kind);
            }
        }
    }

    public static boolean actionExists(Connection conn, String code) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM reference.action WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static boolean columnExists(Connection conn, String schema, String table, String column)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static boolean tableExists(Connection conn, String schema, String table) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT 1 FROM pg_tables WHERE schemaname = ? AND tablename = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void executeResource(Connection conn, String resourcePath) throws Exception {
        try (InputStream is = PayrollTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Migration script not found on test classpath: " + resourcePath);
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }
}
