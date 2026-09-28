package com.infinevo.payroll.salary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SalaryControllerTest {

    private MockMvc mockMvc;
    private EmployeeSalaryService salaryService;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final UUID employeeId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID versionId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        salaryService = mock(EmployeeSalaryService.class);
        EmployeeSalaryController controller = new EmployeeSalaryController(salaryService);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(mapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter)
                .build();
    }

    @Test
    @DisplayName("POST /salary creates initial version and returns 201")
    void createInitialSalary_returns201() throws Exception {
        SalaryVersionRequest request = new SalaryVersionRequest(
                new BigDecimal("600000.00"), LocalDate.of(2026, 1, 1), "Initial", List.of(), List.of(), List.of());

        SalaryVersionResponse response = new SalaryVersionResponse(
                versionId,
                employeeId,
                LocalDate.of(2026, 1, 1),
                new BigDecimal("600000.00"),
                new BigDecimal("50000.00"),
                false,
                null,
                "Initial",
                null,
                List.of(),
                List.of(),
                List.of());

        when(salaryService.create(eq(employeeId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payroll/employees/{id}/salary", employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                                "Location",
                                "/api/v1/payroll/employees/" + employeeId + "/salary/versions/" + versionId))
                .andExpect(jsonPath("$.id").value(versionId.toString()));
    }

    @Test
    @DisplayName("POST /salary/revisions creates a new revision version and returns 201")
    void reviseSalary_returns201() throws Exception {
        SalaryVersionRequest request = new SalaryVersionRequest(
                new BigDecimal("700000.00"), LocalDate.of(2026, 4, 1), "Promotion", List.of(), List.of(), List.of());

        SalaryVersionResponse response = new SalaryVersionResponse(
                versionId,
                employeeId,
                LocalDate.of(2026, 4, 1),
                new BigDecimal("700000.00"),
                new BigDecimal("58333.3333"),
                false,
                null,
                "Promotion",
                new BigDecimal("16.67"),
                List.of(),
                List.of(),
                List.of());

        when(salaryService.revise(eq(employeeId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payroll/employees/{id}/salary/revisions", employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.annualCtc").value(700000.00));
    }

    @Test
    @DisplayName("GET /salary returns version in force as of date")
    void getSalaryAsOf_returns200() throws Exception {
        LocalDate asOf = LocalDate.of(2026, 2, 1);
        SalaryVersionResponse response = new SalaryVersionResponse(
                versionId,
                employeeId,
                LocalDate.of(2026, 1, 1),
                new BigDecimal("600000.00"),
                new BigDecimal("50000.00"),
                false,
                null,
                "Initial",
                null,
                List.of(),
                List.of(),
                List.of());

        when(salaryService.getAsOf(employeeId, asOf)).thenReturn(response);

        mockMvc.perform(get("/api/v1/payroll/employees/{id}/salary?asOf=2026-02-01", employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(versionId.toString()));
    }

    @Test
    @DisplayName("GET /salary/versions returns full history of versions")
    void listVersions_returns200() throws Exception {
        SalaryVersionResponse v1 = new SalaryVersionResponse(
                versionId,
                employeeId,
                LocalDate.of(2026, 1, 1),
                new BigDecimal("600000.00"),
                new BigDecimal("50000.00"),
                false,
                null,
                "Initial",
                null,
                List.of(),
                List.of(),
                List.of());

        when(salaryService.listVersions(employeeId)).thenReturn(List.of(v1));

        mockMvc.perform(get("/api/v1/payroll/employees/{id}/salary/versions", employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(versionId.toString()));
    }

    @Test
    @DisplayName("PUT /salary/versions/{id} updates future-dated version")
    void updateVersion_returns200() throws Exception {
        SalaryVersionRequest request = new SalaryVersionRequest(
                new BigDecimal("650000.00"), LocalDate.of(2026, 12, 1), "Update", List.of(), List.of(), List.of());

        SalaryVersionResponse response = new SalaryVersionResponse(
                versionId,
                employeeId,
                LocalDate.of(2026, 12, 1),
                new BigDecimal("650000.00"),
                new BigDecimal("54166.6667"),
                false,
                null,
                "Update",
                null,
                List.of(),
                List.of(),
                List.of());

        when(salaryService.update(eq(employeeId), eq(versionId), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/payroll/employees/{id}/salary/versions/{vid}", employeeId, versionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualCtc").value(650000.00));
    }

    @Test
    @DisplayName("DELETE /salary/versions/{id} cancels future-dated version and returns 204")
    void cancelVersion_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/payroll/employees/{id}/salary/versions/{vid}", employeeId, versionId))
                .andExpect(status().isNoContent());

        verify(salaryService).cancel(employeeId, versionId);
    }

    @Test
    @DisplayName("Conflict error maps to 409 status")
    void conflictMapsTo409() throws Exception {
        when(salaryService.create(eq(employeeId), any())).thenThrow(new SalaryConflictException("Version conflict"));

        SalaryVersionRequest request = new SalaryVersionRequest(
                new BigDecimal("600000.00"), LocalDate.now(), null, List.of(), List.of(), List.of());

        mockMvc.perform(post("/api/v1/payroll/employees/{id}/salary", employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }
}
