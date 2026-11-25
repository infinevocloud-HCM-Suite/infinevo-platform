package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.document.DocumentLinkService;
import com.infinevo.core.document.DocumentService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** The rules {@link TenantProfileServiceImpl} applies without a database (W-73.1, W-116). */
class TenantProfileServiceTest {

    private static final UUID TENANT = UUID.randomUUID();

    private JdbcTemplate jdbc;
    private DocumentLinkService links;
    private PayRunExistenceChecker payRunChecker;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        links = mock(DocumentLinkService.class);
        payRunChecker = mock(PayRunExistenceChecker.class);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A tagline is trimmed, blank is none, and over 80 characters is refused")
    void taglineRules() {
        assertThat(TenantProfileServiceImpl.cleanTagline(null)).isNull();
        assertThat(TenantProfileServiceImpl.cleanTagline("   ")).isNull();
        assertThat(TenantProfileServiceImpl.cleanTagline("  People first ")).isEqualTo("People first");
        assertThat(TenantProfileServiceImpl.cleanTagline("x".repeat(80))).hasSize(80);
        assertThatThrownBy(() -> TenantProfileServiceImpl.cleanTagline("x".repeat(81)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("80");
    }

    @Test
    @DisplayName("Branding is the row's columns plus a day-long link to the logo")
    void brandingWithLogo() {
        UUID logo = UUID.randomUUID();
        stubRow("Acme Ltd", "People first", logo, (short) 4, "IN", "Asia/Kolkata");
        when(links.signedLink(logo, TenantProfileService.LOGO_LINK_TTL))
                .thenReturn(new DocumentLinkService.SignedLink("/d?t=x", Instant.now()));

        TenantBranding branding = new TenantProfileServiceImpl(jdbc, () -> links, () -> payRunChecker)
                .branding()
                .orElseThrow();

        assertThat(branding.name()).isEqualTo("Acme Ltd");
        assertThat(branding.tagline()).isEqualTo("People first");
        assertThat(branding.logoDocumentId()).isEqualTo(logo);
        assertThat(branding.logoUrl()).isEqualTo("/d?t=x");
        assertThat(branding.countryCode()).isEqualTo("IN");
        assertThat(branding.timezone()).isEqualTo("Asia/Kolkata");
        assertThat(branding.leaveYearStartMonth()).isEqualTo((short) 4);
    }

    @Test
    @DisplayName("No logo, no link service, or a logo that is gone: the link is null and the header shows initials")
    void brandingFallsBackToNoLink() {
        stubRow("Acme Ltd", null, null, (short) 4, "IN", "Asia/Kolkata");
        assertThat(new TenantProfileServiceImpl(jdbc, () -> links, () -> payRunChecker)
                        .branding()
                        .orElseThrow()
                        .logoUrl())
                .isNull();

        UUID logo = UUID.randomUUID();
        stubRow("Acme Ltd", null, logo, (short) 4, "IN", "Asia/Kolkata");
        assertThat(new TenantProfileServiceImpl(jdbc, () -> null, () -> payRunChecker)
                        .branding()
                        .orElseThrow()
                        .logoUrl())
                .isNull();

        when(links.signedLink(eq(logo), any())).thenThrow(new DocumentService.NotFoundException(logo));
        TenantBranding gone = new TenantProfileServiceImpl(jdbc, () -> links, () -> payRunChecker)
                .branding()
                .orElseThrow();
        assertThat(gone.logoUrl()).isNull();
        assertThat(gone.logoDocumentId()).isEqualTo(logo);
    }

    @Test
    @DisplayName("No tenant bound: no branding, no query")
    void noTenantNoBranding() {
        TenantContext.clear();
        assertThat(new TenantProfileServiceImpl(jdbc, () -> links, () -> payRunChecker).branding())
                .isEmpty();
    }

    @Test
    @DisplayName("W-116: unknown timezone is refused with 400")
    void unknownTimezoneRefused() {
        stubRow("Acme Ltd", null, null, (short) 4, "IN", "Asia/Kolkata");
        TenantProfileServiceImpl service = new TenantProfileServiceImpl(jdbc, () -> links, () -> payRunChecker);

        assertThatThrownBy(() -> service.update(new TenantProfileRequest(null, null, null, "Mars/Olympus", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown timezone");
    }

    @Test
    @DisplayName("W-116: invalid leave year start month (<1 or >12) is refused with 400")
    void invalidLeaveYearStartMonthRefused() {
        stubRow("Acme Ltd", null, null, (short) 4, "IN", "Asia/Kolkata");
        TenantProfileServiceImpl service = new TenantProfileServiceImpl(jdbc, () -> links, () -> payRunChecker);

        assertThatThrownBy(() -> service.update(new TenantProfileRequest(null, null, null, null, (short) 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leaveYearStartMonth must be between 1 and 12");

        assertThatThrownBy(() -> service.update(new TenantProfileRequest(null, null, null, null, (short) 13)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leaveYearStartMonth must be between 1 and 12");
    }

    @Test
    @DisplayName("W-116: leave year change refused with 409 when leave data exists")
    void leaveYearChangeRefusedWhenLeaveExists() {
        stubRow("Acme Ltd", null, null, (short) 4, "IN", "Asia/Kolkata");
        when(jdbc.queryForObject(
                        eq("SELECT count(*) FROM core.leave_allocation WHERE tenant_id = ?"),
                        eq(Integer.class),
                        eq(TENANT)))
                .thenReturn(1);

        TenantProfileServiceImpl service = new TenantProfileServiceImpl(jdbc, () -> links, () -> payRunChecker);

        assertThatThrownBy(() -> service.update(new TenantProfileRequest(null, null, null, null, (short) 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("leave year cannot be changed once leave has been allocated or requested");
    }

    @Test
    @DisplayName("W-116: unknown country code is refused with 400")
    void unknownCountryCodeRefused() {
        stubRow("Acme Ltd", null, null, (short) 4, "IN", "Asia/Kolkata");
        when(jdbc.queryForObject(
                        eq("SELECT count(*) FROM reference.country WHERE code = ?"), eq(Integer.class), eq("ZZ")))
                .thenReturn(0);

        TenantProfileServiceImpl service = new TenantProfileServiceImpl(jdbc, () -> links, () -> payRunChecker);

        assertThatThrownBy(() -> service.update(new TenantProfileRequest(null, null, "ZZ", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown country code");
    }

    @Test
    @DisplayName("W-116: country change refused with 409 when pay run processed")
    void countryChangeRefusedWhenPayRunProcessed() {
        stubRow("Acme Ltd", null, null, (short) 4, "IN", "Asia/Kolkata");
        when(jdbc.queryForObject(
                        eq("SELECT count(*) FROM reference.country WHERE code = ?"), eq(Integer.class), eq("AE")))
                .thenReturn(1);
        when(payRunChecker.hasProcessedPayRun(TENANT)).thenReturn(true);

        TenantProfileServiceImpl service = new TenantProfileServiceImpl(jdbc, () -> links, () -> payRunChecker);

        assertThatThrownBy(() -> service.update(new TenantProfileRequest(null, null, "AE", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("country cannot be changed after the first pay run");
    }

    @Test
    @DisplayName("W-116: locked fields keep the same value without conflict")
    void lockedFieldsSameValueAccepted() {
        stubRow("Acme Ltd", "Existing tagline", null, (short) 4, "IN", "Asia/Kolkata");
        when(jdbc.queryForObject(
                        eq("SELECT count(*) FROM reference.country WHERE code = ?"), eq(Integer.class), eq("IN")))
                .thenReturn(1);
        when(payRunChecker.hasProcessedPayRun(TENANT)).thenReturn(true);
        when(jdbc.queryForObject(
                        eq("SELECT count(*) FROM core.leave_allocation WHERE tenant_id = ?"),
                        eq(Integer.class),
                        eq(TENANT)))
                .thenReturn(5);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), eq(TENANT)))
                .thenReturn(1);

        TenantProfileServiceImpl service = new TenantProfileServiceImpl(jdbc, () -> links, () -> payRunChecker);

        TenantProfileResponse response =
                service.update(new TenantProfileRequest("New tagline", null, "IN", "Asia/Kolkata", (short) 4));
        assertThat(response).isNotNull();
        assertThat(response.countryLocked()).isTrue();
        assertThat(response.leaveYearLocked()).isTrue();
    }

    @Test
    @DisplayName("W-116: null leaveYearStartMonth in database yields null in TenantBranding")
    void nullLeaveYearStartMonthInDb() {
        stubRow("Acme Ltd", "Tagline", null, null, "IN", "Asia/Kolkata");
        TenantBranding branding = new TenantProfileServiceImpl(jdbc, () -> links, () -> payRunChecker)
                .branding()
                .orElseThrow();
        assertThat(branding.leaveYearStartMonth()).isNull();
    }

    @SuppressWarnings("unchecked")
    private void stubRow(String name, String tagline, UUID logo, Short leaveYear, String countryCode, String timezone) {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(TENANT))).thenAnswer(invocation -> {
            RowMapper<TenantBranding> mapper = invocation.getArgument(1);
            java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
            when(rs.getString("name")).thenReturn(name);
            when(rs.getString("tagline")).thenReturn(tagline);
            when(rs.getObject("logo_document_id", UUID.class)).thenReturn(logo);
            if (leaveYear != null) {
                when(rs.getShort("leave_year_start_month")).thenReturn(leaveYear);
                when(rs.wasNull()).thenReturn(false);
            } else {
                when(rs.getShort("leave_year_start_month")).thenReturn((short) 0);
                when(rs.wasNull()).thenReturn(true);
            }
            when(rs.getString("country_code")).thenReturn(countryCode);
            when(rs.getString("timezone")).thenReturn(timezone);
            return List.of(mapper.mapRow(rs, 0));
        });
    }
}
