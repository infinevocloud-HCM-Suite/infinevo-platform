package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.tenant.dto.CreateOrganisationRequest;
import com.infinevo.core.tenant.dto.OrganisationResponse;
import com.infinevo.core.tenant.dto.TenantEntitlementResponse;
import com.infinevo.core.tenant.service.EntitlementService;
import com.infinevo.core.tenant.service.TenantService;
import com.infinevo.shared.entitlement.ModuleCode;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EntitlementEnforcementIT extends AbstractIntegrationTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private EntitlementService entitlementService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "Creates Payroll-only tenant and verifies HRMS endpoint entitlement is denied while Payroll is granted")
    void verifiesPayrollOnlyTenantEntitlement() {
        UUID creatorId = UUID.randomUUID();
        CreateOrganisationRequest request = new CreateOrganisationRequest("Payroll Corp", List.of(ModuleCode.PAYROLL));

        OrganisationResponse org = tenantService.createOrganisation(request, creatorId);
        UUID tenantId = org.tenantId();

        TenantContext.set(tenantId);

        assertThat(entitlementService.isTenantEntitled(tenantId, ModuleCode.PAYROLL))
                .isTrue();
        assertThat(entitlementService.isTenantEntitled(tenantId, ModuleCode.CORE))
                .isTrue();
        assertThat(entitlementService.isTenantEntitled(tenantId, ModuleCode.HRMS))
                .isFalse();

        TenantEntitlementResponse entitlements = entitlementService.getTenantEntitlements(tenantId);
        assertThat(entitlements.modules()).contains(ModuleCode.CORE, ModuleCode.PAYROLL);
        assertThat(entitlements.modules()).doesNotContain(ModuleCode.HRMS);

        boolean hasHrmsMenu = entitlements.navigation().stream().anyMatch(nav -> nav.module() == ModuleCode.HRMS);
        assertThat(hasHrmsMenu).isFalse();
    }

    @Test
    @DisplayName("Creates Dual-module tenant and verifies both HRMS and Payroll entitlements are granted")
    void verifiesDualModuleTenantEntitlement() {
        UUID creatorId = UUID.randomUUID();
        CreateOrganisationRequest request =
                new CreateOrganisationRequest("Full Suite Corp", List.of(ModuleCode.HRMS, ModuleCode.PAYROLL));

        OrganisationResponse org = tenantService.createOrganisation(request, creatorId);
        UUID tenantId = org.tenantId();

        TenantContext.set(tenantId);

        assertThat(entitlementService.isTenantEntitled(tenantId, ModuleCode.PAYROLL))
                .isTrue();
        assertThat(entitlementService.isTenantEntitled(tenantId, ModuleCode.HRMS))
                .isTrue();

        TenantEntitlementResponse entitlements = entitlementService.getTenantEntitlements(tenantId);
        assertThat(entitlements.modules()).contains(ModuleCode.CORE, ModuleCode.HRMS, ModuleCode.PAYROLL);

        boolean hasHrmsMenu = entitlements.navigation().stream().anyMatch(nav -> nav.module() == ModuleCode.HRMS);
        boolean hasPayrollMenu = entitlements.navigation().stream().anyMatch(nav -> nav.module() == ModuleCode.PAYROLL);

        assertThat(hasHrmsMenu).isTrue();
        assertThat(hasPayrollMenu).isTrue();
    }
}
