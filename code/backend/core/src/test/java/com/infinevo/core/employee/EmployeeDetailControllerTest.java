package com.infinevo.core.employee;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.employee.detail.EmployeeBankService;
import com.infinevo.core.employee.detail.EmployeeContactRequest;
import com.infinevo.core.employee.detail.EmployeeContactService;
import com.infinevo.core.employee.detail.EmployeeEmploymentService;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.core.employee.detail.EmployeePersonalRequest;
import com.infinevo.core.employee.detail.EmployeePersonalResponse;
import com.infinevo.core.employee.detail.EmployeePersonalService;
import com.infinevo.core.option.TenantOptionService;
import com.infinevo.shared.authz.PermissionService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EmployeeDetailControllerTest {

    private MockMvc mvc;
    private EmployeePersonalService personalService;
    private EmployeeContactService contactService;
    private EmployeeIdentificationService identificationService;
    private EmployeeEmploymentService employmentService;
    private EmployeeBankService bankService;
    private PermissionService permissionService;
    private TenantOptionService tenantOptionService;
    private ObjectMapper objectMapper;

    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        personalService = mock(EmployeePersonalService.class);
        contactService = mock(EmployeeContactService.class);
        identificationService = mock(EmployeeIdentificationService.class);
        employmentService = mock(EmployeeEmploymentService.class);
        bankService = mock(EmployeeBankService.class);
        permissionService = mock(PermissionService.class);
        tenantOptionService = mock(TenantOptionService.class);
        objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        EmployeeDetailController controller = new EmployeeDetailController(
                personalService,
                contactService,
                identificationService,
                employmentService,
                bankService,
                permissionService,
                tenantOptionService);

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("Self-edit off: caller holding only update_own gets 403 SELF_EDIT_DISABLED on PUT personal")
    void selfEditOffOwnPersonalUpdateReturns403() throws Exception {
        when(permissionService.holds("core.employee.update")).thenReturn(false);
        when(tenantOptionService.getBoolean("core.employee.self_edit")).thenReturn(false);

        EmployeePersonalRequest req =
                new EmployeePersonalRequest(LocalDate.of(1990, 1, 1), "MARRIED", "Indian", null, "Father", null, false);

        mvc.perform(put("/api/v1/employees/" + employeeId + "/personal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_EDIT_DISABLED"))
                .andExpect(jsonPath("$.message").value("SELF_EDIT_DISABLED"));

        verify(personalService, never()).put(any(), any());
    }

    @Test
    @DisplayName("Self-edit off: caller holding only update_own gets 403 SELF_EDIT_DISABLED on PUT contact")
    void selfEditOffOwnContactUpdateReturns403() throws Exception {
        when(permissionService.holds("core.employee.update")).thenReturn(false);
        when(tenantOptionService.getBoolean("core.employee.self_edit")).thenReturn(false);

        EmployeeContactRequest req = new EmployeeContactRequest(
                "test@test.local",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        mvc.perform(put("/api/v1/employees/" + employeeId + "/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_EDIT_DISABLED"))
                .andExpect(jsonPath("$.message").value("SELF_EDIT_DISABLED"));

        verify(contactService, never()).put(any(), any());
    }

    @Test
    @DisplayName("Self-edit off: caller holding core.employee.update (HR) still passes")
    void selfEditOffHrUpdatePasses() throws Exception {
        when(permissionService.holds("core.employee.update")).thenReturn(true);
        when(tenantOptionService.getBoolean("core.employee.self_edit")).thenReturn(false);

        EmployeePersonalRequest req =
                new EmployeePersonalRequest(LocalDate.of(1990, 1, 1), "MARRIED", "Indian", null, "Father", null, false);
        EmployeePersonalResponse resp = new EmployeePersonalResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                employeeId,
                LocalDate.of(1990, 1, 1),
                "MARRIED",
                "Indian",
                null,
                "Father",
                null,
                false,
                java.time.Instant.now(),
                java.time.Instant.now());
        when(personalService.put(eq(employeeId), any())).thenReturn(resp);

        mvc.perform(put("/api/v1/employees/" + employeeId + "/personal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(personalService).put(eq(employeeId), any());
    }

    @Test
    @DisplayName("Self-edit on: caller holding update_own passes")
    void selfEditOnOwnUpdatePasses() throws Exception {
        when(permissionService.holds("core.employee.update")).thenReturn(false);
        when(tenantOptionService.getBoolean("core.employee.self_edit")).thenReturn(true);

        EmployeePersonalRequest req =
                new EmployeePersonalRequest(LocalDate.of(1990, 1, 1), "MARRIED", "Indian", null, "Father", null, false);
        EmployeePersonalResponse resp = new EmployeePersonalResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                employeeId,
                LocalDate.of(1990, 1, 1),
                "MARRIED",
                "Indian",
                null,
                "Father",
                null,
                false,
                java.time.Instant.now(),
                java.time.Instant.now());
        when(personalService.put(eq(employeeId), any())).thenReturn(resp);

        mvc.perform(put("/api/v1/employees/" + employeeId + "/personal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(personalService).put(eq(employeeId), any());
    }
}
