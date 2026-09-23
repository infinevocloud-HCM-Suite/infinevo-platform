package com.infinevo.core.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.tenant.dto.CreateOrganisationRequest;
import com.infinevo.core.tenant.dto.OrganisationResponse;
import com.infinevo.core.tenant.entity.Subscription;
import com.infinevo.core.tenant.entity.SubscriptionModule;
import com.infinevo.core.tenant.entity.TenantEntity;
import com.infinevo.core.tenant.entity.UserTenant;
import com.infinevo.core.tenant.repository.SubscriptionModuleRepository;
import com.infinevo.core.tenant.repository.SubscriptionRepository;
import com.infinevo.core.tenant.repository.TenantRepository;
import com.infinevo.core.tenant.repository.UserTenantRepository;
import com.infinevo.core.tenant.service.impl.TenantServiceImpl;
import com.infinevo.shared.entitlement.ModuleCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TenantServiceImplTest {

    private TenantRepository tenantRepository;
    private UserTenantRepository userTenantRepository;
    private SubscriptionRepository subscriptionRepository;
    private SubscriptionModuleRepository subscriptionModuleRepository;
    private TenantServiceImpl tenantService;

    @BeforeEach
    void setUp() {
        tenantRepository = Mockito.mock(TenantRepository.class);
        userTenantRepository = Mockito.mock(UserTenantRepository.class);
        subscriptionRepository = Mockito.mock(SubscriptionRepository.class);
        subscriptionModuleRepository = Mockito.mock(SubscriptionModuleRepository.class);
        tenantService = new TenantServiceImpl(
                tenantRepository, userTenantRepository, subscriptionRepository, subscriptionModuleRepository);
    }

    @Test
    void shouldCreateOrganisationWithSelectedModules() {
        UUID creatorUserId = UUID.randomUUID();
        CreateOrganisationRequest request = new CreateOrganisationRequest("Acme Corp", List.of(ModuleCode.PAYROLL));

        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> {
            Subscription s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        OrganisationResponse response = tenantService.createOrganisation(request, creatorUserId);

        assertNotNull(response.tenantId());
        assertEquals("Acme Corp", response.name());
        assertEquals("ACTIVE", response.status());
        assertTrue(response.modules().contains(ModuleCode.CORE));
        assertTrue(response.modules().contains(ModuleCode.PAYROLL));

        verify(tenantRepository).save(any(TenantEntity.class));
        verify(userTenantRepository).save(any(UserTenant.class));
        verify(subscriptionRepository).save(any(Subscription.class));
        verify(subscriptionModuleRepository, atLeastOnce()).save(any(SubscriptionModule.class));
    }
}
