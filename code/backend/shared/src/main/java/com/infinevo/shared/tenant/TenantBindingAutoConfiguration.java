package com.infinevo.shared.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Spring Boot AutoConfiguration for registering tenant authentication extraction,
 * membership service, and {@link TenantContextFilter}.
 */
@AutoConfiguration
@ConditionalOnClass(FilterRegistrationBean.class)
public class TenantBindingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TenantAuthenticationExtractor tenantAuthenticationExtractor() {
        return new TenantAuthenticationExtractor.DefaultTenantAuthenticationExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantMembershipService tenantMembershipService(DataSource dataSource) {
        return new TenantMembershipService(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantContextFilter tenantContextFilter(
            TenantAuthenticationExtractor extractor,
            TenantMembershipService membershipService,
            ObjectMapper objectMapper) {
        return new TenantContextFilter(extractor, membershipService, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(TenantContextFilter filter) {
        FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
