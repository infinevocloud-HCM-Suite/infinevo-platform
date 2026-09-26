package com.infinevo.core.employee;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.employee.detail.EmployeeContactRequest;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.identity.UserAccount;
import com.infinevo.shared.identity.UserProfileSyncService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test for W-13.4 employee self-service ownership rules.
 *
 * <p>An employee holding {@code core.employee.update_own} can update their own personal and contact
 * sections (200), but is refused (403) when attempting to update another employee's details.
 * An administrator/HR holding {@code core.employee.update} can update any employee.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, RedisTestContainerInitializer.class})
class EmployeeSelfServiceIT extends AbstractIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private UserProfileSyncService userProfileSyncService;

    private UUID keycloakUser1;
    private EmployeeResponse emp1;
    private EmployeeResponse emp2;

    @BeforeAll
    static void applySchema() throws Exception {
        EmployeeTestSchema.apply();
    }

    @BeforeEach
    void seed() throws Exception {
        EmployeeTestSchema.seedTenants();
        EmployeeTestSchema.clearEmployees();
        TenantContext.set(TENANT_ID);

        keycloakUser1 = UUID.randomUUID();
        EmployeeTestSchema.seedMembership(keycloakUser1, TENANT_ID);
        userProfileSyncService.sync(TENANT_ID, keycloakUser1, "emp1@example.com", "Emp", "One");
        UserAccount account1 =
                userProfileSyncService.find(TENANT_ID, keycloakUser1).orElseThrow();
        EmployeeTestSchema.grantRole(TENANT_ID, account1.getId(), "employee");

        // Create two employees
        emp1 = employeeService.create(new EmployeeRequest(
                "EMP-SS-1",
                "Emp",
                null,
                "One",
                "M",
                LocalDate.of(2026, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                "emp1@example.com",
                null,
                true,
                null,
                null,
                null));
        emp2 = employeeService.create(new EmployeeRequest(
                "EMP-SS-2",
                "Emp",
                null,
                "Two",
                "F",
                LocalDate.of(2026, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                "emp2@example.com",
                null,
                true,
                null,
                null,
                null));

        // Link emp1 to account1
        employeeService.linkLogin(emp1.id(), account1.getId());
    }

    @Test
    @DisplayName("Employee updating their own contact section with update_own returns 200")
    void employeeUpdatesOwnContactSuccessfully() throws Exception {
        EmployeeContactRequest request = new EmployeeContactRequest(
                "emp1.personal@example.com",
                "9998887777",
                "123 Main St",
                null,
                "City",
                "State",
                "ST",
                "12345",
                null,
                null,
                null,
                null,
                null,
                null,
                "Emergency Contact",
                "9991112222",
                "Spouse",
                null,
                null,
                null,
                null,
                null);

        mvc.perform(put("/api/v1/employees/{id}/contact", emp1.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder.subject(keycloakUser1.toString())
                                        .claim("tenant_id", TENANT_ID.toString()))
                                .authorities(List.of(new SimpleGrantedAuthority("core.employee.update_own")))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Employee updating another employee's contact section with update_own returns 403")
    void employeeUpdatingOthersContactRefused() throws Exception {
        EmployeeContactRequest request = new EmployeeContactRequest(
                "emp2.personal@example.com",
                "9998887777",
                "123 Main St",
                null,
                "City",
                "State",
                "ST",
                "12345",
                null,
                null,
                null,
                null,
                null,
                null,
                "Emergency Contact",
                "9991112222",
                "Spouse",
                null,
                null,
                null,
                null,
                null);

        mvc.perform(put("/api/v1/employees/{id}/contact", emp2.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder.subject(keycloakUser1.toString())
                                        .claim("tenant_id", TENANT_ID.toString()))
                                .authorities(List.of(new SimpleGrantedAuthority("core.employee.update_own")))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HR holding update permission can update any employee's contact section (200)")
    void hrUpdatesAnyEmployeeContactSuccessfully() throws Exception {
        UUID hrUser = UUID.randomUUID();
        EmployeeTestSchema.seedMembership(hrUser, TENANT_ID);
        userProfileSyncService.sync(TENANT_ID, hrUser, "hr@example.com", "HR", "User");
        UserAccount hrAccount = userProfileSyncService.find(TENANT_ID, hrUser).orElseThrow();
        EmployeeTestSchema.grantRole(TENANT_ID, hrAccount.getId(), "hr");

        EmployeeContactRequest request = new EmployeeContactRequest(
                "emp2.personal@example.com",
                "9998887777",
                "123 Main St",
                null,
                "City",
                "State",
                "ST",
                "12345",
                null,
                null,
                null,
                null,
                null,
                null,
                "Emergency Contact",
                "9991112222",
                "Spouse",
                null,
                null,
                null,
                null,
                null);

        mvc.perform(put("/api/v1/employees/{id}/contact", emp2.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder ->
                                        builder.subject(hrUser.toString()).claim("tenant_id", TENANT_ID.toString()))
                                .authorities(List.of(new SimpleGrantedAuthority("core.employee.update")))))
                .andExpect(status().isOk());
    }
}
