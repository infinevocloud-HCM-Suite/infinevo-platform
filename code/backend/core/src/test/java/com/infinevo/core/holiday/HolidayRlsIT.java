package com.infinevo.core.holiday;

import static com.infinevo.core.holiday.HolidayTestSchema.TENANT_A;
import static com.infinevo.core.holiday.HolidayTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-17 — Row-level security: tenant A cannot read tenant B's holiday calendars or holidays.
 */
@SpringBootTest(classes = HolidayTestApp.class, properties = "spring.main.allow-bean-definition-overriding=true")
class HolidayRlsIT extends AbstractIntegrationTest {

    @BeforeAll
    static void applySchema() throws Exception {
        HolidayTestSchema.apply();
        HolidayTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        HolidayTestSchema.clearAll();
    }

    @BeforeEach
    void seedData() throws SQLException {
        HolidayTestSchema.clearAll();

        try (Connection conn = HolidayTestSchema.migrationConnection()) {
            UUID calA = UUID.randomUUID();
            UUID calB = UUID.randomUUID();

            // Seed Calendar A
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.holiday_calendar (id, tenant_id, name, is_default) VALUES (?, ?, 'Acme Standard', true)")) {
                ps.setObject(1, calA);
                ps.setObject(2, TENANT_A);
                ps.executeUpdate();
            }

            // Seed Calendar B
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.holiday_calendar (id, tenant_id, name, is_default) VALUES (?, ?, 'Globex Standard', true)")) {
                ps.setObject(1, calB);
                ps.setObject(2, TENANT_B);
                ps.executeUpdate();
            }

            // Seed Holiday for A
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.holiday (id, tenant_id, calendar_id, name, from_date, to_date) VALUES (?, ?, ?, 'Acme Holiday', '2026-08-15', '2026-08-15')")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_A);
                ps.setObject(3, calA);
                ps.executeUpdate();
            }

            // Seed Holiday for B
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.holiday (id, tenant_id, calendar_id, name, from_date, to_date) VALUES (?, ?, ?, 'Globex Holiday', '2026-09-01', '2026-09-01')")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_B);
                ps.setObject(3, calB);
                ps.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName("app_user bound to Tenant A sees only Tenant A calendars and holidays")
    void appUserBoundToTenantASeesOnlyTenantA() throws SQLException {
        try (Connection conn = HolidayTestSchema.appConnection()) {
            setTenantContext(conn, TENANT_A);

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT tenant_id FROM core.holiday_calendar")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    assertThat((UUID) rs.getObject("tenant_id")).isEqualTo(TENANT_A);
                }
                assertThat(count).isEqualTo(1);
            }

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT tenant_id FROM core.holiday")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    assertThat((UUID) rs.getObject("tenant_id")).isEqualTo(TENANT_A);
                }
                assertThat(count).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("app_user bound to Tenant B sees only Tenant B calendars and holidays")
    void appUserBoundToTenantBSeesOnlyTenantB() throws SQLException {
        try (Connection conn = HolidayTestSchema.appConnection()) {
            setTenantContext(conn, TENANT_B);

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT tenant_id FROM core.holiday_calendar")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    assertThat((UUID) rs.getObject("tenant_id")).isEqualTo(TENANT_B);
                }
                assertThat(count).isEqualTo(1);
            }

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT tenant_id FROM core.holiday")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    assertThat((UUID) rs.getObject("tenant_id")).isEqualTo(TENANT_B);
                }
                assertThat(count).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("unbound app_user sees 0 rows under RLS")
    void unboundAppUserSeesZeroRows() throws SQLException {
        try (Connection conn = HolidayTestSchema.appConnection()) {
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.holiday_calendar")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.holiday")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    private void setTenantContext(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }
}
