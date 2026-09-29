package com.infinevo.core.notification;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-20.2 spec section 7 — every /reminder-rules verb returns 403 without core.reminder_rule.manage.
 *
 * <p>Through the real chain: tenant filter, the permission aspect, the roles in the database.
 */
@SpringBootTest(classes = NotificationTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            NotificationTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class ReminderRuleGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private UUID tenant;
    private UUID admin;
    private UUID employee;
    private UUID ruleId;

    @BeforeEach
    void seed() throws Exception {
        tenant = NotificationTestSchema.insertTenant("Guard " + UUID.randomUUID());
        admin = UUID.randomUUID();
        employee = UUID.randomUUID();
        NotificationTestSchema.insertMember(tenant, admin, "tenant-admin");
        NotificationTestSchema.insertMember(tenant, employee, "employee");

        ruleId = NotificationTestSchema.insertReminderRule(
                tenant, NotificationEvent.TIMESHEET_REMINDER, "SUBJECT", Anchor.WEEKLY, 0, 5, LocalTime.of(9, 0));
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, UUID sub) {
        return builder.header("X-Tenant-ID", tenant.toString())
                .with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@notification.test")));
    }

    @Test
    @DisplayName("GET /api/v1/reminder-rules returns 403 for employee and 200 for tenant-admin")
    void getListGuarded() throws Exception {
        mvc.perform(authed(get("/api/v1/reminder-rules"), employee))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("core.reminder_rule.manage")));

        mvc.perform(authed(get("/api/v1/reminder-rules"), admin)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/reminder-rules returns 403 for employee and 201 for tenant-admin")
    void postGuarded() throws Exception {
        String body =
                """
            {
              "event": "TIMESHEET_REMINDER",
              "audience": "SUBJECT",
              "anchor": "WEEKLY",
              "offset_days": 0,
              "day_of_week": 5,
              "send_at_local_time": "09:00:00"
            }
            """;

        mvc.perform(authed(
                        post("/api/v1/reminder-rules")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        employee))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("core.reminder_rule.manage")));

        mvc.perform(authed(
                        post("/api/v1/reminder-rules")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        admin))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /api/v1/reminder-rules/{id} returns 403 for employee and 200 for tenant-admin")
    void putGuarded() throws Exception {
        String body =
                """
            {
              "event": "TIMESHEET_REMINDER",
              "audience": "SUBJECT",
              "anchor": "WEEKLY",
              "offset_days": 1,
              "day_of_week": 5,
              "send_at_local_time": "10:00:00"
            }
            """;

        mvc.perform(authed(
                        put("/api/v1/reminder-rules/" + ruleId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        employee))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("core.reminder_rule.manage")));

        mvc.perform(authed(
                        put("/api/v1/reminder-rules/" + ruleId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        admin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/reminder-rules/{id} returns 403 for employee and 204 for tenant-admin")
    void deleteGuarded() throws Exception {
        mvc.perform(authed(delete("/api/v1/reminder-rules/" + ruleId), employee))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("core.reminder_rule.manage")));

        mvc.perform(authed(delete("/api/v1/reminder-rules/" + ruleId), admin)).andExpect(status().isNoContent());
    }
}
