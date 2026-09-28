package com.infinevo.payroll.component;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller unit tests verifying HTTP contracts, JSON structures, status codes and error handling (W-26.1).
 */
class ComponentControllerTest {

    private EarningService earningService;
    private DeductionService deductionService;
    private BenefitService benefitService;
    private ReimbursementService reimbursementService;

    private MockMvc earningMvc;
    private MockMvc deductionMvc;
    private MockMvc benefitMvc;
    private MockMvc reimbursementMvc;

    private static final UUID TEST_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        earningService = mock(EarningService.class);
        deductionService = mock(DeductionService.class);
        benefitService = mock(BenefitService.class);
        reimbursementService = mock(ReimbursementService.class);

        org.springframework.http.converter.json.MappingJackson2HttpMessageConverter jsonConverter =
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter();
        earningMvc = MockMvcBuilders.standaloneSetup(new EarningController(earningService))
                .setMessageConverters(jsonConverter)
                .build();
        deductionMvc = MockMvcBuilders.standaloneSetup(new DeductionController(deductionService))
                .setMessageConverters(jsonConverter)
                .build();
        benefitMvc = MockMvcBuilders.standaloneSetup(new BenefitController(benefitService))
                .setMessageConverters(jsonConverter)
                .build();
        reimbursementMvc = MockMvcBuilders.standaloneSetup(new ReimbursementController(reimbursementService))
                .setMessageConverters(jsonConverter)
                .build();
    }

    @Test
    @DisplayName("POST /earnings returns 201 with Location header and response body")
    void postEarningSuccess() throws Exception {
        EarningResponse response = sampleEarning();
        when(earningService.create(any())).thenReturn(response);

        earningMvc
                .perform(
                        post("/api/v1/payroll/components/earnings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                            "code": "BASIC",
                            "name": "Basic Salary",
                            "earningType": "FIXED",
                            "calculationType": "FLAT",
                            "defaultValue": 50000.0000
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/payroll/components/earnings/" + TEST_ID))
                .andExpect(jsonPath("$.id").value(TEST_ID.toString()))
                .andExpect(jsonPath("$.code").value("BASIC"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("GET /earnings returns 200 list")
    void getEarningsList() throws Exception {
        when(earningService.list(false)).thenReturn(List.of(sampleEarning()));

        earningMvc
                .perform(get("/api/v1/payroll/components/earnings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("BASIC"));
    }

    @Test
    @DisplayName("GET /earnings/{id} returns 200")
    void getEarningById() throws Exception {
        when(earningService.get(TEST_ID)).thenReturn(sampleEarning());

        earningMvc
                .perform(get("/api/v1/payroll/components/earnings/" + TEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_ID.toString()));
    }

    @Test
    @DisplayName("GET /earnings/{id} returns 404 when not found")
    void getEarningNotFound() throws Exception {
        when(earningService.get(TEST_ID)).thenThrow(new ComponentNotFoundException("earning", TEST_ID));

        earningMvc
                .perform(get("/api/v1/payroll/components/earnings/" + TEST_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /earnings/{id} returns 200")
    void putEarningSuccess() throws Exception {
        when(earningService.update(eq(TEST_ID), any())).thenReturn(sampleEarning());

        earningMvc
                .perform(
                        put("/api/v1/payroll/components/earnings/" + TEST_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                            "code": "BASIC",
                            "name": "Basic Salary Updated",
                            "earningType": "FIXED",
                            "calculationType": "FLAT"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BASIC"));
    }

    @Test
    @DisplayName("PUT /earnings/{id}/active returns 200")
    void putEarningActive() throws Exception {
        when(earningService.updateActive(TEST_ID, false)).thenReturn(sampleEarning());

        earningMvc
                .perform(put("/api/v1/payroll/components/earnings/" + TEST_ID + "/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /earnings/{id} returns 204")
    void deleteEarning() throws Exception {
        doNothing().when(earningService).delete(TEST_ID);

        earningMvc
                .perform(delete("/api/v1/payroll/components/earnings/" + TEST_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /deductions returns 201")
    void postDeductionSuccess() throws Exception {
        DeductionResponse resp = new DeductionResponse(
                TEST_ID,
                TENANT_ID,
                "PF",
                "Provident Fund",
                "PF",
                "STATUTORY",
                CalculationType.FLAT,
                new BigDecimal("1800.00"),
                null,
                null,
                true,
                true,
                null,
                null,
                null,
                true,
                Instant.now(),
                "sys",
                Instant.now(),
                "sys");
        when(deductionService.create(any())).thenReturn(resp);

        deductionMvc
                .perform(
                        post("/api/v1/payroll/components/deductions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                            "code": "PF",
                            "name": "Provident Fund",
                            "deductionType": "STATUTORY"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PF"));
    }

    @Test
    @DisplayName("POST /benefits returns 201")
    void postBenefitSuccess() throws Exception {
        BenefitResponse resp = new BenefitResponse(
                TEST_ID,
                TENANT_ID,
                "HEALTH",
                "Health Plan",
                null,
                null,
                null,
                CalculationType.FLAT,
                new BigDecimal("5000.00"),
                null,
                null,
                true,
                false,
                false,
                false,
                true,
                true,
                true,
                true,
                null,
                null,
                true,
                Instant.now(),
                "sys",
                Instant.now(),
                "sys");
        when(benefitService.create(any())).thenReturn(resp);

        benefitMvc
                .perform(
                        post("/api/v1/payroll/components/benefits")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                            "code": "HEALTH",
                            "name": "Health Plan"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("HEALTH"));
    }

    @Test
    @DisplayName("POST /reimbursements returns 201")
    void postReimbursementSuccess() throws Exception {
        ReimbursementResponse resp = new ReimbursementResponse(
                TEST_ID,
                TENANT_ID,
                "FUEL",
                "Fuel Allowance",
                null,
                "EXPENSE",
                CalculationType.FLAT,
                new BigDecimal("2000.00"),
                null,
                null,
                null,
                false,
                false,
                false,
                false,
                true,
                Instant.now(),
                "sys",
                Instant.now(),
                "sys");
        when(reimbursementService.create(any())).thenReturn(resp);

        reimbursementMvc
                .perform(
                        post("/api/v1/payroll/components/reimbursements")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                            "code": "FUEL",
                            "name": "Fuel Allowance",
                            "reimbursementType": "EXPENSE"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("FUEL"));
    }

    @Test
    @DisplayName("Validation exception maps to 400 with VALIDATION_FAILED")
    void validationExceptionResponse() throws Exception {
        when(earningService.create(any())).thenThrow(new ComponentValidationException("code", "Code is required"));

        earningMvc
                .perform(post("/api/v1/payroll/components/earnings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.code").value("Code is required"));
    }

    private static EarningResponse sampleEarning() {
        return new EarningResponse(
                TEST_ID,
                TENANT_ID,
                "BASIC",
                "Basic Salary",
                "Basic",
                "FIXED",
                CalculationType.FLAT,
                new BigDecimal("50000.0000"),
                null,
                null,
                "MONTHLY",
                null,
                false,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                null,
                false,
                true,
                true,
                Instant.now(),
                "sys",
                Instant.now(),
                "sys");
    }
}
