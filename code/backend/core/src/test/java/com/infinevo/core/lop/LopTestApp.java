package com.infinevo.core.lop;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spring Boot application context for loss-of-pay integration tests (W-18.1).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "com.infinevo.core.lop",
            "com.infinevo.core.holiday",
            "com.infinevo.core.org",
            "com.infinevo.core.employee",
            "com.infinevo.core.tenant",
            "com.infinevo.shared.authz"
        },
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
        })
@EntityScan(
        basePackages = {
            "com.infinevo.core.lop",
            "com.infinevo.core.holiday",
            "com.infinevo.core.org",
            "com.infinevo.core.employee"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.core.lop",
            "com.infinevo.core.holiday",
            "com.infinevo.core.org",
            "com.infinevo.core.employee"
        })
public class LopTestApp {}
