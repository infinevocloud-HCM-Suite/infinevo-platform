package com.infinevo.core.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.test.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-43.1 §7, {@code TimesheetEscalationTemplateIT}: {@code V146} seeds both {@code TIMESHEET_ESCALATION} templates for
 * every tenant, new ones included, and the email escapes what an employee typed.
 */
class TimesheetEscalationTemplateIT extends AbstractIntegrationTest {

    // No application context: the migration and the renderer are the whole subject, and the notification
    // test application needs the queue emulator, which this does not. The base class is only what skips the
    // test, as every IT is skipped, on a machine with no Docker.
    private final TemplateRenderer renderer = new TemplateRenderer();

    private static long templates(UUID tenantId) throws Exception {
        return NotificationTestSchema.count(
                "SELECT count(*) FROM core.notification_template WHERE tenant_id = ? AND event = 'TIMESHEET_ESCALATION'",
                tenantId);
    }

    @Test
    @DisplayName("A tenant created after V146 gets both TIMESHEET_ESCALATION templates, in-app and email")
    void aNewTenantGetsBothTemplates() throws Exception {
        UUID tenantId = NotificationTestSchema.insertTenant("Escalation " + UUID.randomUUID());

        assertThat(templates(tenantId)).isEqualTo(2);
        assertThat(NotificationTestSchema.count(
                        "SELECT count(*) FROM core.notification_template WHERE tenant_id = ?"
                                + " AND event = 'TIMESHEET_ESCALATION' AND channel IN ('IN_APP', 'EMAIL')",
                        tenantId))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Re-running the seed for an existing tenant adds nothing and leaves an edited template alone")
    void theSeedIsSafeToRunAgain() throws Exception {
        UUID tenantId = NotificationTestSchema.insertTenant("Escalation again " + UUID.randomUUID());
        try (var conn = NotificationTestSchema.migrationConnection();
                var st = conn.createStatement()) {
            st.execute("UPDATE core.notification_template SET subject = 'Edited by the tenant' WHERE tenant_id = '"
                    + tenantId + "' AND event = 'TIMESHEET_ESCALATION' AND channel = 'EMAIL'");
            st.execute("SELECT core.seed_notification_templates('" + tenantId + "'::uuid)");
        }

        assertThat(templates(tenantId)).isEqualTo(2);
        assertThat(NotificationTestSchema.count(
                        "SELECT count(*) FROM core.notification_template WHERE tenant_id = ?"
                                + " AND event = 'TIMESHEET_ESCALATION' AND subject = 'Edited by the tenant'",
                        tenantId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("The templates use only the event's placeholders, and the email escapes an employee name with <b>")
    void theEmailEscapesWhatAnEmployeeTyped() throws Exception {
        UUID tenantId = NotificationTestSchema.insertTenant("Escalation render " + UUID.randomUUID());
        String body;
        try (var conn = NotificationTestSchema.migrationConnection();
                var ps = conn.prepareStatement("SELECT body FROM core.notification_template WHERE tenant_id = ?"
                        + " AND event = 'TIMESHEET_ESCALATION' AND channel = 'EMAIL'")) {
            ps.setObject(1, tenantId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                body = rs.getString(1);
            }
        }

        String rendered = renderer.render(
                body,
                Map.of(
                        "employee_name", "Manager",
                        "week_start", "2026-09-28",
                        "late_employees", "<b>Asha</b> Rao, Ravi Nair"),
                true);

        assertThat(rendered).contains("2026-09-28").contains("&lt;b&gt;Asha&lt;/b&gt; Rao, Ravi Nair");
        assertThat(rendered).doesNotContain("<b>Asha");
    }
}
