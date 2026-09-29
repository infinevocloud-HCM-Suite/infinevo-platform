package com.infinevo.core.payinput;

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
 * The context the pay input ledger's integration tests share (W-19) — {@code DocumentTestApp}'s
 * shape.
 *
 * <p>{@code core.payinput} needs no service-level knowledge of {@code core.employee}:
 * {@code employee_id} is a plain column, checked by the database foreign key
 * ({@code V031__pay_input.sql}), not resolved through a Java repository. So this context, unlike
 * {@code NotificationTestApp} or {@code DocumentTestApp}, scans nothing from {@code core.employee}
 * or {@code core.org} — only {@code core.authz}, for the {@code @RequiresAction} guard
 * {@code PayInputGuardIT} exercises, and {@code shared.identity} for the profile lookup that guard
 * needs.
 *
 * <p>{@code RedisConfig} is not optional here, even though nothing in this feature caches anything of
 * its own: {@code PermissionCache.actionsOf} — what every {@code @RequiresAction} check calls, not
 * only a role-change invalidation path — requires a working {@code CacheService} and fails closed with
 * {@code CacheOperationException} when none is configured ({@code PermissionCache.requireCache}). So
 * every guarded endpoint in {@code core}, this one included, needs Redis in its test context.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {"com.infinevo.core.payinput", "com.infinevo.core.authz"},
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
        })
@EntityScan(basePackages = {"com.infinevo.core.payinput", "com.infinevo.core.authz", "com.infinevo.shared.identity"})
@EnableJpaRepositories(
        basePackages = {"com.infinevo.core.payinput", "com.infinevo.core.authz", "com.infinevo.shared.identity"})
@Import({RedisConfig.class, UserProfileSyncService.class})
public class PayInputTestApp {}
