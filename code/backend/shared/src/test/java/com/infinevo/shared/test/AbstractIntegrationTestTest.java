package com.infinevo.shared.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-04 — Verifies that {@link AbstractIntegrationTest} connects to PostgreSQL
 * as the non-owner {@code app_user} role, not as the container owner.
 *
 * <p>This test uses a minimal {@link SpringBootApplication} context (inner class)
 * so the shared module does not need a production application class.
 */
@SpringBootTest(classes = AbstractIntegrationTestTest.TestApp.class)
class AbstractIntegrationTestTest extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {
        // Minimal Spring Boot context for testing the integration test infrastructure.
        // No domain entities, no production configuration — shared module owns no domain.
    }

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("connects to PostgreSQL as app_user, not as the database owner")
    void connectsAsAppUser() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                ResultSet rs = conn.createStatement().executeQuery("SELECT current_user")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo(PostgresTestContainerInitializer.APP_USER);
        }
    }

    @Test
    @DisplayName("database name is the platform database: infinevo")
    void databaseNameIsInfinevo() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                ResultSet rs = conn.createStatement().executeQuery("SELECT current_database()")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo(PostgresTestContainerInitializer.DATABASE_NAME);
        }
    }

    @Test
    @DisplayName("app_user does not have SUPERUSER or BYPASSRLS privileges")
    void appUserLacksOwnerPrivileges() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean("rolsuper"))
                    .as("app_user must not be a superuser")
                    .isFalse();
            assertThat(rs.getBoolean("rolbypassrls"))
                    .as("app_user must not bypass RLS policies")
                    .isFalse();
        }
    }
}
