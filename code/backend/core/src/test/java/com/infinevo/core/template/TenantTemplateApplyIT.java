package com.infinevo.core.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.holiday.HolidayTemplateContributor;
import com.infinevo.core.leave.LeaveTypeTemplateContributor;
import com.infinevo.core.tenant.TenantRequest;
import com.infinevo.core.tenant.TenantResponse;
import com.infinevo.core.tenant.TenantService;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.identity.UserProfileSyncService;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-73.9 §7: a tenant created with country {@code IN} has the country's holidays and leave types before its
 * admin signs in; a second run changes nothing; a country with no template gets nothing; a tenant made before
 * templates gets only the sections it has no rows for.
 *
 * <p>Core's two sections only: the payroll sections (components, EPF, ESI, pay schedule) are proven against
 * the payroll schema by {@code PayrollTemplateContributorIT}. Provisioning runs as {@code app_user} bound to the
 * platform tenant, as a platform admin's request is, so row-level security is what the template has to satisfy;
 * rows are then read as the schema owner, past RLS.
 */
@SpringBootTest(classes = TenantTemplateApplyIT.App.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class TenantTemplateApplyIT extends AbstractIntegrationTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantTemplateService templateService;

    @Autowired
    private PlatformTenant platformTenant;

    @BeforeEach
    void bindPlatformTenant() {
        TenantContext.set(platformTenant.tenantId());
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a new IN tenant starts with 6 leave types and this year's national holidays in a default calendar")
    void newIndianTenant_hasHolidaysAndLeaveTypes() throws SQLException {
        UUID tenantId = provision("IN");

        assertThat(strings(
                        "SELECT code FROM core.leave_type WHERE tenant_id = ? AND created_by = 'template'"
                                + " AND updated_by = 'template' AND is_active",
                        tenantId))
                .containsExactlyInAnyOrder("EL", "CL", "SL", "ML", "PL", "LOP");
        assertThat(strings("SELECT code FROM core.leave_type WHERE tenant_id = ? AND NOT is_paid", tenantId))
                .containsExactly("LOP");

        assertThat(strings("SELECT name FROM core.holiday_calendar WHERE tenant_id = ? AND is_default", tenantId))
                .containsExactly("India national holidays");
        int year = LocalDate.now(ZoneId.of("Asia/Kolkata")).getYear();
        int expected = Integer.parseInt(strings(
                        "SELECT jsonb_array_length(payload->'years'->?::text)::text FROM reference.country_template"
                                + " WHERE country_code = 'IN' AND section = 'holidays'",
                        Integer.toString(year))
                .get(0));
        List<String> thisYear = strings(
                "SELECT name FROM core.holiday WHERE tenant_id = ? AND extract(year FROM from_date) = " + year,
                tenantId);
        assertThat(thisYear).hasSize(expected).contains("Republic Day", "Independence Day", "Gandhi Jayanti");
        if (year == 2026) {
            assertThat(thisYear).hasSizeGreaterThanOrEqualTo(12);
        }

        assertThat(strings(
                        "SELECT section || ':' || outcome || ':' || version FROM core.tenant_template_applied"
                                + " WHERE tenant_id = ?",
                        tenantId))
                .containsExactlyInAnyOrder(
                        "holidays:APPLIED:1",
                        "leave_types:APPLIED:1",
                        // No payroll contributor in this context, and the tenant holds no PAYROLL module.
                        "pay_schedule:SKIPPED:1",
                        "salary_components:SKIPPED:1",
                        "statutory:SKIPPED:1");
        assertThat(TenantContext.current())
                .as("the platform binding is restored")
                .contains(platformTenant.tenantId());
    }

    @Test
    @DisplayName("applying the template again writes nothing and reports every section skipped")
    void secondApply_changesNothing() throws SQLException {
        UUID tenantId = provision("IN");
        String before = fingerprint(tenantId);

        TemplateApplyResponse again = tenantService.applyCountryTemplate(tenantId);

        assertThat(again.countryCode()).isEqualTo("IN");
        assertThat(again.applied()).isEmpty();
        assertThat(again.skipped())
                .containsExactly("holidays", "leave_types", "pay_schedule", "salary_components", "statutory");
        assertThat(fingerprint(tenantId)).isEqualTo(before);
        assertThat(TenantContext.current()).contains(platformTenant.tenantId());
    }

    @Test
    @DisplayName("a country with no template: nothing written, nothing recorded")
    void countryWithoutTemplate_getsNothing() throws SQLException {
        UUID tenantId = provision("AE");

        assertThat(fingerprint(tenantId)).isEqualTo("0/0/0");
        assertThat(strings("SELECT id::text FROM core.tenant_template_applied WHERE tenant_id = ?", tenantId))
                .isEmpty();
        assertThat(templateService.countries())
                .extracting(CountryTemplateSummary::countryCode)
                .contains("IN")
                .doesNotContain("AE");
    }

    @Test
    @DisplayName("a tenant made before templates, with leave types of its own, gets the holidays and keeps its"
            + " leave types")
    void olderTenant_getsOnlyWhatItLacks() throws SQLException {
        UUID tenantId = provision("AE");
        owner("UPDATE core.tenant SET country_code = 'IN' WHERE tenant_id = ?", tenantId);
        owner(
                "INSERT INTO core.leave_type (tenant_id, code, name, is_paid, unit, allow_half_day, valid_from)"
                        + " VALUES (?, 'ANNUAL', 'Annual leave', true, 'DAYS', true, DATE '2026-01-01')",
                tenantId);

        TemplateApplyResponse result = tenantService.applyCountryTemplate(tenantId);

        assertThat(result.applied()).containsExactly("holidays");
        assertThat(result.skipped()).contains("leave_types");
        assertThat(strings("SELECT code FROM core.leave_type WHERE tenant_id = ?", tenantId))
                .containsExactly("ANNUAL");
        assertThat(strings("SELECT id::text FROM core.holiday WHERE tenant_id = ?", tenantId))
                .isNotEmpty();
    }

    @Test
    @DisplayName("a customer tenant cannot apply a template to another tenant")
    void customerTenant_refused() throws SQLException {
        UUID customer = provision("AE");
        UUID other = provision("AE");
        owner("UPDATE core.tenant SET country_code = 'IN' WHERE tenant_id = ?", other);
        String before = fingerprint(other);

        TenantContext.set(customer);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tenantService.applyCountryTemplate(other))
                .isInstanceOf(PermissionDeniedException.class);

        assertThat(fingerprint(other)).isEqualTo(before);
    }

    @Test
    @DisplayName("the platform tenant takes no template")
    void platformTenant_refused() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> tenantService.applyCountryTemplate(platformTenant.tenantId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UUID provision(String country) {
        TenantResponse response = tenantService.provisionTenant(new TenantRequest(
                "Template Corp " + UUID.randomUUID(), country, "Asia/Kolkata", (short) 4, Set.of(), null));
        return response.tenantId();
    }

    private static String fingerprint(UUID tenantId) throws SQLException {
        return strings(
                        "SELECT (SELECT count(*) FROM core.leave_type WHERE tenant_id = ?) || '/'"
                                + " || (SELECT count(*) FROM core.holiday_calendar WHERE tenant_id = ?) || '/'"
                                + " || (SELECT count(*) FROM core.holiday WHERE tenant_id = ?)",
                        tenantId,
                        tenantId,
                        tenantId)
                .get(0);
    }

    static List<String> strings(String sql, Object... params) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            List<String> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            return out;
        }
    }

    static void owner(String sql, Object... params) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }

    /**
     * The tenant-provisioning context of {@code TenantAdminInvitationIT}, plus the template service and core's two
     * contributors. The holiday and leave packages contribute repositories and entities only.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(
            basePackages = {
                "com.infinevo.core.invitation",
                "com.infinevo.core.notification",
                "com.infinevo.core.authz",
                "com.infinevo.core.employee",
                "com.infinevo.core.org",
                "com.infinevo.core.tenant",
                "com.infinevo.core.template",
                "com.infinevo.shared.authz"
            },
            excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
            })
    @EntityScan(
            basePackages = {
                "com.infinevo.core.invitation",
                "com.infinevo.core.notification",
                "com.infinevo.core.authz",
                "com.infinevo.core.employee",
                "com.infinevo.core.org",
                "com.infinevo.core.tenant",
                "com.infinevo.core.holiday",
                "com.infinevo.core.leave",
                "com.infinevo.shared.identity"
            })
    @EnableJpaRepositories(
            basePackages = {
                "com.infinevo.core.invitation",
                "com.infinevo.core.notification",
                "com.infinevo.core.authz",
                "com.infinevo.core.employee",
                "com.infinevo.core.org",
                "com.infinevo.core.tenant",
                "com.infinevo.core.holiday",
                "com.infinevo.core.leave",
                "com.infinevo.shared.identity"
            })
    @Import({UserProfileSyncService.class, HolidayTemplateContributor.class, LeaveTypeTemplateContributor.class})
    static class App {}
}
