package com.infinevo.shared.entitlement;

import com.infinevo.shared.authz.PermissionCache;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.tenant.TenantSuspensionAspect;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for entitlement enforcement and subscription write guards (W-12.2, W-85.1).
 */
@AutoConfiguration
@ConditionalOnClass(Aspect.class)
public class EntitlementAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EntitlementService entitlementService(
            ObjectProvider<PermissionCache> permissionCache, ObjectProvider<EntitlementSource> entitlementSource) {
        return new EntitlementService(permissionCache::getIfAvailable, entitlementSource::getIfAvailable);
    }

    @Bean
    @ConditionalOnMissingBean
    public RequiresModuleAspect requiresModuleAspect(EntitlementService entitlementService) {
        return new RequiresModuleAspect(entitlementService);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantSuspensionAspect tenantSuspensionAspect(
            EntitlementService entitlementService,
            ObjectProvider<PlatformTenant> platformTenant,
            @Value("${infinevo.security.suspension-guard.enabled:true}") boolean enabled) {
        return new TenantSuspensionAspect(
                entitlementService, platformTenant.getIfAvailable(PlatformTenant::new), enabled);
    }
}
