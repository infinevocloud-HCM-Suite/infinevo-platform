package com.infinevo.core.guard;

import com.infinevo.shared.cache.RedisConfig;
import com.infinevo.shared.identity.UserProfileSyncService;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The Spring context {@code PermissionGuardIT} runs in (W-11.2): every guarded core controller, over
 * HTTP, with the permission check wired the shipped way.
 *
 * <p>What it holds and why:
 *
 * <ul>
 *   <li>{@code com.infinevo.core.authz}, {@code .employee} and {@code .org} — the controllers under
 *       test, their services, and {@code PermissionReadServiceImpl} as the {@code ActionSource}.
 *   <li>{@link RedisConfig}, imported by name — the real {@code CacheService} over the test Redis, so
 *       the version bump is exercised against the store every replica shares.
 *   <li>{@link UserProfileSyncService}, imported by name — what the default
 *       {@code UserAccountIdResolver} maps the token subject to a profile row through.
 *   <li>{@code AuthzAutoConfiguration} and {@code TenantBindingAutoConfiguration} arrive by
 *       auto-configuration, as they do in the applications.
 * </ul>
 *
 * <p><strong>Its own package, and it excludes every other {@code @SpringBootConfiguration}.</strong>
 * It scans {@code com.infinevo.core.authz}, where {@code AuthzTestApp} sits; picking that up would
 * register the authz repositories a second time — the collision {@code CoreFeatureTestApp} documents.
 * And it lives outside the three scanned packages so that neither {@code AuthzTestApp} nor
 * {@code CoreFeatureTestApp} finds it.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {"com.infinevo.core.authz", "com.infinevo.core.employee", "com.infinevo.core.org"},
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
        })
@EntityScan(
        basePackages = {
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.shared.identity"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.shared.identity"
        })
@Import({RedisConfig.class, UserProfileSyncService.class})
public class PermissionGuardTestApp {}
