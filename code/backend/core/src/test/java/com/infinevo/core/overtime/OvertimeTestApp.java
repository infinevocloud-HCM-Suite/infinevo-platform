package com.infinevo.core.overtime;

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
 * The context overtime's integration tests share (W-39.2) — {@code NotificationTestApp}'s shape:
 * {@code core.employee} and {@code core.org} alongside the feature package, since
 * {@code OvertimeServiceImpl} validates the employee directly, the same reason
 * {@code NotificationServiceImpl.compose} needs them. {@code core.payinput} is this feature's own
 * addition: {@code OvertimeServiceImpl} calls the real {@code PayInputService}, in the same
 * transaction, not a stand-in — {@code OvertimeLedgerIT} is the point of doing that.
 *
 * <p>{@code RedisConfig} is not optional, the reason {@code PayInputTestApp} gives:
 * {@code PermissionCache.actionsOf} — every {@code @RequiresAction} check, not only a role-change
 * invalidation path — requires a working {@code CacheService} and fails closed without one.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "com.infinevo.core.overtime",
            "com.infinevo.core.payinput",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org"
        },
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
        })
@EntityScan(
        basePackages = {
            "com.infinevo.core.overtime",
            "com.infinevo.core.payinput",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.shared.identity"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.core.overtime",
            "com.infinevo.core.payinput",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.shared.identity"
        })
@Import({RedisConfig.class, UserProfileSyncService.class})
public class OvertimeTestApp {}
