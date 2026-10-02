package com.infinevo.hrms.project;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
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
 * Integration test verifying authorization and action guards across project endpoints (W-41).
 */
@SpringBootTest(classes = HrmsTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsProjectTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class ProjectPermissionIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProjectService projectService;

    private UUID tenant;
    private UUID hrUser;
    private UUID managerUser;
    private UUID employeeUser;

    private UUID managerEmpId;
    private UUID employeeEmpId;

    @BeforeEach
    void seed() throws SQLException {
        tenant = HrmsProjectTestSchema.insertTenant("Permission Tenant " + UUID.randomUUID());
        HrmsTestApp.ENTITLED.put(tenant, Set.of(PlatformModule.HRMS));
        managerEmpId = HrmsProjectTestSchema.insertEmployee(tenant, "MGR-" + UUID.randomUUID());
        employeeEmpId = HrmsProjectTestSchema.insertEmployee(tenant, "EMP-" + UUID.randomUUID());

        hrUser = UUID.randomUUID();
        managerUser = UUID.randomUUID();
        employeeUser = UUID.randomUUID();

        HrmsProjectTestSchema.insertMember(tenant, hrUser, "hr");
        HrmsProjectTestSchema.insertMember(tenant, managerUser, "manager");
        HrmsProjectTestSchema.insertMember(tenant, employeeUser, "employee");

        TenantContext.set(tenant);
        projectService.create(new ProjectRequest(
                "HR Visible Project",
                "Internal",
                "Managed by managerEmpId",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                Priority.HIGH,
                ProjectStatus.STARTED,
                BigDecimal.valueOf(10000),
                managerEmpId));
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        HrmsTestApp.ENTITLED.remove(tenant);
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        TenantContext.clear();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, UUID sub) {
        return builder.header("X-Tenant-ID", tenant.toString())
                .with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@hrms.test")));
    }

    @Test
    @DisplayName("GET /api/v1/hrms/projects: hr lists all projects")
    void hrCanListAllProjects() throws Exception {
        mvc.perform(authed(get("/api/v1/hrms/projects"), hrUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/hrms/projects: manager with managed=true succeeds; without managed=true is 403")
    void managerRequiresManagedFlag() throws Exception {
        EmployeeResponse managerResponse = new EmployeeResponse(
                managerEmpId,
                tenant,
                "MGR-001",
                "Manager",
                null,
                "One",
                "MALE",
                LocalDate.now(),
                null,
                EmploymentStatus.ACTIVE,
                "manager@test.com",
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
        HrmsTestApp.CURRENT_EMPLOYEE.set(managerResponse);

        // manager holds hrms.project.read_team, so managed=true is allowed
        mvc.perform(authed(get("/api/v1/hrms/projects").param("managed", "true"), managerUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // manager calling without managed=true lacks hrms.project.read -> 403 FORBIDDEN
        mvc.perform(authed(get("/api/v1/hrms/projects"), managerUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("hrms.project.read")));
    }

    @Test
    @DisplayName("POST /api/v1/hrms/projects: employee gets 403 FORBIDDEN; hr gets 201 CREATED")
    void postProjectAuthorization() throws Exception {
        String body =
                """
            {
              "name": "New Portal",
              "priority": "HIGH",
              "status": "STARTED"
            }
            """;

        // Employee lacks hrms.project.manage
        mvc.perform(authed(
                        post("/api/v1/hrms/projects")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        employeeUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("hrms.project.manage")));

        // HR holds hrms.project.manage
        mvc.perform(authed(
                        post("/api/v1/hrms/projects")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        hrUser))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.name").value("New Portal"));
    }

    @Test
    @DisplayName("A tenant that has not bought HRMS is refused on every project endpoint, whatever its roles")
    void tenantWithoutHrmsIsRefused() throws Exception {
        HrmsTestApp.ENTITLED.put(tenant, Set.of(PlatformModule.PAYROLL));
        String body = """
            {"name": "Blocked", "priority": "HIGH", "status": "STARTED"}
            """;

        mvc.perform(authed(get("/api/v1/hrms/projects"), hrUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
        mvc.perform(authed(
                        post("/api/v1/hrms/projects")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        hrUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
        mvc.perform(authed(get("/api/v1/hrms/tasks/mine"), employeeUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
    }
}
