package com.infinevo.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * W-06 — Flyway migration runner.
 *
 * <p>Lets Spring Boot's Flyway autoconfiguration run against the configured datasource
 * (migration_user), then exits. With {@code spring.main.web-application-type=none} there is no
 * non-daemon thread, so the context closes and the JVM exits on its own — exit 0 on success,
 * non-zero on any Flyway failure.
 *
 * <p>DataSourceAutoConfiguration is excluded because Flyway connects via its own {@code
 * spring.flyway.url/user/password} properties; no shared HikariCP pool is needed or desired in
 * this run-once migration runner (spec §4 file 3, D-46).
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class MigrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigrationApplication.class, args);
    }

    /**
     * Boots the real application context with the given CLI-style arguments ({@code --key=value}).
     * Used by integration tests so they exercise the same Spring Boot auto-configuration chain and
     * {@code application.yml} that ships, rather than a hand-built Flyway instance.
     */
    static ConfigurableApplicationContext launch(String... args) {
        return SpringApplication.run(MigrationApplication.class, args);
    }
}
