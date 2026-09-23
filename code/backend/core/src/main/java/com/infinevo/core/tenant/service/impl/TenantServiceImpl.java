package com.infinevo.core.tenant.service.impl;

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
import com.infinevo.core.tenant.service.TenantService;
import com.infinevo.shared.entitlement.ModuleCode;
import com.infinevo.shared.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final UserTenantRepository userTenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionModuleRepository subscriptionModuleRepository;

    public TenantServiceImpl(
            TenantRepository tenantRepository,
            UserTenantRepository userTenantRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionModuleRepository subscriptionModuleRepository) {
        this.tenantRepository = tenantRepository;
        this.userTenantRepository = userTenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionModuleRepository = subscriptionModuleRepository;
    }

    @Override
    @Transactional
    public OrganisationResponse createOrganisation(CreateOrganisationRequest request, UUID creatorUserId) {
        UUID tenantId = UUID.randomUUID();
        TenantEntity tenant = new TenantEntity(tenantId, request.name());

        UUID previousTenantId = TenantContext.current().orElse(null);
        TenantContext.set(tenantId);
        try {
            tenantRepository.save(tenant);

            if (creatorUserId != null) {
                UserTenant userTenant = new UserTenant(tenantId, creatorUserId);
                userTenantRepository.save(userTenant);
            }

            Subscription subscription = new Subscription(tenantId, "ACTIVE");
            subscription = subscriptionRepository.save(subscription);

            List<ModuleCode> grantedModules = new ArrayList<>();
            grantedModules.add(ModuleCode.CORE);
            subscriptionModuleRepository.save(new SubscriptionModule(tenantId, subscription.getId(), ModuleCode.CORE));

            if (request.modules() != null) {
                for (ModuleCode mod : request.modules()) {
                    if (mod != ModuleCode.CORE && !grantedModules.contains(mod)) {
                        subscriptionModuleRepository.save(new SubscriptionModule(tenantId, subscription.getId(), mod));
                        grantedModules.add(mod);
                    }
                }
            }

            return new OrganisationResponse(tenantId, tenant.getName(), subscription.getStatus(), grantedModules);
        } finally {
            if (previousTenantId != null) {
                TenantContext.set(previousTenantId);
            } else {
                TenantContext.clear();
            }
        }
    }
}
