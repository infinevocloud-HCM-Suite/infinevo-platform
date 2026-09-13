package com.itsdev.payroll.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link OrganizationRoleInterceptor} for all API routes so the
 * Admin/Employee portal boundary is enforced server-side.
 *
 * Public routes are excluded; they are already {@code permitAll()} in
 * {@link SecurityConfig} and must remain reachable without an organization role.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final OrganizationRoleInterceptor organizationRoleInterceptor;

    public WebConfig(OrganizationRoleInterceptor organizationRoleInterceptor) {
        this.organizationRoleInterceptor = organizationRoleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(organizationRoleInterceptor)
                .addPathPatterns("/api/**", "/admin/**")
                .excludePathPatterns("/api/public/**");
    }
}
