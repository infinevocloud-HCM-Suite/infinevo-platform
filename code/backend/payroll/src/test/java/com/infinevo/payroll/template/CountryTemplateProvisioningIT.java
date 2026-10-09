package com.infinevo.payroll.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.holiday.HolidayTemplateContributor;
import com.infinevo.core.leave.LeaveTypeTemplateContributor;
import com.infinevo.core.subscription.EntitlementReadService;
import com.infinevo.core.tenant.TenantRequest;
import com.infinevo.core.tenant.TenantResponse;
import com.infinevo.core.tenant.TenantService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.identity.UserProfileSyncService;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-73.9, end to end: the real Create Tenant flow - {@code TenantService.provisionTenant} as a platform admin -
 * for an {@code IN} tenant holding {@code PAYROLL} with an administrator email, against every shipped migration.
 * The country template's core and payroll sections are both written into the new tenant, and the D-42
 * administrator invitation is still sent in the same flow.
 *
 * <p>Its own database, migrated by running every script under {@code db/migration} in version order as the
 * schema owner, as Flyway does (placeholder replacement is off in the shipped configuration). The application
 * connects as {@code app_user}, so row-level security applies; rows are read back as the owner.
 */
@SpringBootTest(
        classes = CountryTemplateProvisioningIT.App.class,
        properties = "invitation.link.base-url=https://app.infinevo.test/invitations/accept")
@ContextConfiguration(
        initializers = {PostgresTestContainerInitializer.class, CountryTemplateProvisioningIT.FullSchema.class})
class CountryTemplateProvisioningIT extends AbstractIntegrationTest {

    static final String DATABASE = "infinevo_country_template";

    @Autowired
    private TenantService tenantService;

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
    @DisplayName("Create Tenant IN with PAYROLL and an admin email: components, EPF, pay schedule, holidays and the"
            + " admin invitation all exist")
    void createIndianPayrollTenant_withAdmin() throws SQLException {
        String email = "admin-" + UUID.randomUUID() + "@example.test";
        TenantResponse response = tenantService.provisionTenant(
                new TenantRequest(
                        "Country Corp " + UUID.randomUUID(),
                        "IN",
                        "Asia/Kolkata",
                        (short) 4,
                        Set.of(PlatformModule.PAYROLL),
                        email),
                UUID.randomUUID());
        UUID tenantId = response.tenantId();

        assertThat(count(
                        "SELECT count(*) FROM payroll.earning WHERE tenant_id = ? AND created_by = 'template'",
                        tenantId))
                .isGreaterThanOrEqualTo(17);
        assertThat(count("SELECT count(*) FROM payroll.earning WHERE tenant_id = ?", tenantId)
                        + count("SELECT count(*) FROM payroll.deduction WHERE tenant_id = ?", tenantId))
                .isGreaterThanOrEqualTo(20);
        assertThat(strings(
                        "SELECT is_enabled::text || '/' || employee_rate::text FROM payroll.epf_setting"
                                + " WHERE tenant_id = ?",
                        tenantId))
                .containsExactly("false/12.0000");
        assertThat(count("SELECT count(*) FROM payroll.esi_setting WHERE tenant_id = ?", tenantId))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payroll.pay_schedule WHERE tenant_id = ?", tenantId))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM core.leave_type WHERE tenant_id = ?", tenantId))
                .isEqualTo(6);
        assertThat(count("SELECT count(*) FROM core.holiday WHERE tenant_id = ?", tenantId))
                .isPositive();
        assertThat(strings(
                        "SELECT section || ':' || outcome FROM core.tenant_template_applied WHERE tenant_id = ?",
                        tenantId))
                .containsExactlyInAnyOrder(
                        "holidays:APPLIED",
                        "leave_types:APPLIED",
                        "pay_schedule:APPLIED",
                        "salary_components:APPLIED",
                        "statutory:APPLIED");

        assertThat(response.adminInvitationId()).isNotNull();
        assertThat(strings(
                        "SELECT tenant_id::text || '|' || email || '|' || status FROM core.user_invitation WHERE id = ?",
                        response.adminInvitationId()))
                .containsExactly(tenantId + "|" + email + "|PENDING");
        assertThat(TenantContext.current()).contains(platformTenant.tenantId());
    }

    private static long count(String sql, UUID tenantId) throws SQLException {
        return Long.parseLong(strings(sql, tenantId).get(0));
    }

    private static List<String> strings(String sql, UUID param) throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                        FullSchema.url(),
                        PostgresTestContainerInitializer.MIGRATION_USER,
                        PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            List<String> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            return out;
        }
    }

    /** Creates {@value #DATABASE}, runs every shipped script into it once, and points the datasource at it. */
    static class FullSchema implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");
        private static String url;

        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of("spring.datasource.url=" + url()).applyTo(ctx.getEnvironment());
        }

        static synchronized String url() {
            if (url == null) {
                String created = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);
                try (Connection conn = DriverManager.getConnection(
                                created,
                                PostgresTestContainerInitializer.MIGRATION_USER,
                                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
                        Statement st = conn.createStatement()) {
                    for (Path script : scripts()) {
                        try {
                            st.execute(Files.readString(script, StandardCharsets.UTF_8));
                        } catch (SQLException e) {
                            throw new IllegalStateException("Migration script failed: " + script.getFileName(), e);
                        }
                    }
                } catch (SQLException | IOException | URISyntaxException e) {
                    throw new IllegalStateException("Could not prepare " + DATABASE, e);
                }
                url = created;
            }
            return url;
        }

        private static List<Path> scripts() throws IOException, URISyntaxException {
            Path root = Paths.get(CountryTemplateProvisioningIT.class
                    .getClassLoader()
                    .getResource("db/migration")
                    .toURI());
            try (Stream<Path> files = Files.walk(root)) {
                return files.filter(
                                p -> VERSION.matcher(p.getFileName().toString()).matches())
                        .sorted(Comparator.comparingInt(FullSchema::version))
                        .toList();
            }
        }

        private static int version(Path script) {
            Matcher m = VERSION.matcher(script.getFileName().toString());
            if (!m.matches()) {
                throw new IllegalArgumentException(script.toString());
            }
            return Integer.parseInt(m.group(1));
        }
    }

    /**
     * Core's tenant-provisioning context (as {@code TenantAdminInvitationIT}), the template service with every
     * contributor, and the entitlement reader so the tenant's {@code PAYROLL} module is seen.
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
                "com.infinevo.shared.authz",
                "com.infinevo.payroll.template"
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
                "com.infinevo.core.subscription",
                "com.infinevo.shared.identity",
                "com.infinevo.payroll.component",
                "com.infinevo.payroll.statutory.settings",
                "com.infinevo.payroll.schedule"
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
                "com.infinevo.core.subscription",
                "com.infinevo.shared.identity",
                "com.infinevo.payroll.component",
                "com.infinevo.payroll.statutory.settings",
                "com.infinevo.payroll.schedule"
            })
    @Import({
        UserProfileSyncService.class,
        HolidayTemplateContributor.class,
        LeaveTypeTemplateContributor.class,
        EntitlementReadService.class
    })
    static class App {}
}
