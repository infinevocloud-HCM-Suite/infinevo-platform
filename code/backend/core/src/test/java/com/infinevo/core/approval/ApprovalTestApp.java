package com.infinevo.core.approval;

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
 * Spring Boot configuration for approval integration tests (W-15.1, W-15.2).
 *
 * <p>Excludes any other {@code @SpringBootConfiguration} classes so that test applications in
 * scanned packages (like {@code AuthzTestApp} and {@code CoreFeatureTestApp}) are not picked up,
 * avoiding duplicate repository registrations and {@code BeanDefinitionOverrideException}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "com.infinevo.core.approval",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.shared.identity"
        },
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
        })
@EntityScan(
        basePackages = {
            "com.infinevo.core.approval",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.shared.identity"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.core.approval",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.shared.identity"
        })
@Import(UserProfileSyncService.class)
public class ApprovalTestApp {}
