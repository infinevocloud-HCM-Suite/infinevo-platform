package com.infinevo.shared.entitlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.tenant.TenantBindingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot AutoConfiguration for registering {@link EntitlementEnforcementFilter}.
 * Runs after {@link TenantBindingAutoConfiguration} so {@link com.infinevo.shared.tenant.TenantContext} is bound.
 */
@AutoConfiguration
@AutoConfigureAfter(TenantBindingAutoConfiguration.class)
@ConditionalOnClass(FilterRegistrationBean.class)
public class EntitlementAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EntitlementEnforcementFilter entitlementEnforcementFilter(
            org.springframework.beans.factory.ObjectProvider<EntitlementChecker> entitlementCheckerProvider,
            ObjectMapper objectMapper) {
        return new EntitlementEnforcementFilter(entitlementCheckerProvider.getIfAvailable(), objectMapper);
    }

    @Bean
    public FilterRegistrationBean<EntitlementEnforcementFilter> entitlementEnforcementFilterRegistration(
            EntitlementEnforcementFilter filter) {
        FilterRegistrationBean<EntitlementEnforcementFilter> registration = new FilterRegistrationBean<>(filter);
        // Order -80: Runs AFTER TenantContextFilter (-90) so TenantContext is bound
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 20);
        return registration;
    }
}
