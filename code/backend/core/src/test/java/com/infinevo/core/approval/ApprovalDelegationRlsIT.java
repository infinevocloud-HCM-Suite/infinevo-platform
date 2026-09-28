package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tenant isolation and RLS tests for core.approval_delegation (W-15.3, spec section 6).
 */
@SpringBootTest(classes = ApprovalTestApp.class)
class ApprovalDelegationRlsIT extends AbstractIntegrationTest {

    private static final UUID TENANT_A = ApprovalTestSchema.TENANT_A;
    private static final UUID TENANT_B = ApprovalTestSchema.TENANT_B;

    @Autowired
    private DelegationService delegationService;

    @MockitoBean
    private EmployeeService employeeService;

    private UUID empA1Id;
    private UUID empA2Id;
    private UUID empB1Id;

    @BeforeAll
    static void setupSchema() throws Exception {
        ApprovalTestSchema.apply();
    }

    @BeforeEach
    void setUp() throws Exception {
        ApprovalTestSchema.seedTenants();
        ApprovalTestSchema.clearAll();

        TenantContext.set(TENANT_A);
        empA1Id = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-A1", "Alice", "alice@a.test");
        empA2Id = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-A2", "Bob", "bob@a.test");

        TenantContext.set(TENANT_B);
        empB1Id = ApprovalTestSchema.insertEmployee(TENANT_B, "EMP-B1", "Charlie", "charlie@b.test");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Tenant B cannot see or delete Tenant A's delegations")
    void tenantIsolationOnDelegations() {
        TenantContext.set(TENANT_A);

        DelegationCreateRequest createReq = new DelegationCreateRequest(
                empA2Id, LocalDate.now(), LocalDate.now().plusDays(5), null);
        DelegationResponse delegationA = delegationService.createDelegation(TENANT_A, empA1Id, createReq);

        // Tenant A can see it
        List<DelegationResponse> listA = delegationService.getDelegations(TENANT_A, null, LocalDate.now());
        assertThat(listA).hasSize(1);
        assertThat(listA.get(0).id()).isEqualTo(delegationA.id());

        // Switch to Tenant B
        TenantContext.set(TENANT_B);

        // Tenant B sees empty list
        List<DelegationResponse> listB = delegationService.getDelegations(TENANT_B, null, LocalDate.now());
        assertThat(listB).isEmpty();

        // Tenant B cannot resolve delegate from Tenant A's delegator
        Optional<UUID> resolvedInB =
                delegationService.resolveDelegate(TENANT_B, empA1Id, ApprovalFlowType.LEAVE, LocalDate.now());
        assertThat(resolvedInB).isEmpty();

        // Tenant B cannot delete Tenant A's delegation
        assertThatThrownBy(() -> delegationService.deleteDelegation(TENANT_B, delegationA.id(), empB1Id))
                .isInstanceOf(NoSuchElementException.class);
    }
}
