package com.infinevo.payroll;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spring Boot test application for the payroll component integration tests (W-26.1).
 */
@SpringBootApplication(scanBasePackages = {"com.infinevo.payroll.component"})
@EntityScan(basePackages = {"com.infinevo.payroll.component"})
@EnableJpaRepositories(basePackages = {"com.infinevo.payroll.component"})
public class PayrollTestApp {}
