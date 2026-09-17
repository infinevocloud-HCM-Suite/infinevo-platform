package com.infinevo.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * W-06 — Flyway migration runner.
 *
 * <p>Lets Spring Boot's Flyway autoconfiguration run against the configured datasource
 * (migration_user), then exits. With {@code spring.main.web-application-type=none}
 * there is no non-daemon thread, so the context closes and the JVM exits on its own
 * — exit 0 on success, non-zero on any Flyway failure.
 *
 * <p>No CommandLineRunner, no datasource bean, no JPA. The only job is to let Boot
 * wire and run Flyway, then stop.
 */
@SpringBootApplication
public class MigrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigrationApplication.class, args);
    }
}
