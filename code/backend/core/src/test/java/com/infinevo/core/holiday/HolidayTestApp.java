package com.infinevo.core.holiday;

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
 * Spring Boot application context for holiday calendar integration tests (W-17).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "com.infinevo.core.holiday",
            "com.infinevo.core.org",
            "com.infinevo.core.employee",
            "com.infinevo.shared.authz"
        },
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
        })
// shared.identity: EmployeeServiceImpl needs UserAccountRepository since W-13.4.
@EntityScan(
        basePackages = {
            "com.infinevo.core.holiday",
            "com.infinevo.core.org",
            "com.infinevo.core.employee",
            "com.infinevo.shared.identity"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.core.holiday",
            "com.infinevo.core.org",
            "com.infinevo.core.employee",
            "com.infinevo.shared.identity"
        })
// UserProfileSyncService: EmployeeServiceImpl constructor dependency since W-13.4.
@Import(UserProfileSyncService.class)
public class HolidayTestApp {}
