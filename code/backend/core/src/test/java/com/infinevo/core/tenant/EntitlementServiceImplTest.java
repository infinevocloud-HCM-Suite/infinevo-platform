package com.infinevo.core.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.infinevo.core.tenant.dto.TenantEntitlementResponse;
import com.infinevo.core.tenant.entity.SubscriptionModule;
import com.infinevo.core.tenant.repository.SubscriptionModuleRepository;
import com.infinevo.core.tenant.service.impl.EntitlementServiceImpl;
import com.infinevo.shared.entitlement.ModuleCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EntitlementServiceImplTest {

    private SubscriptionModuleRepository subscriptionModuleRepository;
    private EntitlementServiceImpl entitlementService;

    @BeforeEach
    void setUp() {
        subscriptionModuleRepository = Mockito.mock(SubscriptionModuleRepository.class);
        entitlementService = new EntitlementServiceImpl(subscriptionModuleRepository);
    }

    @Test
    void shouldCheckModuleEntitlement() {
        UUID tenantId = UUID.randomUUID();
        when(subscriptionModuleRepository.existsByTenantIdAndModuleCodeAndStatus(
                        tenantId, ModuleCode.PAYROLL, "ACTIVE"))
                .thenReturn(true);
        when(subscriptionModuleRepository.existsByTenantIdAndModuleCodeAndStatus(tenantId, ModuleCode.HRMS, "ACTIVE"))
                .thenReturn(false);

        assertTrue(entitlementService.isTenantEntitled(tenantId, ModuleCode.CORE));
        assertTrue(entitlementService.isTenantEntitled(tenantId, ModuleCode.PAYROLL));
        assertFalse(entitlementService.isTenantEntitled(tenantId, ModuleCode.HRMS));
    }

    @Test
    void shouldBuildNavigationFeedOmittingUnsubscribedModules() {
        UUID tenantId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        SubscriptionModule payrollMod = new SubscriptionModule(tenantId, subId, ModuleCode.PAYROLL);

        when(subscriptionModuleRepository.findByTenantIdAndStatus(tenantId, "ACTIVE"))
                .thenReturn(List.of(payrollMod));

        TenantEntitlementResponse response = entitlementService.getTenantEntitlements(tenantId);

        assertEquals(tenantId, response.tenantId());
        assertTrue(response.modules().contains(ModuleCode.CORE));
        assertTrue(response.modules().contains(ModuleCode.PAYROLL));
        assertFalse(response.modules().contains(ModuleCode.HRMS));

        boolean hasHrmsNav = response.navigation().stream().anyMatch(nav -> nav.module() == ModuleCode.HRMS);
        boolean hasPayrollNav = response.navigation().stream().anyMatch(nav -> nav.module() == ModuleCode.PAYROLL);

        assertFalse(hasHrmsNav, "Payroll-only tenant must not receive HRMS navigation items");
        assertTrue(hasPayrollNav, "Payroll-only tenant must receive Payroll navigation items");
    }
}
