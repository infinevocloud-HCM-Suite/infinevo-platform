package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-15.1, spec section 7 — {@code ApprovalDefinitionRlsIT}.
 *
 * <p>Proves tenant isolation on {@code core.approval_definition}:
 * <ul>
 *   <li>Tenant A cannot read Tenant B's definitions through the service or raw app_user connection.
 *   <li>Row-level security policy {@code tenant_isolation} strictly enforces visibility at DB level.
 * </ul>
 */
@SpringBootTest(classes = ApprovalTestApp.class)
class ApprovalDefinitionRlsIT extends AbstractIntegrationTest {

    private static final UUID TENANT_A = ApprovalTestSchema.TENANT_A;
    private static final UUID TENANT_B = ApprovalTestSchema.TENANT_B;

    @Autowired
    private ApprovalDefinitionService service;

    @BeforeAll
    static void setupSchema() throws Exception {
        ApprovalTestSchema.apply();
    }

    @BeforeEach
    void seed() throws Exception {
        ApprovalTestSchema.seedTenants();
        ApprovalTestSchema.clearDefinitions();

        // Seed 1 definition for Tenant A
        TenantContext.set(TENANT_A);
        service.saveDefinition(
                TENANT_A,
                ApprovalFlowType.LEAVE,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.PER_STEP,
                        LocalDate.of(2026, 1, 1),
                        List.of(new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER))));

        // Seed 2 definitions for Tenant B
        TenantContext.set(TENANT_B);
        service.saveDefinition(
                TENANT_B,
                ApprovalFlowType.OVERTIME,
                new ApprovalDefinitionRequest(
                        StepOrdering.ANY_ORDER,
                        CommentScope.PER_STEP,
                        LocalDate.of(2026, 1, 1),
                        List.of(new ApprovalStepDefinition(ApproverKind.ROLE, "hr", 3, false))));
        service.saveDefinition(
                TENANT_B,
                ApprovalFlowType.REIMBURSEMENT,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.SHARED,
                        LocalDate.of(2026, 1, 1),
                        List.of(new ApprovalStepDefinition(ApproverKind.ROLE, "tenant-admin", 3, false))));
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Tenant A cannot see Tenant B definitions via service")
    void tenantACannotSeeTenantBDefinitions() {
        TenantContext.set(TENANT_A);

        List<ApprovalDefinitionResponse> tenantADefs = service.getAllDefinitions(TENANT_A);
        assertThat(tenantADefs).hasSize(1);
        assertThat(tenantADefs.get(0).flowType()).isEqualTo(ApprovalFlowType.LEAVE);

        TenantContext.set(TENANT_B);
        List<ApprovalDefinitionResponse> tenantBDefs = service.getAllDefinitions(TENANT_B);
        assertThat(tenantBDefs).hasSize(2);
        assertThat(tenantBDefs)
                .extracting(ApprovalDefinitionResponse::flowType)
                .containsExactlyInAnyOrder(ApprovalFlowType.OVERTIME, ApprovalFlowType.REIMBURSEMENT);
    }

    @Test
    @DisplayName("Raw app_user connection enforces tenant_isolation RLS policy")
    void rawAppUserConnectionIsolatesRows() throws Exception {
        assertThat(ApprovalTestSchema.visibleRowCount(TENANT_A)).isEqualTo(1);
        assertThat(ApprovalTestSchema.visibleRowCount(TENANT_B)).isEqualTo(2);
    }
}
