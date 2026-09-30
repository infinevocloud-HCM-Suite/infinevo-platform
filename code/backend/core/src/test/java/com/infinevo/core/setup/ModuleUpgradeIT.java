package com.infinevo.core.setup;

import static com.infinevo.core.setup.SetupChecklistTestSchema.TENANT_A;
import static com.infinevo.core.setup.SetupChecklistTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.subscription.SubscriptionService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-24.1 — adding Payroll to an HRMS-only tenant adds the payroll steps and leaves completed ones alone.
 */
@SpringBootTest(classes = SetupChecklistTestApp.class, properties = "spring.main.allow-bean-definition-overriding=true")
class ModuleUpgradeIT extends AbstractIntegrationTest {

    @Autowired
    private SetupChecklistService checklistService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private TenantSetupStepRepository stepRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // Derived and @Query repository reads run outside any transaction when called directly, and
    // the tenant-binding datasource refuses an auto-commit connection. Reads go through here.
    private <T> T inTransaction(Supplier<T> read) {
        return new TransactionTemplate(transactionManager).execute(status -> read.get());
    }

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
            // 1. Initial state: HRMS only -> exactly 2 core steps, WORK_LOCATION incomplete.
            // EMPLOYEE is not asserted: other suites seed core.employee rows for the same tenant id.
            SetupChecklistResponse initialChecklist = checklistService.getChecklist(TENANT_A);
            assertThat(initialChecklist.steps()).hasSize(2);
            assertThat(initialChecklist.steps())
                    .extracting(SetupStepResponse::code)
                    .containsExactly("WORK_LOCATION", "EMPLOYEE");
            assertThat(initialChecklist.steps())
                    .filteredOn(s -> s.code().equals("WORK_LOCATION"))
                    .allMatch(s -> !s.completed());

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

            // Eager assembly: verify steps exist in DB immediately before getChecklist is called
            assertThat(inTransaction(() -> stepRepository.findByTenantIdOrderByDisplayOrderAsc(TENANT_A)))
                    .hasSize(SetupStepCatalogue.DEFAULT_STEPS.size());

            // 3. Re-read checklist
            SetupChecklistResponse upgradedChecklist = checklistService.getChecklist(TENANT_A);

            // Now holds every catalogue step
            assertThat(upgradedChecklist.steps()).hasSize(SetupStepCatalogue.DEFAULT_STEPS.size());
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

    @Test
    @DisplayName(
            "a platform admin bound to another tenant upgrades the target: its payroll steps are added, completed ones kept")
    void crossTenantModuleUpgradeAddsTargetStepsAndPreservesCompleted() throws SQLException {
        // 1. The target tenant's own checklist, with WORK_LOCATION completed.
        try (Connection conn = SetupChecklistTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.work_location (id, tenant_id, code, name, country_code, is_filing_address, is_active) "
                                + "VALUES (?, ?, 'HQ', 'Headquarters', 'IN', true, true)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, TENANT_A);
            ps.executeUpdate();
        }
        TenantContext.set(TENANT_A);
        try {
            SetupChecklistResponse before = checklistService.getChecklist(TENANT_A);
            assertThat(before.steps()).extracting(SetupStepResponse::code).containsExactly("WORK_LOCATION", "EMPLOYEE");
            assertThat(before.steps())
                    .filteredOn(s -> s.code().equals("WORK_LOCATION"))
                    .allMatch(SetupStepResponse::completed);
        } finally {
            TenantContext.clear();
        }

        // 2. The upgrade runs with the thread bound to a different tenant, as a platform admin's
        //    request is (TenantServiceImpl provisions the same way, rebinding to the target).
        TenantContext.set(TENANT_B);
        try {
            subscriptionService.updateModules(TENANT_A, Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL));
            assertThat(TenantContext.require())
                    .as("the caller's binding is restored after the upgrade")
                    .isEqualTo(TENANT_B);
            assertThat(inTransaction(() -> stepRepository.findByTenantIdOrderByDisplayOrderAsc(TENANT_B)))
                    .as("nothing is written under the admin's own tenant")
                    .isEmpty();
        } finally {
            TenantContext.clear();
        }

        // 3. Read back as the target: every catalogue step, WORK_LOCATION still completed.
        TenantContext.set(TENANT_A);
        try {
            List<TenantSetupStep> rows =
                    inTransaction(() -> stepRepository.findByTenantIdOrderByDisplayOrderAsc(TENANT_A));
            assertThat(rows)
                    .extracting(TenantSetupStep::getStepCode)
                    .containsExactlyElementsOf(SetupStepCatalogue.DEFAULT_STEPS.stream()
                            .map(SetupStepCatalogue.StepDefinition::code)
                            .toList());
            assertThat(rows)
                    .filteredOn(r -> r.getStepCode().equals("WORK_LOCATION"))
                    .allMatch(r -> r.getCompletedAt() != null);
            assertThat(rows)
                    .filteredOn(r -> r.getModule() == PlatformModule.PAYROLL)
                    .hasSize(5)
                    .allMatch(r -> r.getCompletedAt() == null);
        } finally {
            TenantContext.clear();
        }
    }
}
