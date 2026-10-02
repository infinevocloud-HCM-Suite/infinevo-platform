package com.infinevo.payroll.priorpayroll;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.setup.SetupChecklistService;
import com.infinevo.core.setup.SetupStepCatalogue;
import com.infinevo.core.setup.SetupStepChecker;
import com.infinevo.core.setup.TenantSetupStepRepository;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.entitlement.EntitlementSource;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
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
 * W-38.3 §7: Integration test for setup_step_skipped on prior payroll status.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PriorPayrollStatusIT extends AbstractIntegrationTest {

    private static final String FY = "2026-2027";

    @Autowired
    private PriorPayrollService priorPayrollService;

    @Autowired
    private TenantSetupStepRepository tenantSetupStepRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeAll
    static void applySchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
        PayrollTestSchema.seedTenants();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "status reports setup_step_skipped true when PRIOR_PAYROLL is skipped for tenant, isolated from other tenants")
    void statusReportsSetupStepSkippedWhenStepIsSkipped() {
        // 1. Initial read: no step row exists yet, setup_step_skipped is false
        TenantContext.set(TENANT_A);
        PriorPayrollStatusResponse initialResponse = priorPayrollService.status(FY);
        assertThat(initialResponse.setupStepSkipped()).isFalse();

        // 2. Skip through W-24.1's real service inside a transaction bound to TENANT_A
        EntitlementSource entitlementSource = mock(EntitlementSource.class);
        when(entitlementSource.modulesOf(any())).thenReturn(Set.of(PlatformModule.PAYROLL));

        SetupChecklistService checklist = new SetupChecklistService(
                tenantSetupStepRepository,
                entitlementSource,
                SetupStepCatalogue.DEFAULT_STEPS.stream()
                        .map(d -> stub(d.code(), d.module()))
                        .toList());

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            TenantContext.set(TENANT_A);
            checklist.skipStep(TENANT_A, "PRIOR_PAYROLL", "Started payroll in April");
        });

        // 3. Status for Tenant A now reports setup_step_skipped = true
        TenantContext.set(TENANT_A);
        PriorPayrollStatusResponse afterSkipResponse = priorPayrollService.status(FY);
        assertThat(afterSkipResponse.setupStepSkipped()).isTrue();

        // 4. Status for Tenant B reports setup_step_skipped = false (RLS isolation)
        TenantContext.set(TENANT_B);
        PriorPayrollStatusResponse tenantBResponse = priorPayrollService.status(FY);
        assertThat(tenantBResponse.setupStepSkipped()).isFalse();
    }

    private static SetupStepChecker stub(String code, PlatformModule module) {
        return new SetupStepChecker() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public PlatformModule module() {
                return module;
            }

            @Override
            public boolean isComplete(UUID tenantId) {
                return false;
            }
        };
    }
}
