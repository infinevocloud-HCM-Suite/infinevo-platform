package com.infinevo.core.onboarding;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * W-102 review: the controller's own answers — the read guard on another employee's checklist (403), no linked
 * employee on {@code /me/onboarding} (404), an unknown assigned role (400), and two assigns at once (409).
 * Standalone MockMvc does not run {@code @RequiresAction}; {@code OnboardingGuardTest} covers the service rules.
 */
class OnboardingControllerTest {

    private final UUID tenantId = UUID.randomUUID();
    private OnboardingService onboardingService;
    private EmployeeService employeeService;
    private PermissionService permissionService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        onboardingService = mock(OnboardingService.class);
        employeeService = mock(EmployeeService.class);
        permissionService = mock(PermissionService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new EmployeeOnboardingController(onboardingService, employeeService, permissionService),
                        new OnboardingTemplateController(onboardingService))
                .build();
        TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("an employee reading another employee's checklist gets 403 and the service is not asked")
    void readAnotherEmployeesChecklist_403() throws Exception {
        UUID me = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        when(permissionService.currentActions()).thenReturn(Set.of("core.onboarding.read_own"));
        EmployeeResponse self = employee(me);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(self));
        when(onboardingService.isPrimaryManagerOf(tenantId, me, someoneElse)).thenReturn(false);

        mvc.perform(get("/api/v1/employees/{id}/onboarding", someoneElse)).andExpect(status().isForbidden());
        verify(onboardingService, never()).getEmployeeProgress(any(), any());
    }

    @Test
    @DisplayName("an employee reading their own checklist is let through")
    void readOwnChecklist_200() throws Exception {
        UUID me = UUID.randomUUID();
        when(permissionService.currentActions()).thenReturn(Set.of("core.onboarding.read_own"));
        EmployeeResponse self = employee(me);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(self));

        mvc.perform(get("/api/v1/employees/{id}/onboarding", me)).andExpect(status().isOk());
        verify(onboardingService).getEmployeeProgress(tenantId, me);
    }

    @Test
    @DisplayName("/me/onboarding with no linked employee is 404")
    void myChecklistWithoutEmployee_404() throws Exception {
        when(employeeService.currentEmployee()).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/me/onboarding")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a template with assignedRole CEO is 400")
    void templateWithUnknownRole_400() throws Exception {
        mvc.perform(post("/api/v1/onboarding/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"taskType\":\"MANUAL_TASK\",\"assignedRole\":\"CEO\"}"))
                .andExpect(status().isBadRequest());
        verify(onboardingService, never()).createTemplate(any(), any(), anyString());
    }

    @Test
    @DisplayName("a second assign that hits the unique (employee, template) index is 409, not 500")
    void concurrentAssign_409() throws Exception {
        UUID employeeId = UUID.randomUUID();
        when(onboardingService.assignTasksToEmployee(eq(tenantId), eq(employeeId), any()))
                .thenThrow(new DataIntegrityViolationException("uq_employee_onboarding_task_template"));

        mvc.perform(post("/api/v1/employees/{id}/onboarding/assign", employeeId))
                .andExpect(status().isConflict());
    }

    private static EmployeeResponse employee(UUID id) {
        EmployeeResponse employee = mock(EmployeeResponse.class);
        when(employee.id()).thenReturn(id);
        return employee;
    }
}
