package com.infinevo.payroll.settlement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

class LeaveEncashmentControllerTest {

    private MockMvc mockMvc;
    private LeaveEncashmentService service;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final UUID employeeId = UUID.randomUUID();
    private final UUID leaveTypeId = UUID.randomUUID();
    private final LocalDate exitDate = LocalDate.of(2026, 11, 30);

    @BeforeEach
    void setUp() {
        service = mock(LeaveEncashmentService.class);
        LeaveEncashmentController controller = new LeaveEncashmentController(service);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter)
                .build();
    }

    @Test
    @DisplayName("POST /estimate returns 200 with calculated leave encashment and tax exemption")
    void estimateEncashment_Returns200() throws Exception {
        LeaveEncashmentResponse mockResponse = new LeaveEncashmentResponse(
                null,
                employeeId,
                leaveTypeId,
                "EL",
                "Earned Leave",
                exitDate,
                BigDecimal.valueOf(18.00),
                BigDecimal.valueOf(2000.00),
                BigDecimal.valueOf(60000.00),
                30,
                BigDecimal.valueOf(36000.00),
                BigDecimal.valueOf(36000.00),
                BigDecimal.ZERO,
                BigDecimal.valueOf(2500000.00),
                "ESTIMATE");

        when(service.estimateEncashment(any())).thenReturn(mockResponse);

        LeaveEncashmentEstimateRequest request =
                new LeaveEncashmentEstimateRequest(employeeId, exitDate, leaveTypeId, null, 30);

        mockMvc.perform(post("/api/v1/payroll/settlement/leave-encashment/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.employeeId").value(employeeId.toString()))
                .andExpect(jsonPath("$.data.grossEncashmentAmount").value(36000.00))
                .andExpect(jsonPath("$.data.taxExemptAmount").value(36000.00))
                .andExpect(jsonPath("$.data.taxableAmount").value(0))
                .andExpect(jsonPath("$.data.status").value("ESTIMATE"));
    }

    @Test
    @DisplayName("POST /record returns 201 with created leave encashment settlement")
    void recordSettlement_Returns201() throws Exception {
        UUID settlementId = UUID.randomUUID();
        LeaveEncashmentResponse mockResponse = new LeaveEncashmentResponse(
                settlementId,
                employeeId,
                leaveTypeId,
                "EL",
                "Earned Leave",
                exitDate,
                BigDecimal.valueOf(15.00),
                BigDecimal.valueOf(2000.00),
                BigDecimal.valueOf(60000.00),
                30,
                BigDecimal.valueOf(30000.00),
                BigDecimal.valueOf(30000.00),
                BigDecimal.ZERO,
                BigDecimal.valueOf(2500000.00),
                "APPROVED");

        when(service.recordSettlement(any())).thenReturn(mockResponse);

        RecordLeaveEncashmentRequest request = new RecordLeaveEncashmentRequest(
                employeeId, exitDate, leaveTypeId, BigDecimal.valueOf(15.00), 30, "APPROVED");

        mockMvc.perform(post("/api/v1/payroll/settlement/leave-encashment/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.id").value(settlementId.toString()))
                .andExpect(jsonPath("$.data.grossEncashmentAmount").value(30000.00))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /{employeeId} returns 200 with list of settlements")
    void getSettlements_Returns200() throws Exception {
        LeaveEncashmentResponse item = new LeaveEncashmentResponse(
                UUID.randomUUID(),
                employeeId,
                leaveTypeId,
                "EL",
                "Earned Leave",
                exitDate,
                BigDecimal.valueOf(10.00),
                BigDecimal.valueOf(2000.00),
                BigDecimal.valueOf(60000.00),
                30,
                BigDecimal.valueOf(20000.00),
                BigDecimal.valueOf(20000.00),
                BigDecimal.ZERO,
                BigDecimal.valueOf(2500000.00),
                "DRAFT");

        when(service.getSettlements(employeeId)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/payroll/settlement/leave-encashment/{employeeId}", employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].employeeId").value(employeeId.toString()))
                .andExpect(jsonPath("$.data[0].grossEncashmentAmount").value(20000.00));
    }

    @Test
    @DisplayName("POST /estimate returns 400 when required fields are missing")
    void estimateEncashment_InvalidRequest_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/payroll/settlement/leave-encashment/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
