package com.infinevo.core.employeeimport;

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
 * Spring Boot context for the bulk import integration tests (W-73.7): the invitation context
 * ({@code InvitationTestApp}) plus the import service and the job service it queues through. The
 * document store is a {@code @MockBean} in each test, which captures the result file.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "com.infinevo.core.employeeimport",
            "com.infinevo.core.invitation",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.core.tenant",
            "com.infinevo.core.job",
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
            "com.infinevo.core.job",
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
            "com.infinevo.core.job",
            "com.infinevo.shared.identity",
            "com.infinevo.shared.audit"
        })
@Import({UserProfileSyncService.class, AuditWriter.class, AuditIntegratorConfig.class})
public class EmployeeImportTestApp {}
