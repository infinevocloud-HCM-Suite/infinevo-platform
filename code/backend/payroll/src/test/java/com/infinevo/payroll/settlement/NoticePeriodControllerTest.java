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

class NoticePeriodControllerTest {

    private MockMvc mockMvc;
    private NoticePeriodService service;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final UUID employeeId = UUID.randomUUID();
    private final LocalDate resignationDate = LocalDate.of(2026, 10, 1);
    private final LocalDate actualExitDate = LocalDate.of(2026, 10, 16);

    @BeforeEach
    void setUp() {
        service = mock(NoticePeriodService.class);
        NoticePeriodController controller = new NoticePeriodController(service);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter)
                .build();
    }

    @Test
    @DisplayName("POST /calculate returns 200 with notice shortfall calculation")
    void calculateNoticePeriod_Returns200() throws Exception {
        NoticePeriodResponse mockResponse = new NoticePeriodResponse(
                null,
                employeeId,
                resignationDate,
                resignationDate.plusDays(30),
                actualExitDate,
                30,
                15,
                15,
                BigDecimal.valueOf(3000.00),
                "GROSS",
                30,
                BigDecimal.valueOf(90000.00),
                "RECOVERY",
                BigDecimal.valueOf(45000.00),
                null,
                null,
                "ESTIMATE");

        when(service.calculateNoticePeriod(any())).thenReturn(mockResponse);

        NoticePeriodCalculateRequest request = new NoticePeriodCalculateRequest(
                employeeId, resignationDate, actualExitDate, 30, "GROSS", 30, false, null);

        mockMvc.perform(post("/api/v1/payroll/settlement/notice-period/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.employeeId").value(employeeId.toString()))
                .andExpect(jsonPath("$.data.shortfallDays").value(15))
                .andExpect(jsonPath("$.data.settlementType").value("RECOVERY"))
                .andExpect(jsonPath("$.data.settlementAmount").value(45000.00));

        // Verify the alias path /api/v1/payroll/notice-period/calculate also resolves
        mockMvc.perform(post("/api/v1/payroll/notice-period/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("POST /record returns 201 with created notice settlement")
    void recordSettlement_Returns201() throws Exception {
        UUID settlementId = UUID.randomUUID();
        NoticePeriodResponse mockResponse = new NoticePeriodResponse(
                settlementId,
                employeeId,
                resignationDate,
                resignationDate.plusDays(30),
                actualExitDate,
                30,
                15,
                15,
                BigDecimal.valueOf(3000.00),
                "GROSS",
                30,
                BigDecimal.valueOf(90000.00),
                "RECOVERY",
                BigDecimal.valueOf(45000.00),
                null,
                "admin",
                "APPROVED");

        when(service.recordSettlement(any())).thenReturn(mockResponse);

        RecordNoticeSettlementRequest request = new RecordNoticeSettlementRequest(
                employeeId, resignationDate, actualExitDate, 30, "GROSS", 30, false, null, "admin", "APPROVED");

        mockMvc.perform(post("/api/v1/payroll/settlement/notice-period/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.id").value(settlementId.toString()))
                .andExpect(jsonPath("$.data.settlementType").value("RECOVERY"))
                .andExpect(jsonPath("$.data.settlementAmount").value(45000.00))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /{employeeId} returns 200 with list of settlements")
    void getSettlements_Returns200() throws Exception {
        NoticePeriodResponse item = new NoticePeriodResponse(
                UUID.randomUUID(),
                employeeId,
                resignationDate,
                resignationDate.plusDays(30),
                actualExitDate,
                30,
                15,
                15,
                BigDecimal.valueOf(3000.00),
                "GROSS",
                30,
                BigDecimal.valueOf(90000.00),
                "RECOVERY",
                BigDecimal.valueOf(45000.00),
                null,
                null,
                "DRAFT");

        when(service.getSettlements(employeeId)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/payroll/settlement/notice-period/{employeeId}", employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].employeeId").value(employeeId.toString()))
                .andExpect(jsonPath("$.data[0].settlementAmount").value(45000.00));
    }

    @Test
    @DisplayName("POST /calculate returns 400 when required fields are missing")
    void calculateNoticePeriod_Invalid_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/payroll/settlement/notice-period/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
