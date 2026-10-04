package com.infinevo.hrms.project;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-48.1 section 7: {@code GET /api/v1/hrms/employees/assignable} - a manager finds active employees of the bound
 * tenant by name or number; an employee is refused; another tenant's and inactive employees never match; a one
 * character query gives an empty list.
 */
@SpringBootTest(classes = HrmsTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsProjectTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class AssignableEmployeeIT extends AbstractIntegrationTest {

    private static final String PATH = "/api/v1/hrms/employees/assignable";

    @Autowired
    private MockMvc mvc;

    private UUID tenant;
    private UUID managerUser;
    private UUID employeeUser;
    private UUID activeId;
    private String prefix;

    @BeforeEach
    void seed() throws SQLException {
        prefix = "Q" + UUID.randomUUID().toString().substring(0, 6);
        tenant = HrmsProjectTestSchema.insertTenant("Assignable " + UUID.randomUUID());
        UUID otherTenant = HrmsProjectTestSchema.insertTenant("Assignable other " + UUID.randomUUID());
        HrmsTestApp.ENTITLED.put(tenant, Set.of(PlatformModule.HRMS));

        activeId = HrmsProjectTestSchema.insertEmployee(tenant, prefix + "-A");
        HrmsProjectTestSchema.update(
                "UPDATE core.employee SET first_name = 'Asha', last_name = 'Roy' WHERE id = ?", activeId);
        UUID inactive = HrmsProjectTestSchema.insertEmployee(tenant, prefix + "-I");
        HrmsProjectTestSchema.update("UPDATE core.employee SET status = 'TERMINATED' WHERE id = ?", inactive);
        HrmsProjectTestSchema.insertEmployee(otherTenant, prefix + "-O");

        managerUser = UUID.randomUUID();
        employeeUser = UUID.randomUUID();
        HrmsProjectTestSchema.insertMember(tenant, managerUser, "manager");
        HrmsProjectTestSchema.insertMember(tenant, employeeUser, "employee");
    }

    @AfterEach
    void cleanup() {
        HrmsTestApp.ENTITLED.remove(tenant);
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, UUID sub) {
        return builder.header("X-Tenant-ID", tenant.toString())
                .with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@hrms.test")));
    }

    @Test
    @DisplayName("manager: a number prefix matches only this tenant's active employee")
    void managerGetsActiveMatchesOfOwnTenant() throws Exception {
        mvc.perform(authed(get(PATH).param("q", prefix), managerUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].employee_id").value(activeId.toString()))
                .andExpect(jsonPath("$.data[0].employee_number").value(prefix + "-A"))
                .andExpect(jsonPath("$.data[0].name").value("Asha Roy"));
    }

    @Test
    @DisplayName("manager: a name prefix matches too")
    void managerMatchesByName() throws Exception {
        mvc.perform(authed(get(PATH).param("q", "ash"), managerUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.employee_id == '" + activeId + "')]")
                        .exists());
    }

    @Test
    @DisplayName("q of one character, or none, gives an empty list")
    void shortQueryIsEmpty() throws Exception {
        mvc.perform(authed(get(PATH).param("q", " Q "), managerUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(authed(get(PATH), managerUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("employee role: 403")
    void employeeIsForbidden() throws Exception {
        mvc.perform(authed(get(PATH).param("q", prefix), employeeUser)).andExpect(status().isForbidden());
    }
}
