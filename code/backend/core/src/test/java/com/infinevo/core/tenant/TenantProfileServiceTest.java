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

/** The rules {@link TenantProfileServiceImpl} applies without a database (W-73.1). */
class TenantProfileServiceTest {

    private static final UUID TENANT = UUID.randomUUID();

    private JdbcTemplate jdbc;
    private DocumentLinkService links;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        links = mock(DocumentLinkService.class);
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
    @DisplayName("Branding is the row's three columns plus a day-long link to the logo")
    void brandingWithLogo() {
        UUID logo = UUID.randomUUID();
        stubRow("Acme Ltd", "People first", logo);
        when(links.signedLink(logo, TenantProfileService.LOGO_LINK_TTL))
                .thenReturn(new DocumentLinkService.SignedLink("/d?t=x", Instant.now()));

        TenantBranding branding =
                new TenantProfileServiceImpl(jdbc, () -> links).branding().orElseThrow();

        assertThat(branding.name()).isEqualTo("Acme Ltd");
        assertThat(branding.tagline()).isEqualTo("People first");
        assertThat(branding.logoDocumentId()).isEqualTo(logo);
        assertThat(branding.logoUrl()).isEqualTo("/d?t=x");
    }

    @Test
    @DisplayName("No logo, no link service, or a logo that is gone: the link is null and the header shows initials")
    void brandingFallsBackToNoLink() {
        stubRow("Acme Ltd", null, null);
        assertThat(new TenantProfileServiceImpl(jdbc, () -> links)
                        .branding()
                        .orElseThrow()
                        .logoUrl())
                .isNull();

        UUID logo = UUID.randomUUID();
        stubRow("Acme Ltd", null, logo);
        assertThat(new TenantProfileServiceImpl(jdbc, () -> null)
                        .branding()
                        .orElseThrow()
                        .logoUrl())
                .isNull();

        when(links.signedLink(eq(logo), any())).thenThrow(new DocumentService.NotFoundException(logo));
        TenantBranding gone =
                new TenantProfileServiceImpl(jdbc, () -> links).branding().orElseThrow();
        assertThat(gone.logoUrl()).isNull();
        assertThat(gone.logoDocumentId()).isEqualTo(logo);
    }

    @Test
    @DisplayName("No tenant bound: no branding, no query")
    void noTenantNoBranding() {
        TenantContext.clear();
        assertThat(new TenantProfileServiceImpl(jdbc, () -> links).branding()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private void stubRow(String name, String tagline, UUID logo) {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(TENANT))).thenAnswer(invocation -> {
            RowMapper<TenantBranding> mapper = invocation.getArgument(1);
            java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
            when(rs.getString("name")).thenReturn(name);
            when(rs.getString("tagline")).thenReturn(tagline);
            when(rs.getObject("logo_document_id", UUID.class)).thenReturn(logo);
            return List.of(mapper.mapRow(rs, 0));
        });
    }
}
