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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.challan.TdsChallanResponse.AllocationStatus;
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
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller unit tests for {@link TdsChallanController} (W-89.1).
 */
class TdsChallanControllerTest {

    private static final String BASE = "/api/v1/payroll/compliance/challans";
    private static final UUID TENANT_ID = UUID.randomUUID();

    private TdsChallanService challanService;
    private MockMvc mvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        challanService = mock(TdsChallanService.class);
        TdsChallanController controller = new TdsChallanController(challanService);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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

    private static TdsChallanResponse response(UUID id, String allocated, AllocationStatus status) {
        BigDecimal allocatable = new BigDecimal("52000.00");
        BigDecimal a = new BigDecimal(allocated);
        return new TdsChallanResponse(
                id,
                "2026-2027",
                "Q1",
                "0210045",
                "12345",
                LocalDate.of(2026, 7, 5),
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                new BigDecimal("2000.00"),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                new BigDecimal("52150.00"),
                allocatable,
                a,
                allocatable.subtract(a),
                status,
                "200",
                false,
                "Q1 TDS",
                Instant.now(),
                Instant.now());
    }

    @Test
    @DisplayName("POST /challans returns 201 with the totals")
    void create_returnsCreated() throws Exception {
        UUID challanId = UUID.randomUUID();
        TdsChallanResponse created = response(challanId, "0.00", AllocationStatus.UNALLOCATED);
        when(challanService.create(any(TdsChallanRequest.class))).thenReturn(created);

        TdsChallanRequest request = new TdsChallanRequest(
                "2026-2027",
                "Q1",
                "0210045",
                "12345",
                LocalDate.of(2026, 7, 5),
                new BigDecimal("50000"),
                BigDecimal.ZERO,
                new BigDecimal("2000"),
                new BigDecimal("150"),
                BigDecimal.ZERO,
                "200",
                "Q1 TDS");

        mvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.id").value(challanId.toString()))
                .andExpect(jsonPath("$.data.totalAmount").value(52150.00))
                .andExpect(jsonPath("$.data.allocatableAmount").value(52000.00))
                .andExpect(jsonPath("$.data.allocationStatus").value("UNALLOCATED"));

        ArgumentCaptor<TdsChallanRequest> sent = ArgumentCaptor.forClass(TdsChallanRequest.class);
        verify(challanService).create(sent.capture());
        org.assertj.core.api.Assertions.assertThat(sent.getValue().tenderDate()).isEqualTo(LocalDate.of(2026, 7, 5));
    }

