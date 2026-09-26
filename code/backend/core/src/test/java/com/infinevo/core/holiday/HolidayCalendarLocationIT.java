package com.infinevo.core.holiday;

import static com.infinevo.core.holiday.HolidayTestSchema.TENANT_A;
import static com.infinevo.core.holiday.HolidayTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-17 — Holiday calendar location assignment and default calendar enforcement.
 */
@SpringBootTest(classes = HolidayTestApp.class, properties = "spring.main.allow-bean-definition-overriding=true")
class HolidayCalendarLocationIT extends AbstractIntegrationTest {

    @Autowired
    private HolidayCalendarService calendarService;

    private UUID locationTenantA;
    private UUID locationTenantB;

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
    void seedLocations() throws SQLException {
        HolidayTestSchema.clearAll();

        locationTenantA = UUID.randomUUID();
        locationTenantB = UUID.randomUUID();

        try (Connection conn = HolidayTestSchema.migrationConnection()) {
            // Seed work location for Tenant A
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.work_location (id, tenant_id, code, name, country_code, is_filing_address, is_active) "
                            + "VALUES (?, ?, 'LOC_A', 'Location A', 'IN', true, true)")) {
                ps.setObject(1, locationTenantA);
                ps.setObject(2, TENANT_A);
                ps.executeUpdate();
            }

            // Seed work location for Tenant B
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.work_location (id, tenant_id, code, name, country_code, is_filing_address, is_active) "
                            + "VALUES (?, ?, 'LOC_B', 'Location B', 'IN', true, true)")) {
                ps.setObject(1, locationTenantB);
                ps.setObject(2, TENANT_B);
                ps.executeUpdate();
            }
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a calendar cannot be assigned a work location belonging to another tenant")
    void cannotAssignWorkLocationBelongingToAnotherTenant() {
        TenantContext.set(TENANT_A);

        HolidayCalendarRequest request = new HolidayCalendarRequest("Acme HQ Calendar", false, Set.of(locationTenantB));

        assertThatThrownBy(() -> calendarService.createCalendar(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Work location does not belong to tenant");
    }

    @Test
    @DisplayName("assigning a location already on another calendar is refused by service and unique key")
    void assigningLocationAlreadyOnAnotherCalendarIsRefused() throws SQLException {
        TenantContext.set(TENANT_A);

        // First calendar successfully takes locationTenantA
        HolidayCalendarResponse cal1 = calendarService.createCalendar(
                new HolidayCalendarRequest("Calendar One", false, Set.of(locationTenantA)));
        assertThat(cal1.workLocationIds()).contains(locationTenantA);

        // Second calendar attempting to take same location is refused by service
        HolidayCalendarRequest conflictRequest =
                new HolidayCalendarRequest("Calendar Two", false, Set.of(locationTenantA));
        assertThatThrownBy(() -> calendarService.createCalendar(conflictRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already assigned to another calendar");

        // Direct database insert also fails on the unique index idx_holiday_calendar_location_tenant_location
        try (Connection conn = HolidayTestSchema.migrationConnection()) {
            UUID cal2Id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.holiday_calendar (id, tenant_id, name, is_default) VALUES (?, ?, 'Cal 2', false)")) {
                ps.setObject(1, cal2Id);
                ps.setObject(2, TENANT_A);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.holiday_calendar_location (id, tenant_id, calendar_id, work_location_id) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_A);
                ps.setObject(3, cal2Id);
                ps.setObject(4, locationTenantA);

                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("idx_holiday_calendar_location_tenant_location");
            }
        }
    }

    @Test
    @DisplayName("a second is_default calendar for one tenant is refused by service and partial unique index")
    void secondDefaultCalendarForOneTenantIsRefused() throws SQLException {
        TenantContext.set(TENANT_A);

        // First default calendar created
        HolidayCalendarResponse defaultCal =
                calendarService.createCalendar(new HolidayCalendarRequest("Default 1", true, Set.of()));
        assertThat(defaultCal.isDefault()).isTrue();

        // Second default calendar is refused by service
        HolidayCalendarRequest secondDefaultReq = new HolidayCalendarRequest("Default 2", true, Set.of());
        assertThatThrownBy(() -> calendarService.createCalendar(secondDefaultReq))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default holiday calendar already exists");

        // Direct DB insert also violates partial unique index idx_holiday_calendar_tenant_default
        try (Connection conn = HolidayTestSchema.migrationConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.holiday_calendar (id, tenant_id, name, is_default) VALUES (?, ?, 'Default 3', true)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_A);

                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("idx_holiday_calendar_tenant_default");
            }
        }
    }
}
