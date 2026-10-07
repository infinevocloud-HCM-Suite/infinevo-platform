package com.infinevo.core.invitation;

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
 * Spring Boot context for invitation integration tests (W-24.2).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "com.infinevo.core.invitation",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.core.tenant",
            "com.infinevo.shared.authz"
        },
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
        })
@EntityScan(
        basePackages = {
            "com.infinevo.core.invitation",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.core.tenant",
            "com.infinevo.shared.identity",
            "com.infinevo.shared.audit"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.core.invitation",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.core.tenant",
            "com.infinevo.shared.identity",
            "com.infinevo.shared.audit"
        })
// The audit listener is part of this context on purpose: acceptance links an @Audited employee to its
// account, and the audit row needs the tenant at commit. Without the listener the suite could not see
// the 500 that Azure dev answered on every employee acceptance (2026-10-07).
@Import({UserProfileSyncService.class, AuditWriter.class, AuditIntegratorConfig.class})
public class InvitationTestApp {}
