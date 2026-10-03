package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Unit tests for {@link TenantClock} (W-40.2, spec section 7).
 */
class TenantClockTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("An instant at 20:00 UTC is the next date in Asia/Kolkata (+05:30)")
    void instantAt2000UtcIsNextDateInKolkata() {
        when(jdbcTemplate.query(
                        eq("SELECT timezone FROM core.tenant WHERE tenant_id = ?"),
                        any(RowMapper.class),
                        eq(TENANT_ID)))
                .thenReturn(List.of("Asia/Kolkata"));

        TenantClock clock = new TenantClock(jdbcTemplate);
        Instant instant = Instant.parse("2026-10-02T20:00:00Z");

        LocalDate date = clock.dateOf(instant);
        assertThat(date).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    @DisplayName("A null or blank timezone in core.tenant falls back to Asia/Kolkata")
    void nullOrBlankTimezoneFallsBackToKolkata() {
        when(jdbcTemplate.query(
                        eq("SELECT timezone FROM core.tenant WHERE tenant_id = ?"),
                        any(RowMapper.class),
                        eq(TENANT_ID)))
                .thenReturn(Collections.singletonList(null));

        TenantClock clock = new TenantClock(jdbcTemplate);
        assertThat(clock.zone()).isEqualTo(ZoneId.of("Asia/Kolkata"));

        when(jdbcTemplate.query(
                        eq("SELECT timezone FROM core.tenant WHERE tenant_id = ?"),
                        any(RowMapper.class),
                        eq(TENANT_ID)))
                .thenReturn(List.of("   "));
        assertThat(clock.zone()).isEqualTo(ZoneId.of("Asia/Kolkata"));

        when(jdbcTemplate.query(
                        eq("SELECT timezone FROM core.tenant WHERE tenant_id = ?"),
                        any(RowMapper.class),
                        eq(TENANT_ID)))
                .thenReturn(List.of());
        assertThat(clock.zone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    @DisplayName("An invalid timezone string falls back to Asia/Kolkata")
    void invalidTimezoneFallsBackToKolkata() {
        when(jdbcTemplate.query(
                        eq("SELECT timezone FROM core.tenant WHERE tenant_id = ?"),
                        any(RowMapper.class),
                        eq(TENANT_ID)))
                .thenReturn(List.of("Invalid/Unknown_Zone"));

        TenantClock clock = new TenantClock(jdbcTemplate);
        assertThat(clock.zone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    @DisplayName("A valid configured timezone is respected")
    void validTimezoneRespected() {
        when(jdbcTemplate.query(
                        eq("SELECT timezone FROM core.tenant WHERE tenant_id = ?"),
                        any(RowMapper.class),
                        eq(TENANT_ID)))
                .thenReturn(List.of("Europe/London"));

        TenantClock clock = new TenantClock(jdbcTemplate);
        assertThat(clock.zone()).isEqualTo(ZoneId.of("Europe/London"));

        Instant summerInstant = Instant.parse("2026-07-01T23:30:00Z"); // BST (UTC+1)
        assertThat(clock.dateOf(summerInstant)).isEqualTo(LocalDate.of(2026, 7, 2));
    }

    @Test
    @DisplayName("today() returns current date in the bound tenant timezone")
    void todayReturnsDateInTenantZone() {
        when(jdbcTemplate.query(
                        eq("SELECT timezone FROM core.tenant WHERE tenant_id = ?"),
                        any(RowMapper.class),
                        eq(TENANT_ID)))
                .thenReturn(List.of("Asia/Kolkata"));

        Instant fixedInstant = Instant.parse("2026-10-02T21:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        TenantClock clock = new TenantClock(jdbcTemplate, fixedClock);
        assertThat(clock.today()).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    @DisplayName("dateOf(null) throws NullPointerException")
    void dateOfNullThrows() {
        TenantClock clock = new TenantClock(jdbcTemplate);
        assertThatThrownBy(() -> clock.dateOf(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("instant must not be null");
    }
}
