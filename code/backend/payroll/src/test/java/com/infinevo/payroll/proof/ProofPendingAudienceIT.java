package com.infinevo.payroll.proof;

import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_A;
import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.notification.ReminderRule;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test for {@link ProofPendingAudienceResolver} (W-34.3 spec section 7).
 *
 * <p>Verifies that of five employees (no declaration; declaration DRAFT; submitted, no proof; proof
 * DRAFT; proof APPROVED), and one inactive with a submitted declaration: exactly two IDs are returned,
 * and Tenant B's rows never appear.
 */
class ProofPendingAudienceIT extends ProofIntegrationTestBase {

    @Autowired
    private ProofPendingAudienceResolver audienceResolver;

    @Autowired
    private EmployeeProofOfInvestmentRepository proofRepository;

    @Test
    @DisplayName("Acceptance: exactly two pending employees returned for Tenant A, and Tenant B never leaks")
    void resolvesPendingEmployeesWithTenantIsolation() throws Exception {
        openWindows();

        // 1. No declaration
        UUID emp1 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-001", "emp1@acme.com", "Emp", "One");

        // 2. Declaration in DRAFT status
        UUID emp2 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-002", "emp2@acme.com", "Emp", "Two");
        taxDeclarationService.read(emp2, fy);

        // 3. Submitted declaration, no proof row (NOT_STARTED)
        UUID emp3 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-003", "emp3@acme.com", "Emp", "Three");
        declare(emp3);

        // 4. Submitted declaration, proof in DRAFT status
        UUID emp4 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-004", "emp4@acme.com", "Emp", "Four");
        declare(emp4);
        actAs(emp4);
        proofService.readOwn(fy);

        // 5. Submitted declaration, proof APPROVED
        UUID emp5 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-005", "emp5@acme.com", "Emp", "Five");
        declare(emp5);
        actAs(emp5);
        ProofResponse p5 = proofService.readOwn(fy);
        inTransaction(() -> {
            EmployeeProofOfInvestment proof =
                    proofRepository.findByTenantIdAndId(TENANT_A, p5.id()).orElseThrow();
            proof.setStatus(ProofStatus.APPROVED);
            return proofRepository.save(proof);
        });

        // 6. Inactive (TERMINATED) employee with a submitted declaration, no proof row
        UUID emp6 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-006", "emp6@acme.com", "Emp", "Six");
        declare(emp6);
        inTransaction(() -> {
            try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                    Statement stmt = conn.createStatement()) {
                stmt.execute("UPDATE core.employee SET status = 'TERMINATED' WHERE id = '" + emp6 + "'");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        // 7. Tenant B: Submitted declaration, no proof row
        TenantContext.set(TENANT_B);
        openWindows();
        UUID empTenantB =
                TaxDeclarationTestSchema.seedEmployee(TENANT_B, "EMP-B01", "empB@globex.com", "Globex", "User");
        declare(empTenantB);
        TenantContext.set(TENANT_A);

        ReminderRule dummyRule = org.mockito.Mockito.mock(ReminderRule.class);

        // When resolving for Tenant A:
        List<UUID> resolvedTenantA = audienceResolver.resolve(dummyRule, TENANT_A);

        // Exactly emp3 and emp4 should be returned:
        assertThat(resolvedTenantA)
                .as("Tenant A pending proof audience")
                .containsExactlyInAnyOrder(emp3, emp4)
                .doesNotContain(emp1, emp2, emp5, emp6, empTenantB);

        // When resolving for Tenant B. The worker's ReminderEvaluator binds the tenant it resolves for before
        // it calls a resolver, so this does the same: the resolver itself never binds one.
        TenantContext.set(TENANT_B);
        List<UUID> resolvedTenantB;
        try {
            resolvedTenantB = audienceResolver.resolve(dummyRule, TENANT_B);
        } finally {
            TenantContext.set(TENANT_A);
        }
        assertThat(resolvedTenantB).as("Tenant B pending proof audience").containsExactly(empTenantB);
    }
}
