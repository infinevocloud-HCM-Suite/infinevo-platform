package com.infinevo.core.notification;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.util.List;
import java.util.Map;
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
 * W-20.1 spec section 7 — the template endpoints are {@code 403} without {@code
 * core.notification_template.manage}; {@code GET /notifications} returns only the caller's own.
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
class NotificationTemplateGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationTestApp.LinkedRecipients recipients;

    private UUID tenant;
    private UUID admin;
    private UUID employee;

    @BeforeEach
    void seed() throws Exception {
        tenant = NotificationTestSchema.insertTenant("Guard " + UUID.randomUUID());
        admin = UUID.randomUUID();
        employee = UUID.randomUUID();
        NotificationTestSchema.insertMember(tenant, admin, "tenant-admin");
        NotificationTestSchema.insertMember(tenant, employee, "employee");
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("employee: 403 on reading and on changing templates")
    void templatesNeedManage() throws Exception {
        mvc.perform(as(employee, get("/api/v1/notification-templates")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("core.notification_template.manage")));
        mvc.perform(as(employee, put("/api/v1/notification-templates/LEAVE_APPROVED"))
                        .content("{\"channel\":\"IN_APP\",\"body\":\"Approved\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("tenant-admin: sees the seeded defaults and saves a new version; a foreign placeholder is 400")
    void adminManagesTemplates() throws Exception {
        mvc.perform(as(admin, get("/api/v1/notification-templates").param("event", "LEAVE_APPROVED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(as(admin, put("/api/v1/notification-templates/LEAVE_APPROVED"))
                        .content("{\"channel\":\"IN_APP\",\"body\":\"Enjoy your ${leave_type}.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Enjoy your ${leave_type}."));
        mvc.perform(as(admin, put("/api/v1/notification-templates/LEAVE_APPROVED"))
                        .content("{\"channel\":\"IN_APP\",\"body\":\"Salary ${salary}\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.body").value(containsString("salary")));
    }

    @Test
    @DisplayName("GET /notifications is the caller's own: B's inbox holds none of A's, and B cannot mark A's read")
    void inboxIsTheCallersOwn() throws Exception {
        UUID employeeA = NotificationTestSchema.insertEmployee(tenant, "A-" + UUID.randomUUID());
        UUID employeeB = NotificationTestSchema.insertEmployee(tenant, "B-" + UUID.randomUUID());
        recipients.link(admin, employeeA);
        recipients.link(employee, employeeB);

        TenantContext.set(tenant);
        List<UUID> forA = notificationService.compose(
                NotificationEvent.TIMESHEET_REMINDER,
                employeeA,
                Map.of("employee_name", "A", "week_start", "2026-09-21"));
        TenantContext.clear();
        UUID inAppOfA = forA.get(0);

        mvc.perform(as(admin, get("/api/v1/notifications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(inAppOfA.toString()));
        mvc.perform(as(employee, get("/api/v1/notifications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(as(employee, post("/api/v1/notifications/" + inAppOfA + "/read")))
                .andExpect(status().isNotFound());
        mvc.perform(as(admin, post("/api/v1/notifications/" + inAppOfA + "/read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenant.toString())));
    }
}
