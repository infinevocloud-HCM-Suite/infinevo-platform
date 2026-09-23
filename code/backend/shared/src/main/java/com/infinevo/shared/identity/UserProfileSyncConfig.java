package com.infinevo.shared.identity;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Puts {@link UserProfileSyncFilter} in the servlet chain, after the tenant is bound (W-10).
 *
 * <p>The order is the contract. {@code TenantContextFilter} registers at
 * {@code DEFAULT_FILTER_ORDER + 10} ({@code TenantBindingAutoConfiguration}); this registers at
 * {@code + 20}, so the sequence is authentication (Spring Security, {@code DEFAULT_FILTER_ORDER}),
 * then tenant binding, then profile sync — spec section 3. Reverse any two of those and the sync
 * either has no principal or no tenant.
 */
@Configuration(proxyBeanMethods = false)
public class UserProfileSyncConfig {

    /** Ten after {@code TenantContextFilter}, which is ten after Spring Security. */
    public static final int FILTER_ORDER = SecurityProperties.DEFAULT_FILTER_ORDER + 20;

    @Bean
    public UserProfileSyncFilter userProfileSyncFilter(UserProfileSyncService syncService) {
        return new UserProfileSyncFilter(syncService);
    }

    @Bean
    public FilterRegistrationBean<UserProfileSyncFilter> userProfileSyncFilterRegistration(
            UserProfileSyncFilter filter) {
        FilterRegistrationBean<UserProfileSyncFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(FILTER_ORDER);
        return registration;
    }
}
