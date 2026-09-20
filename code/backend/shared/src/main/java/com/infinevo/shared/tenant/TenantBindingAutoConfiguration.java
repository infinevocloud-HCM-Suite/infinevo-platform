package com.infinevo.shared.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Boot AutoConfiguration for registering tenant authentication extraction,
 * membership service, {@link TenantContextFilter}, {@link TenantBindingDataSourceProxy},
 * and default security filter chain.
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
        // Order -90: Runs AFTER Spring Security authentication (-100) so SecurityContextHolder contains valid auth
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 10);
        return registration;
    }

    @Bean
    public static BeanPostProcessor tenantBindingDataSourceBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof DataSource ds && !(bean instanceof TenantBindingDataSourceProxy)) {
                    return new TenantBindingDataSourceProxy(ds);
                }
                return bean;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain tenantDefaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.requestMatchers(
                        "/actuator/health",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api/v1/auth/login")
                .permitAll()
                .anyRequest()
                .authenticated());
        return http.build();
    }
}
