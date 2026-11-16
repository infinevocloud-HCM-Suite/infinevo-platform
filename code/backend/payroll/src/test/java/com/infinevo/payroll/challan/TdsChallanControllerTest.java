package com.infinevo.payroll.challan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
 * Controller unit tests for {@link TdsChallanController} (W-89.1).
 */
class TdsChallanControllerTest {

    private TdsChallanService challanService;
    private MockMvc mvc;
    private ObjectMapper objectMapper;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        challanService = mock(TdsChallanService.class);
        TdsChallanController controller = new TdsChallanController(challanService);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter)
                .build();

        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /api/v1/payroll/compliance/challans returns 201 Created")
    void createChallan_returnsCreated() throws Exception {
        UUID challanId = UUID.randomUUID();
        TdsChallanResponse response = new TdsChallanResponse(
                challanId,
                TENANT_ID,
                "2026-2027",
                "Q1",
                "0210045",
                "12345",
                LocalDate.of(2026, 7, 5),
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                new BigDecimal("2000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("52000.00"),
                BigDecimal.ZERO,
                new BigDecimal("52000.00"),
                "200",
                false,
                "Q1 TDS",
                Instant.now(),
                Instant.now());

        when(challanService.create(any(TdsChallanRequest.class))).thenReturn(response);

        TdsChallanRequest request = new TdsChallanRequest(
                "2026-2027",
                "Q1",
                "0210045",
                "12345",
                LocalDate.of(2026, 7, 5),
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                new BigDecimal("2000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "200",
                "Q1 TDS");

        mvc.perform(post("/api/v1/payroll/compliance/challans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.id").value(challanId.toString()))
                .andExpect(jsonPath("$.data.bsrCode").value("0210045"))
                .andExpect(jsonPath("$.data.totalAmount").value(52000.00));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/compliance/challans returns 200 with list")
    void listChallans_returnsOk() throws Exception {
        UUID challanId = UUID.randomUUID();
        TdsChallanResponse response = new TdsChallanResponse(
                challanId,
                TENANT_ID,
                "2026-2027",
                "Q1",
                "0210045",
                "12345",
                LocalDate.of(2026, 7, 5),
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                new BigDecimal("50000.00"),
                "200",
                false,
                null,
                Instant.now(),
                Instant.now());

        when(challanService.list("2026-2027", "Q1")).thenReturn(List.of(response));

        mvc.perform(get("/api/v1/payroll/compliance/challans")
                        .param("financialYear", "2026-2027")
                        .param("quarter", "Q1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].id").value(challanId.toString()))
                .andExpect(jsonPath("$.data[0].bsrCode").value("0210045"));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/compliance/challans/{id} when not found returns 404")
    void getChallan_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(challanService.findById(id)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/payroll/compliance/challans/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /api/v1/payroll/compliance/challans/{id} returns 204 No Content")
    void deleteChallan_returnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(challanService).delete(id);

        mvc.perform(delete("/api/v1/payroll/compliance/challans/{id}", id)).andExpect(status().isNoContent());

        verify(challanService).delete(id);
    }

    @Test
    @DisplayName("POST /api/v1/payroll/compliance/challans/{id}/allocate returns 200 with updated allocation")
    void allocateChallan_returnsOk() throws Exception {
        UUID challanId = UUID.randomUUID();
        UUID payRunId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        ChallanAllocationRequest request = new ChallanAllocationRequest(
                payRunId,
                List.of(new ChallanAllocationRequest.EmployeeAllocationItem(
                        employeeId, new BigDecimal("10000.00"), BigDecimal.ZERO)));

        TdsChallanResponse response = new TdsChallanResponse(
                challanId,
                TENANT_ID,
                "2026-2027",
                "Q1",
                "0210045",
                "12345",
                LocalDate.of(2026, 7, 5),
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("50000.00"),
                new BigDecimal("10000.00"),
                new BigDecimal("40000.00"),
                "200",
                false,
                null,
                Instant.now(),
                Instant.now());

        when(challanService.allocate(eq(challanId), any(ChallanAllocationRequest.class)))
                .thenReturn(response);

        mvc.perform(post("/api/v1/payroll/compliance/challans/{id}/allocate", challanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.allocatedAmount").value(10000.00))
                .andExpect(jsonPath("$.data.unallocatedAmount").value(40000.00));
    }
}
