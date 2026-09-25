package com.infinevo.core.report;

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
 * The context the export integration tests share (W-23.1) — one for all four, so the connection
 * budget ({@code W-04.1}) pays for one pool.
 *
 * <p>{@code DocumentTestApp}'s shape plus {@code core.report}: the export writes through the document
 * store, and its three sources read the employee, the org masters and the audit log — whose entity is
 * {@code shared.audit}'s, scanned for the entity only.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "com.infinevo.core.report",
            "com.infinevo.core.document",
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
            "com.infinevo.core.report",
            "com.infinevo.core.document",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.shared.identity",
            "com.infinevo.shared.audit"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.core.report",
            "com.infinevo.core.document",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.shared.identity"
        })
@Import({RedisConfig.class, UserProfileSyncService.class})
public class ReportTestApp {}
