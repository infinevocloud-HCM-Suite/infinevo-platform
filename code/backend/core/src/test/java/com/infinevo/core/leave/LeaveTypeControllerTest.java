package com.infinevo.core.leave;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.shared.tenant.TenantContext;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit test for {@link LeaveTypeController} (W-16.1 error handling).
 * Verifies that:
 * - Missing resources return 404 NOT_FOUND error envelope
 * - Validation errors and bad arguments return 400 VALIDATION_FAILED error envelope
 * - Unreadable / malformed request bodies return 400
 */
class LeaveTypeControllerTest {

    private MockMvc mvc;
    private LeaveTypeService leaveTypeService;
    private LeaveEligibilityService leaveEligibilityService;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        leaveTypeService = mock(LeaveTypeService.class);
        leaveEligibilityService = mock(LeaveEligibilityService.class);
        LeaveTypeController controller = new LeaveTypeController(leaveTypeService, leaveEligibilityService);

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
        TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("PUT with non-existent ID returns 404 with standard error envelope (W-16.1)")
    void updateNonExistentLeaveTypeReturns404() throws Exception {
        UUID typeId = UUID.randomUUID();
        when(leaveTypeService.updateLeaveType(eq(tenantId), eq(typeId), any()))
                .thenThrow(new NoSuchElementException("Leave type not found: " + typeId));

        String body =
                """
                {
                    "name": "Updated Sick Leave",
                    "unit": "DAYS",
                    "allowHalfDay": true,
                    "isActive": true
                }
                """;

        mvc.perform(put("/api/v1/leave-types/" + typeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Leave type not found: " + typeId));
    }

    @Test
    @DisplayName("PUT policy with non-existent ID returns 404 with standard error envelope (W-16.1)")
    void configurePolicyNonExistentLeaveTypeReturns404() throws Exception {
        UUID typeId = UUID.randomUUID();
        when(leaveTypeService.configurePolicy(eq(tenantId), eq(typeId), any()))
                .thenThrow(new NoSuchElementException("Leave type not found: " + typeId));

        String body =
                """
                {
                    "effectiveFrom": "2026-04-01",
                    "annualDays": 12.00
                }
                """;

        mvc.perform(put("/api/v1/leave-types/" + typeId + "/policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Leave type not found: " + typeId));
    }

    @Test
    @DisplayName("POST with duplicate code returns 400 with standard error envelope (W-16.1)")
    void createDuplicateCodeReturns400() throws Exception {
        when(leaveTypeService.createLeaveType(eq(tenantId), any()))
                .thenThrow(new IllegalArgumentException("Leave type with code 'AL' already exists"));

        String body =
                """
                {
                    "code": "AL",
                    "name": "Annual Leave",
                    "unit": "DAYS",
                    "allowHalfDay": true
                }
                """;

        mvc.perform(post("/api/v1/leave-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Leave type with code 'AL' already exists"));
    }

    @Test
    @DisplayName("PUT policy with back-dated effectiveFrom returns 400 (W-16.1)")
    void configurePolicyWithBackdatedEffectiveFromReturns400() throws Exception {
        UUID typeId = UUID.randomUUID();
        when(leaveTypeService.configurePolicy(eq(tenantId), eq(typeId), any()))
                .thenThrow(
                        new IllegalArgumentException(
                                "Policy effectiveFrom date 2025-01-01 cannot be before the start of the current leave year 2026-04-01"));

        String body =
                """
                {
                    "effectiveFrom": "2025-01-01",
                    "annualDays": 12.00
                }
                """;

        mvc.perform(put("/api/v1/leave-types/" + typeId + "/policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Policy effectiveFrom date 2025-01-01 cannot be before the start of the current leave year 2026-04-01"));
    }

    @Test
    @DisplayName("POST with malformed JSON body returns 400 (W-16.1)")
    void malformedBodyReturns400() throws Exception {
        mvc.perform(post("/api/v1/leave-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request body is unreadable or malformed"));
    }
}
