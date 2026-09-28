package com.infinevo.core.leave;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spring Boot test configuration for leave integration tests (W-16.1).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {"com.infinevo.core.leave", "com.infinevo.core.employee", "com.infinevo.core.org"},
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
        })
@EntityScan(basePackages = {"com.infinevo.core.leave", "com.infinevo.core.employee", "com.infinevo.core.org"})
@EnableJpaRepositories(
        basePackages = {"com.infinevo.core.leave", "com.infinevo.core.employee", "com.infinevo.core.org"})
public class LeaveTestApp {}
