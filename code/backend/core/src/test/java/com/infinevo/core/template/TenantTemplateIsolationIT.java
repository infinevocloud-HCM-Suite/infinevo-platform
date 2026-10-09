package com.infinevo.core.template;

import static com.infinevo.core.template.TenantTemplateApplyIT.strings;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.tenant.TenantRequest;
import com.infinevo.core.tenant.TenantService;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-73.9 §7: every row a template writes carries the new tenant's id only - nothing lands under the platform
 * tenant that provisioned it, nor under another tenant - and {@code app_user} bound to one tenant sees only that
 * tenant's template rows and template record.
 */
@SpringBootTest(classes = TenantTemplateApplyIT.App.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class TenantTemplateIsolationIT extends AbstractIntegrationTest {

    private static final String[] TABLES = {
        "core.leave_type", "core.holiday_calendar", "core.holiday", "core.tenant_template_applied"
    };

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
    @DisplayName("two IN tenants: each template row carries its own tenant id, none the platform tenant's")
    void rowsCarryTheNewTenantOnly() throws SQLException {
        UUID a = provision();
        UUID b = provision();

        for (String table : TABLES) {
            assertThat(strings("SELECT count(*)::text FROM " + table + " WHERE tenant_id = ?", a))
                    .as(table + " for A")
                    .doesNotContain("0");
            assertThat(strings("SELECT count(*)::text FROM " + table + " WHERE tenant_id = ?", b))
                    .as(table + " for B")
                    .doesNotContain("0");
            assertThat(strings(
                            "SELECT count(*)::text FROM " + table + " WHERE tenant_id = ?", platformTenant.tenantId()))
                    .as(table + " for the platform tenant")
                    .containsExactly("0");
        }
        // Every holiday sits in its own tenant's calendar.
        assertThat(strings(
                        "SELECT count(*)::text FROM core.holiday h JOIN core.holiday_calendar c ON c.id = h.calendar_id"
                                + " WHERE h.tenant_id <> c.tenant_id"))
                .containsExactly("0");
    }

    @Test
    @DisplayName("app_user bound to tenant A reads A's template rows and none of B's")
    void appUserSeesItsOwnTenantOnly() throws SQLException {
        UUID a = provision();
        UUID b = provision();

        for (String table : TABLES) {
            List<String> seen = asTenant(a, "SELECT DISTINCT tenant_id::text FROM " + table);
            assertThat(seen).as(table).containsExactly(a.toString()).doesNotContain(b.toString());
        }
    }

    private UUID provision() {
        return tenantService
                .provisionTenant(new TenantRequest(
                        "Isolation Corp " + UUID.randomUUID(), "IN", "Asia/Kolkata", (short) 4, Set.of(), null))
                .tenantId();
    }

    private static List<String> asTenant(UUID tenantId, String sql) throws SQLException {
        try (Connection conn = AuthzTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement bind =
                    conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
                bind.setString(1, tenantId.toString());
                bind.execute();
            }
            List<String> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            conn.rollback();
            return out;
        }
    }
}
