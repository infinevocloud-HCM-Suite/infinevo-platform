package com.infinevo.core.setup;

import static com.infinevo.core.setup.SetupChecklistTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.subscription.SubscriptionService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-24.1 — adding Payroll to an HRMS-only tenant adds the payroll steps and leaves completed ones alone.
 */
@SpringBootTest(classes = SetupChecklistTestApp.class, properties = "spring.main.allow-bean-definition-overriding=true")
class ModuleUpgradeIT extends AbstractIntegrationTest {

    @Autowired
    private SetupChecklistService checklistService;

    @Autowired
    private SubscriptionService subscriptionService;

    @BeforeAll
    static void applySchema() throws Exception {
        SetupChecklistTestSchema.apply();
        SetupChecklistTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        SetupChecklistTestSchema.clearAll();
    }

    @BeforeEach
    void resetData() throws SQLException {
        SetupChecklistTestSchema.clearAll();

        try (Connection conn = SetupChecklistTestSchema.migrationConnection()) {
            UUID subA = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.subscription (id, tenant_id, status, started_on) VALUES (?, ?, 'ACTIVE', CURRENT_DATE)")) {
                ps.setObject(1, subA);
                ps.setObject(2, TENANT_A);
                ps.executeUpdate();
            }
            // Seed with HRMS module only
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.subscription_module (id, tenant_id, subscription_id, module, granted_on) VALUES (?, ?, ?, 'HRMS', CURRENT_DATE)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_A);
                ps.setObject(3, subA);
                ps.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName("adding Payroll to HRMS-only tenant adds payroll steps and preserves completed state")
    void moduleUpgradeAddsStepsAndPreservesCompleted() throws SQLException {
        TenantContext.set(TENANT_A);
        try {
            // 1. Initial state: HRMS only -> exactly 2 core steps, both incomplete
            SetupChecklistResponse initialChecklist = checklistService.getChecklist(TENANT_A);
            assertThat(initialChecklist.steps()).hasSize(2);
            assertThat(initialChecklist.steps())
                    .extracting(SetupStepResponse::code)
                    .containsExactly("WORK_LOCATION", "EMPLOYEE");
            assertThat(initialChecklist.steps()).allMatch(s -> !s.completed());

            // Complete WORK_LOCATION by creating a work location for Tenant A
            try (Connection conn = SetupChecklistTestSchema.migrationConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO core.work_location (id, tenant_id, code, name, country_code, is_filing_address, is_active) "
                                    + "VALUES (?, ?, 'HQ', 'Headquarters', 'IN', true, true)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_A);
                ps.executeUpdate();
            }

            // Verify WORK_LOCATION is now detected as completed
            SetupChecklistResponse checklistWithWl = checklistService.getChecklist(TENANT_A);
            SetupStepResponse completedWl = checklistWithWl.steps().stream()
                    .filter(s -> s.code().equals("WORK_LOCATION"))
                    .findFirst()
                    .orElseThrow();
            assertThat(completedWl.completed()).isTrue();
            assertThat(completedWl.completedAt()).isNotNull();

            // 2. Upgrade: add PAYROLL module to subscription
            subscriptionService.updateModules(TENANT_A, Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL));

            // 3. Re-read checklist
            SetupChecklistResponse upgradedChecklist = checklistService.getChecklist(TENANT_A);

            // Now holds all 9 steps
            assertThat(upgradedChecklist.steps()).hasSize(9);
            assertThat(upgradedChecklist.steps())
                    .extracting(SetupStepResponse::code)
                    .contains("WORK_LOCATION", "EMPLOYEE", "PAY_SCHEDULE", "EPF");

            // WORK_LOCATION preserved its completion!
            SetupStepResponse updatedWl = upgradedChecklist.steps().stream()
                    .filter(s -> s.code().equals("WORK_LOCATION"))
                    .findFirst()
                    .orElseThrow();
            assertThat(updatedWl.completed()).isTrue();
            assertThat(updatedWl.completedAt()).isNotNull();

            // Newly added steps are incomplete
            SetupStepResponse paySchedule = upgradedChecklist.steps().stream()
                    .filter(s -> s.code().equals("PAY_SCHEDULE"))
                    .findFirst()
                    .orElseThrow();
            assertThat(paySchedule.completed()).isFalse();
        } finally {
            TenantContext.clear();
        }
    }
}
