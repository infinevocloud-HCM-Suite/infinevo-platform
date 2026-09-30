package com.infinevo.core.leave;

import com.infinevo.shared.audit.AuditIntegratorConfig;
import com.infinevo.shared.audit.AuditWriter;
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
 * Spring Boot test configuration for leave integration tests (W-16.1, W-16.2).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "com.infinevo.core.leave",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.core.approval",
            "com.infinevo.core.authz",
            "com.infinevo.core.payinput"
        },
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
        })
@EntityScan(
        basePackages = {
            "com.infinevo.core.leave",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.core.approval",
            "com.infinevo.core.authz",
            "com.infinevo.core.payinput",
            "com.infinevo.core.document",
            "com.infinevo.shared.audit",
            "com.infinevo.shared.identity"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.core.leave",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.core.approval",
            "com.infinevo.core.authz",
            "com.infinevo.core.payinput",
            "com.infinevo.core.document",
            "com.infinevo.shared.audit",
            "com.infinevo.shared.identity"
        })
@Import({AuditWriter.class, AuditIntegratorConfig.class, UserProfileSyncService.class})
public class LeaveTestApp {}