    @Test
    @DisplayName("A validation failure from the service is 400 VALIDATION_FAILED")
    void create_invalid_returns400() throws Exception {
        when(challanService.create(any(TdsChallanRequest.class)))
                .thenThrow(new TdsChallanValidationException("BSR code must be exactly 7 digits"));

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("BSR code must be exactly 7 digits"));
    }

    @Test
    @DisplayName("GET /challans?financialYear&quarter returns the list")
    void list_returnsOk() throws Exception {
        UUID challanId = UUID.randomUUID();
        List<TdsChallanResponse> list = List.of(response(challanId, "52000.00", AllocationStatus.FULL));
        when(challanService.list("2026-2027", "Q1")).thenReturn(list);

        mvc.perform(get(BASE).param("financialYear", "2026-2027").param("quarter", "Q1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(challanId.toString()))
                .andExpect(jsonPath("$.data[0].allocationStatus").value("FULL"));
    }

    @Test
    @DisplayName("GET /challans/{id} when not found returns 404")
    void get_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(challanService.findById(id)).thenReturn(Optional.empty());

        mvc.perform(get(BASE + "/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /challans/{id} returns 204")
    void delete_returnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(challanService).delete(id);

        mvc.perform(delete(BASE + "/{id}", id)).andExpect(status().isNoContent());

        verify(challanService).delete(id);
    }

    @Test
    @DisplayName("POST /challans/{id}/allocate passes tax, surcharge and cess per employee")
    void allocate_returnsOk() throws Exception {
        UUID challanId = UUID.randomUUID();
        UUID payRunId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        ChallanAllocationRequest request = new ChallanAllocationRequest(
                payRunId,
                List.of(new ChallanAllocationRequest.EmployeeAllocationItem(
                        employeeId, new BigDecimal("10000.00"), BigDecimal.ZERO, new BigDecimal("400.00"))));
        TdsChallanResponse allocated = response(challanId, "10400.00", AllocationStatus.PARTIAL);
        when(challanService.allocate(eq(challanId), any(ChallanAllocationRequest.class)))
                .thenReturn(allocated);

        mvc.perform(post(BASE + "/{id}/allocate", challanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allocatedAmount").value(10400.00))
                .andExpect(jsonPath("$.data.unallocatedAmount").value(41600.00))
                .andExpect(jsonPath("$.data.allocationStatus").value("PARTIAL"));

        ArgumentCaptor<ChallanAllocationRequest> sent = ArgumentCaptor.forClass(ChallanAllocationRequest.class);
        verify(challanService).allocate(eq(challanId), sent.capture());
        org.assertj.core.api.Assertions.assertThat(sent.getValue().payRunId()).isEqualTo(payRunId);
        org.assertj.core.api.Assertions.assertThat(
                        sent.getValue().employeeAllocations().get(0).cessAmount())
                .isEqualByComparingTo("400");
    }

    @Test
    @DisplayName("GET /challans/{id}/allocations lists the rows")
    void listAllocations_returnsOk() throws Exception {
        UUID challanId = UUID.randomUUID();
        List<TdsChallanAllocationResponse> rows = List.of(new TdsChallanAllocationResponse(
                UUID.randomUUID(),
                challanId,
                UUID.randomUUID(),
                "2026-05",
                UUID.randomUUID(),
                "E001",
                "Asha Rao",
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                new BigDecimal("40.00"),
                new BigDecimal("1040.00")));
        when(challanService.listAllocations(challanId)).thenReturn(rows);

        mvc.perform(get(BASE + "/{id}/allocations", challanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].employeeNumber").value("E001"))
                .andExpect(jsonPath("$.data[0].total").value(1040.00));
    }

    @Test
    @DisplayName("GET /challans/{id}/allocation-candidates lists who still needs covering")
    void candidates_returnsOk() throws Exception {
        UUID challanId = UUID.randomUUID();
        List<AllocationCandidate> out = List.of(new AllocationCandidate(
                UUID.randomUUID(),
                "2026-05",
                "REGULAR",
                UUID.randomUUID(),
                "E001",
                "Asha Rao",
                new BigDecimal("900.00"),
                new BigDecimal("300.00"),
                new BigDecimal("200.00"),
                new BigDecimal("400.00")));
        when(challanService.candidates(challanId)).thenReturn(out);

        mvc.perform(get(BASE + "/{id}/allocation-candidates", challanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].employeeNumber").value("E001"))
                .andExpect(jsonPath("$.data[0].remaining").value(400.00));
    }

    @Test
    @DisplayName("GET /challans/returns?financialYear lists the filed quarters")
    void returns_returnsOk() throws Exception {
        List<TdsReturnResponse> filed = List.of(new TdsReturnResponse(
                "2026-2027", "Q1", "123456789012345", LocalDate.of(2026, 7, 25), Instant.now(), "admin"));
        when(challanService.returns("2026-2027")).thenReturn(filed);

        mvc.perform(get(BASE + "/returns").param("financialYear", "2026-2027"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quarter").value("Q1"))
                .andExpect(jsonPath("$.data[0].tokenNumber").value("123456789012345"));
    }

    @Test
    @DisplayName("POST /challans/returns/{fy}/{q} marks the quarter filed: 201")
    void markFiled_returnsCreated() throws Exception {
        TdsReturnResponse saved = new TdsReturnResponse(
                "2026-2027", "Q1", "123456789012345", LocalDate.of(2026, 7, 25), Instant.now(), "admin");
        when(challanService.markFiled(eq("2026-2027"), eq("Q1"), any(TdsReturnRequest.class)))
                .thenReturn(saved);

        mvc.perform(post(BASE + "/returns/{fy}/{q}", "2026-2027", "Q1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenNumber\":\"123456789012345\",\"filedOn\":\"2026-07-25\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tokenNumber").value("123456789012345"));

        ArgumentCaptor<TdsReturnRequest> sent = ArgumentCaptor.forClass(TdsReturnRequest.class);
        verify(challanService).markFiled(eq("2026-2027"), eq("Q1"), sent.capture());
        org.assertj.core.api.Assertions.assertThat(sent.getValue().filedOn()).isEqualTo(LocalDate.of(2026, 7, 25));
    }

    @Test
    @DisplayName("POST /challans/returns/{fy}/{q} with a bad token is 400")
    void markFiled_badToken_returns400() throws Exception {
        when(challanService.markFiled(eq("2026-2027"), eq("Q1"), any(TdsReturnRequest.class)))
                .thenThrow(new TdsChallanValidationException(
                        "The token number is the 15 digits on the filing acknowledgement"));

        mvc.perform(post(BASE + "/returns/{fy}/{q}", "2026-2027", "Q1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenNumber\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("DELETE /challans/returns/{fy}/{q} reopens the quarter: 204")
    void reopen_returnsNoContent() throws Exception {
        mvc.perform(delete(BASE + "/returns/{fy}/{q}", "2026-2027", "Q1")).andExpect(status().isNoContent());

        verify(challanService).reopen("2026-2027", "Q1");
    }
}
