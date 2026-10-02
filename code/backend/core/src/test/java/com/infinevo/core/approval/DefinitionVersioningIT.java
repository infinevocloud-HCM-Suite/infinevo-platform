package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
 * W-15.1, spec section 7 — {@code DefinitionVersioningIT}.
 *
 * <p>Proves effective-date versioning on approval definitions:
 * the definition in force on a date is the latest active one with {@code effective_from <= date}.
 */
@SpringBootTest(classes = ApprovalTestApp.class)
class DefinitionVersioningIT extends AbstractIntegrationTest {

    private static final UUID TENANT = ApprovalTestSchema.TENANT_A;

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
        TenantContext.set(TENANT);

        // Version 1: effective 2024-01-01, 1 step (REPORTING_MANAGER)
        service.saveDefinition(
                TENANT,
                ApprovalFlowType.LEAVE,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.PER_STEP,
                        LocalDate.of(2024, 1, 1),
                        true,
                        List.of(new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER))));

        // Version 2: effective 2026-06-01, 2 steps (REPORTING_MANAGER, then HR)
        service.saveDefinition(
                TENANT,
                ApprovalFlowType.LEAVE,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.PER_STEP,
                        LocalDate.of(2026, 6, 1),
                        true,
                        List.of(
                                new ApprovalStepDefinition(ApproverKind.REPORTING_MANAGER),
                                new ApprovalStepDefinition(ApproverKind.ROLE, "hr", 3, false))));
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @AfterEach
    void removeApprovalRows() throws Exception {
        // These tests share a database with the employee tests, which delete employees; approval rows left
        // behind by the last test would block that delete through approval_instance's foreign key.
        ApprovalTestSchema.clearDefinitions();
    }

    @Test
    @DisplayName("Queries before version 2 effective date resolve version 1")
    void queryBeforeVersion2ResolvesVersion1() {
        Optional<ApprovalDefinitionResponse> effective =
                service.getEffectiveDefinition(TENANT, ApprovalFlowType.LEAVE, LocalDate.of(2026, 5, 31));

        assertThat(effective).isPresent();
        assertThat(effective.get().effectiveFrom()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(effective.get().steps()).hasSize(1);
        assertThat(effective.get().steps().get(0).getKind()).isEqualTo(ApproverKind.REPORTING_MANAGER);
    }

    @Test
    @DisplayName("Queries on or after version 2 effective date resolve version 2")
    void queryOnOrAfterVersion2ResolvesVersion2() {
        Optional<ApprovalDefinitionResponse> effectiveOnStart =
                service.getEffectiveDefinition(TENANT, ApprovalFlowType.LEAVE, LocalDate.of(2026, 6, 1));

        assertThat(effectiveOnStart).isPresent();
        assertThat(effectiveOnStart.get().effectiveFrom()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(effectiveOnStart.get().steps()).hasSize(2);

        Optional<ApprovalDefinitionResponse> effectiveLater =
                service.getEffectiveDefinition(TENANT, ApprovalFlowType.LEAVE, LocalDate.of(2026, 9, 27));

        assertThat(effectiveLater).isPresent();
        assertThat(effectiveLater.get().effectiveFrom()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(effectiveLater.get().steps()).hasSize(2);
    }

    @Test
    @DisplayName("Queries prior to any version return empty")
    void queryBeforeAnyVersionReturnsEmpty() {
        Optional<ApprovalDefinitionResponse> effective =
                service.getEffectiveDefinition(TENANT, ApprovalFlowType.LEAVE, LocalDate.of(2023, 12, 31));

        assertThat(effective).isEmpty();
    }

    // Instances reference employees. Left behind, they block every later suite that wipes
    // core.employee (attendance, employee, org, job, lop) with a foreign-key error.
    @AfterAll
    static void clearApprovalRows() throws Exception {
        ApprovalTestSchema.clearAll();
    }
}
