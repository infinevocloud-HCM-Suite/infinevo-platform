package com.infinevo.payroll.schedule;

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
 * Spring Boot application context for pay schedule integration tests (W-28).
 *
 * <p>Scans only the {@code payroll.schedule} and necessary {@code core.lop} packages so
 * that the {@link PayScheduleWorkingWeekSource} bean satisfies the
 * {@link com.infinevo.core.lop.WorkingWeekSource} autowiring in the
 * {@link com.infinevo.core.lop.WorkingDayBasisCalculator}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "com.infinevo.payroll.schedule",
            "com.infinevo.payroll.setup",
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
// shared.identity: EmployeeServiceImpl needs UserAccountRepository since W-13.4.
@EntityScan(
        basePackages = {
            "com.infinevo.payroll.schedule",
            "com.infinevo.core.lop",
            "com.infinevo.core.holiday",
            "com.infinevo.core.org",
            "com.infinevo.core.employee",
            "com.infinevo.shared.identity"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.payroll.schedule",
            "com.infinevo.core.lop",
            "com.infinevo.core.holiday",
            "com.infinevo.core.org",
            "com.infinevo.core.employee",
            "com.infinevo.shared.identity"
        })
// UserProfileSyncService: EmployeeServiceImpl constructor dependency since W-13.4.
@Import(UserProfileSyncService.class)
public class PayScheduleTestApp {}
