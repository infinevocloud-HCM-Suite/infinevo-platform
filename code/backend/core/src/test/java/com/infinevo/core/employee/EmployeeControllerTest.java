package com.infinevo.core.employee;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** W-84.2 — the limit refusal's envelope, and {@code GET /api/v1/employees/limit}. */
class EmployeeControllerTest {

    private static final String MESSAGE =
            "Your plan allows 2 employees and 2 are in use. Contact Infinevo to raise the limit.";

    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final EmployeeLimitGuard limitGuard = mock(EmployeeLimitGuard.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        EmployeeController controller =
                new EmployeeController(employeeService, mock(EmployeeQueryService.class), limitGuard);
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    @DisplayName("POST over the limit: 409 EMPLOYEE_LIMIT_REACHED with the message as it stands")
    void createOverTheLimitIs409() throws Exception {
        when(employeeService.create(any())).thenThrow(new EmployeeService.EmployeeLimitReachedException(2, 2));

        mvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeNumber\":\"E-3\",\"firstName\":\"Asha\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.message").value(MESSAGE));
    }

    @Test
    @DisplayName("PUT reactivating over the limit: the same 409")
    void reactivateOverTheLimitIs409() throws Exception {
        UUID id = UUID.randomUUID();
        when(employeeService.update(eq(id), any())).thenThrow(new EmployeeService.EmployeeLimitReachedException(2, 2));

        mvc.perform(put("/api/v1/employees/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeNumber\":\"E-1\",\"firstName\":\"Asha\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.message").value(MESSAGE));
    }

    @Test
    @DisplayName("a duplicate number keeps the plain CONFLICT code, so a client can tell the two apart")
    void duplicateKeepsConflict() throws Exception {
        when(employeeService.create(any())).thenThrow(new EmployeeService.DuplicateEmployeeNumberException("E-1"));

        mvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeNumber\":\"E-1\",\"firstName\":\"Asha\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("GET /limit with a limit: {\"limit\":50,\"used\":12}")
    void limitWithALimit() throws Exception {
        when(limitGuard.usage()).thenReturn(new EmployeeLimitGuard.Usage(50, 12));

        mvc.perform(get("/api/v1/employees/limit"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"limit\":50,\"used\":12}", true));
    }

    @Test
    @DisplayName("GET /limit with none: {\"limit\":null,\"used\":12}")
    void limitWithNone() throws Exception {
        when(limitGuard.usage()).thenReturn(new EmployeeLimitGuard.Usage(null, 12));

        mvc.perform(get("/api/v1/employees/limit"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"limit\":null,\"used\":12}", true));
    }
}
