package com.infinevo.shared.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fast unit tests for DatabaseIndexConvention validation logic (PLAT-06, DEBT-018).
 * Runs unconditionally without requiring a running Docker daemon.
 */
class DatabaseIndexConventionTest {

    @Test
    @DisplayName("Unit test: findIndexViolations flags non-tenant-leading column")
    void flagsNonTenantLeadingColumn() {
        DatabaseIndexConventionIT.IndexInfo badColumnIndex = new DatabaseIndexConventionIT.IndexInfo(
                "core", "employee", "idx_employee_tenant_test", "status", false, false, "CREATE INDEX ...");
        List<String> violations = DatabaseIndexConventionIT.findIndexViolations(List.of(badColumnIndex));
        assertThat(violations)
                .hasSize(1)
                .first()
                .asString()
                .contains("has leading column 'status', expected 'tenant_id'");
    }

    @Test
    @DisplayName("Unit test: findIndexViolations flags invalid naming convention (missing tenant)")
    void flagsInvalidIndexNameMissingTenant() {
        DatabaseIndexConventionIT.IndexInfo badNameIndex = new DatabaseIndexConventionIT.IndexInfo(
                "core", "employee", "idx_employee_status", "tenant_id", false, false, "CREATE INDEX ...");
        List<String> violations = DatabaseIndexConventionIT.findIndexViolations(List.of(badNameIndex));
        assertThat(violations).hasSize(1).first().asString().contains("does not follow naming convention");
    }

    @Test
    @DisplayName("Unit test: findIndexViolations flags invalid naming convention prefix")
    void flagsInvalidIndexNamePrefix() {
        DatabaseIndexConventionIT.IndexInfo badPrefixIndex = new DatabaseIndexConventionIT.IndexInfo(
                "core", "employee", "custom_emp_tenant_idx", "tenant_id", false, false, "CREATE INDEX ...");
        List<String> violations = DatabaseIndexConventionIT.findIndexViolations(List.of(badPrefixIndex));
        assertThat(violations).hasSize(1).first().asString().contains("does not follow naming convention");
    }

    @Test
    @DisplayName("Unit test: findIndexViolations accepts valid tenant-leading composite index")
    void acceptsValidTenantIndex() {
        DatabaseIndexConventionIT.IndexInfo validIndex = new DatabaseIndexConventionIT.IndexInfo(
                "core", "employee", "idx_employee_tenant_status", "tenant_id", false, false, "CREATE INDEX ...");
        List<String> violations = DatabaseIndexConventionIT.findIndexViolations(List.of(validIndex));
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Unit test: findIndexViolations accepts valid unique tenant index")
    void acceptsValidUniqueTenantIndex() {
        DatabaseIndexConventionIT.IndexInfo validUnique = new DatabaseIndexConventionIT.IndexInfo(
                "core", "employee", "uk_employee_tenant_code", "tenant_id", false, true, "CREATE UNIQUE INDEX ...");
        List<String> violations = DatabaseIndexConventionIT.findIndexViolations(List.of(validUnique));
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Unit test: findIndexViolations respects platform exemptions")
    void respectsPlatformExemptions() {
        DatabaseIndexConventionIT.IndexInfo shedlock =
                new DatabaseIndexConventionIT.IndexInfo("core", "shedlock", "shedlock_pk", "name", true, true, "...");
        DatabaseIndexConventionIT.IndexInfo userTenantLookup = new DatabaseIndexConventionIT.IndexInfo(
                "core", "user_tenant", "idx_user_tenant_user_id", "user_id", false, false, "...");
        DatabaseIndexConventionIT.IndexInfo jobStatusReaper = new DatabaseIndexConventionIT.IndexInfo(
                "core", "job_status", "idx_job_status_created", "created_at", false, false, "...");

        List<String> violations =
                DatabaseIndexConventionIT.findIndexViolations(List.of(shedlock, userTenantLookup, jobStatusReaper));
        assertThat(violations).isEmpty();
    }
}
