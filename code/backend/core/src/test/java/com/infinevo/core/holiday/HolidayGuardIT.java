package com.infinevo.core.holiday;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
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
 * W-17 — Authorization guard tests for holiday calendar endpoints.
 *
 * <p>GET without core.holiday.read is 403; GET with core.holiday.read is 200;
 * POST without core.holiday.manage is 403; POST with core.holiday.manage is 201.
 */
@SpringBootTest(
        classes = PermissionGuardTestApp.class,
        properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class HolidayGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private UUID tenant;
    private UUID noPermSub;
    private UUID readerSub;
    private UUID managerSub;

    @BeforeEach
    void setUp() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("HolidayGuard " + UUID.randomUUID());

        // 1. User with no holiday actions
        noPermSub = UUID.randomUUID();
        UUID noPermAccount = AuthzTestSchema.insertMember(tenant, noPermSub, "noperm@guard.test");
        UUID noHolidayRole = AuthzTestSchema.insertRole(tenant, "no-holiday", "No Holiday", "core.tenant.read");
        AuthzTestSchema.grant(tenant, noPermAccount, noHolidayRole);

        // 2. User holding core.holiday.read only
        readerSub = UUID.randomUUID();
        UUID readerAccount = AuthzTestSchema.insertMember(tenant, readerSub, "reader@guard.test");
        UUID readerRole = AuthzTestSchema.insertRole(tenant, "holiday-reader", "Reader", "core.holiday.read");
        AuthzTestSchema.grant(tenant, readerAccount, readerRole);

        // 3. User holding core.holiday.manage and core.holiday.read
        managerSub = UUID.randomUUID();
        UUID managerAccount = AuthzTestSchema.insertMember(tenant, managerSub, "manager@guard.test");
        UUID managerRole = AuthzTestSchema.insertRole(
                tenant, "holiday-manager", "Manager", "core.holiday.read", "core.holiday.manage");
        AuthzTestSchema.grant(tenant, managerAccount, managerRole);
    }

    @Test
    @DisplayName("GET /api/v1/holiday-calendars without core.holiday.read is 403")
    void getCalendars_withoutRead_forbidden() throws Exception {
        mvc.perform(as(noPermSub, get("/api/v1/holiday-calendars")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/holiday-calendars with core.holiday.read is 200")
    void getCalendars_withRead_ok() throws Exception {
        mvc.perform(as(readerSub, get("/api/v1/holiday-calendars"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/holiday-calendars without core.holiday.manage is 403")
    void createCalendar_withoutManage_forbidden() throws Exception {
        HolidayCalendarRequest req = new HolidayCalendarRequest("Test Calendar", false, Set.of());
        mvc.perform(as(readerSub, post("/api/v1/holiday-calendars")).content(json.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /api/v1/holiday-calendars with core.holiday.manage is 201")
    void createCalendar_withManage_created() throws Exception {
        HolidayCalendarRequest req = new HolidayCalendarRequest("Test Calendar", false, Set.of());
        mvc.perform(as(managerSub, post("/api/v1/holiday-calendars")).content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Calendar"));
    }

    @Test
    @DisplayName("GET /api/v1/holidays without core.holiday.read is 403")
    void queryHolidays_withoutRead_forbidden() throws Exception {
        mvc.perform(as(
                        noPermSub,
                        get("/api/v1/holidays")
                                .param("workLocationId", UUID.randomUUID().toString())
                                .param("from", "2026-01-01")
                                .param("to", "2026-12-31")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/holidays with core.holiday.read is 200")
    void queryHolidays_withRead_ok() throws Exception {
        mvc.perform(as(
                        readerSub,
                        get("/api/v1/holidays")
                                .param("workLocationId", UUID.randomUUID().toString())
                                .param("from", "2026-01-01")
                                .param("to", "2026-12-31")))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenant.toString())));
    }
}
