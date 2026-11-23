package com.infinevo.payroll.settlement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
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

class FnfSettlementControllerTest {

    private MockMvc mockMvc;
    private FnfSettlementService service;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final UUID settlementId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final LocalDate resignationDate = LocalDate.of(2026, 10, 1);
    private final LocalDate exitDate = LocalDate.of(2026, 10, 15);

    private FnfSettlementResponse sampleResponse(String status) {
        return new FnfSettlementResponse(
                settlementId,
                employeeId,
                resignationDate,
                exitDate,
                "RESIGNATION",
                15,
                BigDecimal.valueOf(25000.00),
                BigDecimal.valueOf(15000.00),
                BigDecimal.valueOf(50000.00),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(90000.00),
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(1800.00),
                BigDecimal.ZERO,
                BigDecimal.valueOf(200.00),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(7000.00),
                BigDecimal.valueOf(83000.00),
                status,
                status.equals("APPROVED") ? "hr-admin" : null,
                status.equals("APPROVED") ? Instant.now() : null,
                status.equals("PAID") ? Instant.now() : null,
                "Sample F&F",
                Instant.now());
    }

    @BeforeEach
    void setUp() {
        service = mock(FnfSettlementService.class);
        FnfSettlementController controller = new FnfSettlementController(service);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter)
                .build();
    }

    @Test
    @DisplayName("POST /initiate returns 201 with initiated F&F settlement")
    void initiateSettlement_Returns201() throws Exception {
        when(service.initiateSettlement(any())).thenReturn(sampleResponse("DRAFT"));

        FnfInitiateRequest request = new FnfInitiateRequest(
                employeeId, resignationDate, exitDate, "RESIGNATION", 15, null, null, "Standard exit");

        mockMvc.perform(post("/api/v1/payroll/settlement/fnf/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.employeeId").value(employeeId.toString()))
                .andExpect(jsonPath("$.data.grossEarnings").value(90000.00))
                .andExpect(jsonPath("$.data.netPayable").value(83000.00))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        // Verify alias route /api/v1/payroll/fnf/initiate
        mockMvc.perform(post("/api/v1/payroll/fnf/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET /{id} returns 200 with F&F settlement details")
    void getSettlement_Returns200() throws Exception {
        when(service.getSettlement(settlementId)).thenReturn(sampleResponse("DRAFT"));

        mockMvc.perform(get("/api/v1/payroll/settlement/fnf/{id}", settlementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(settlementId.toString()));
    }

    @Test
    @DisplayName("GET / returns 200 with list of settlements")
    void listSettlements_Returns200() throws Exception {
        when(service.listSettlements()).thenReturn(List.of(sampleResponse("DRAFT")));

        mockMvc.perform(get("/api/v1/payroll/settlement/fnf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].id").value(settlementId.toString()));
    }

    @Test
    @DisplayName("POST /{id}/approve returns 200 with approved status")
    void approveSettlement_Returns200() throws Exception {
        when(service.approveSettlement(eq(settlementId), any())).thenReturn(sampleResponse("APPROVED"));

        mockMvc.perform(post("/api/v1/payroll/settlement/fnf/{id}/approve", settlementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("POST /{id}/pay returns 200 with paid status")
    void paySettlement_Returns200() throws Exception {
        when(service.paySettlement(settlementId)).thenReturn(sampleResponse("PAID"));

        mockMvc.perform(post("/api/v1/payroll/settlement/fnf/{id}/pay", settlementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    @DisplayName("POST /initiate with missing exitDate returns 400 Bad Request")
    void initiateSettlement_Invalid_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/payroll/settlement/fnf/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
