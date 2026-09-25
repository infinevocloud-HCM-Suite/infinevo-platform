package com.infinevo.core.subscription;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spring Boot configuration for subscription integration tests (W-12.1).
 */
@SpringBootApplication(scanBasePackages = {"com.infinevo.core.subscription", "com.infinevo.core.tenant"})
@EntityScan(basePackages = {"com.infinevo.core.subscription"})
@EnableJpaRepositories(basePackages = {"com.infinevo.core.subscription"})
public class SubscriptionTestApp {}
