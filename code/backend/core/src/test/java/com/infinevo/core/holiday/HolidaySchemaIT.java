package com.infinevo.core.holiday;

import static com.infinevo.core.holiday.HolidayTestSchema.TENANT_A;
import static com.infinevo.core.holiday.HolidayTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * W-17 F-6 — database guarantees of {@code V036}: the date-range check constraint on
 * {@code core.holiday}, and row-level security on {@code core.holiday_calendar_location}.
 */
@SpringBootTest(classes = HolidayTestApp.class, properties = "spring.main.allow-bean-definition-overriding=true")
class HolidaySchemaIT extends AbstractIntegrationTest {

    private UUID calendarA;

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
    void seed() throws SQLException {
        HolidayTestSchema.clearAll();
        calendarA = UUID.randomUUID();
        UUID calendarB = UUID.randomUUID();
        try (Connection conn = HolidayTestSchema.migrationConnection()) {
            insertCalendar(conn, calendarA, TENANT_A);
            insertCalendar(conn, calendarB, TENANT_B);
            insertLink(conn, TENANT_A, calendarA, insertLocation(conn, TENANT_A, "LOC_SA"));
            insertLink(conn, TENANT_B, calendarB, insertLocation(conn, TENANT_B, "LOC_SB"));
        }
    }

    @Test
    @DisplayName("chk_holiday_date_range refuses a holiday whose to_date is before its from_date")
    void invertedRange_refusedByCheckConstraint() throws SQLException {
        try (Connection conn = HolidayTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.holiday (id, tenant_id, calendar_id, name, from_date, to_date)"
                                + " VALUES (?, ?, ?, 'Backwards', '2026-08-15', '2026-08-14')")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, TENANT_A);
            ps.setObject(3, calendarA);

            assertThatThrownBy(ps::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_holiday_date_range");
        }
    }

    @Test
    @DisplayName("RLS: app_user bound to Tenant A sees only Tenant A's calendar-location links")
    void locationLinks_tenantIsolated() throws SQLException {
        try (Connection conn = HolidayTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant_id = '" + TENANT_A + "'");
            }
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT tenant_id FROM core.holiday_calendar_location")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    assertThat((UUID) rs.getObject("tenant_id")).isEqualTo(TENANT_A);
                }
                assertThat(count).isEqualTo(1);
            }
            conn.rollback();
        }
    }

    @Test
    @DisplayName("RLS: unbound app_user sees no calendar-location links")
    void locationLinks_unboundSeesNothing() throws SQLException {
        try (Connection conn = HolidayTestSchema.appConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.holiday_calendar_location")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    private static void insertCalendar(Connection conn, UUID id, UUID tenant) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.holiday_calendar (id, tenant_id, name, is_default) VALUES (?, ?, 'Cal', true)")) {
            ps.setObject(1, id);
            ps.setObject(2, tenant);
            ps.executeUpdate();
        }
    }

    private static UUID insertLocation(Connection conn, UUID tenant, String code) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.work_location (id, tenant_id, code, name, country_code, is_filing_address,"
                        + " is_active) VALUES (?, ?, ?, 'Location', 'IN', true, true)")) {
            ps.setObject(1, id);
            ps.setObject(2, tenant);
            ps.setString(3, code);
            ps.executeUpdate();
        }
        return id;
    }

    private static void insertLink(Connection conn, UUID tenant, UUID calendar, UUID location) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.holiday_calendar_location (id, tenant_id, calendar_id, work_location_id)"
                        + " VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenant);
            ps.setObject(3, calendar);
            ps.setObject(4, location);
            ps.executeUpdate();
        }
    }
}
