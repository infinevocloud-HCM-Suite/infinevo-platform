package com.infinevo.core.invitation;

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
            "com.infinevo.shared.identity"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.core.invitation",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.core.tenant",
            "com.infinevo.shared.identity"
        })
@Import(UserProfileSyncService.class)
public class InvitationTestApp {}
