package com.infinevo.core.wfh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.attendance.AttendanceRepository;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.leave.LeaveRequestRepository;
import com.infinevo.core.leave.WorkingDayCalculator;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Ownership of a WFH request on withdraw, cancel and read-by-id: an employee acts on their own
 * request only, unless they hold the broad action. Same rule as
 * {@code LeaveRequestController.withdraw/cancel/getRequest}.
 *
 * <p>The controller runs over the real {@link WfhRequestServiceImpl} with in-memory repositories,
 * so "B's request is still PENDING" is the stored state, not a mock's say-so. {@code @RequiresAction}
 * is not exercised here (standalone MockMvc): each test grants the actions the annotation admits.
 */
class WfhRequestControllerTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeA = UUID.randomUUID();
    private final UUID employeeB = UUID.randomUUID();

    private final Map<UUID, WfhRequest> store = new HashMap<>();
    private final Set<String> granted = new HashSet<>();

    private WfhRequestRepository requestRepository;
    private EmployeeService employeeService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);

        requestRepository = mock(WfhRequestRepository.class);
        when(requestRepository.findByTenantIdAndId(any(), any())).thenAnswer(inv -> {
            WfhRequest r = store.get(inv.<UUID>getArgument(1));
            return r != null && r.getTenantId().equals(inv.getArgument(0)) ? Optional.of(r) : Optional.empty();
        });
        when(requestRepository.save(any(WfhRequest.class))).thenAnswer(inv -> {
            WfhRequest r = inv.getArgument(0);
            store.put(r.getId(), r);
            return r;
        });

        WfhRequestServiceImpl service = new WfhRequestServiceImpl(
                requestRepository,
                mock(LeaveRequestRepository.class),
                mock(WfhBalanceService.class),
                mock(WfhPolicyService.class),
                mock(WfhConsumptionRepository.class),
                mock(EmployeeRepository.class),
                mock(WorkingDayCalculator.class),
                mock(AttendanceRepository.class),
                mock(ApprovalService.class));

        employeeService = mock(EmployeeService.class);
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.holds(anyString())).thenAnswer(inv -> granted.contains(inv.<String>getArgument(0)));

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = MockMvcBuilders.standaloneSetup(new WfhRequestController(service, employeeService, permissionService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ---- withdraw ----

    @Test
    @DisplayName("withdraw: employee A withdraws own PENDING request - 200, WITHDRAWN")
    void withdrawOwn() throws Exception {
        callerIs(employeeA, "core.wfh.apply");
        UUID id = request(employeeA, WfhRequestStatus.PENDING, LocalDate.now().plusDays(3));

        mvc.perform(post("/api/v1/wfh-requests/" + id + "/withdraw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        assertThat(store.get(id).getStatus()).isEqualTo(WfhRequestStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("withdraw: employee A withdraws B's request - 403, B's request still PENDING")
    void withdrawOthersRefused() throws Exception {
        callerIs(employeeA, "core.wfh.apply");
        UUID id = request(employeeB, WfhRequestStatus.PENDING, LocalDate.now().plusDays(3));

        mvc.perform(post("/api/v1/wfh-requests/" + id + "/withdraw"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot withdraw another employee's WFH request"));
        assertThat(store.get(id).getStatus()).isEqualTo(WfhRequestStatus.PENDING);
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("withdraw: holder of core.wfh.manage withdraws B's request - 200")
    void withdrawOthersWithManage() throws Exception {
        callerIs(employeeA, "core.wfh.apply", "core.wfh.manage");
        UUID id = request(employeeB, WfhRequestStatus.PENDING, LocalDate.now().plusDays(3));

        mvc.perform(post("/api/v1/wfh-requests/" + id + "/withdraw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        assertThat(store.get(id).getStatus()).isEqualTo(WfhRequestStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("withdraw: core.wfh.apply but no employee record - 403, request unchanged")
    void withdrawWithoutEmployeeRecordRefused() throws Exception {
        callerIs(null, "core.wfh.apply");
        UUID id = request(employeeB, WfhRequestStatus.PENDING, LocalDate.now().plusDays(3));

        mvc.perform(post("/api/v1/wfh-requests/" + id + "/withdraw")).andExpect(status().isForbidden());
        assertThat(store.get(id).getStatus()).isEqualTo(WfhRequestStatus.PENDING);
    }

    @Test
    @DisplayName("withdraw: unknown id is still 404")
    void withdrawUnknownIs404() throws Exception {
        callerIs(employeeA, "core.wfh.apply");

        mvc.perform(post("/api/v1/wfh-requests/" + UUID.randomUUID() + "/withdraw"))
                .andExpect(status().isNotFound());
    }

    // ---- cancel ----

    @Test
    @DisplayName("cancel: employee A cancels own APPROVED future request - 200, CANCELLED")
    void cancelOwn() throws Exception {
        callerIs(employeeA, "core.wfh.apply");
        UUID id = request(employeeA, WfhRequestStatus.APPROVED, LocalDate.now().plusDays(5));

        mvc.perform(post("/api/v1/wfh-requests/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(store.get(id).getStatus()).isEqualTo(WfhRequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel: employee A cancels B's APPROVED future request - 403, still APPROVED")
    void cancelOthersRefused() throws Exception {
        callerIs(employeeA, "core.wfh.apply");
        UUID id = request(employeeB, WfhRequestStatus.APPROVED, LocalDate.now().plusDays(5));

        mvc.perform(post("/api/v1/wfh-requests/" + id + "/cancel"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot cancel another employee's WFH request"));
        assertThat(store.get(id).getStatus()).isEqualTo(WfhRequestStatus.APPROVED);
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancel: holder of core.wfh.manage cancels B's request - 200")
    void cancelOthersWithManage() throws Exception {
        callerIs(employeeA, "core.wfh.apply", "core.wfh.manage");
        UUID id = request(employeeB, WfhRequestStatus.APPROVED, LocalDate.now().plusDays(5));

        mvc.perform(post("/api/v1/wfh-requests/" + id + "/cancel")).andExpect(status().isOk());
        assertThat(store.get(id).getStatus()).isEqualTo(WfhRequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel: core.wfh.apply but no employee record - 403, request unchanged")
    void cancelWithoutEmployeeRecordRefused() throws Exception {
        callerIs(null, "core.wfh.apply");
        UUID id = request(employeeB, WfhRequestStatus.APPROVED, LocalDate.now().plusDays(5));

        mvc.perform(post("/api/v1/wfh-requests/" + id + "/cancel")).andExpect(status().isForbidden());
        assertThat(store.get(id).getStatus()).isEqualTo(WfhRequestStatus.APPROVED);
    }

    // ---- read by id ----

    @Test
    @DisplayName("GET /{id} with only read_own: own request - 200")
    void readOwnWithReadOwn() throws Exception {
        callerIs(employeeA, "core.wfh.read_own");
        UUID id = request(employeeA, WfhRequestStatus.PENDING, LocalDate.now().plusDays(3));

        mvc.perform(get("/api/v1/wfh-requests/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @DisplayName("GET /{id} with only read_own: another employee's request - 403")
    void readOthersWithReadOwnRefused() throws Exception {
        callerIs(employeeA, "core.wfh.read_own");
        UUID id = request(employeeB, WfhRequestStatus.PENDING, LocalDate.now().plusDays(3));

        mvc.perform(get("/api/v1/wfh-requests/" + id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot read another employee's WFH request"));
    }

    @Test
    @DisplayName("GET /{id} with only read_own and no employee record - 403")
    void readWithoutEmployeeRecordRefused() throws Exception {
        callerIs(null, "core.wfh.read_own");
        UUID id = request(employeeB, WfhRequestStatus.PENDING, LocalDate.now().plusDays(3));

        mvc.perform(get("/api/v1/wfh-requests/" + id)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /{id} with core.wfh.read: another employee's request - 200")
    void readOthersWithRead() throws Exception {
        callerIs(employeeA, "core.wfh.read");
        UUID id = request(employeeB, WfhRequestStatus.PENDING, LocalDate.now().plusDays(3));

        mvc.perform(get("/api/v1/wfh-requests/" + id)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /{id} with core.wfh.read_team: another employee's request - 200 (unchanged)")
    void readOthersWithReadTeam() throws Exception {
        callerIs(employeeA, "core.wfh.read_team");
        UUID id = request(employeeB, WfhRequestStatus.PENDING, LocalDate.now().plusDays(3));

        mvc.perform(get("/api/v1/wfh-requests/" + id)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /{id} unknown id is still 404")
    void readUnknownIs404() throws Exception {
        callerIs(employeeA, "core.wfh.read_own");

        mvc.perform(get("/api/v1/wfh-requests/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    // ---- helpers ----

    private void callerIs(UUID employeeId, String... actions) {
        granted.addAll(Set.of(actions));
        when(employeeService.currentEmployee())
                .thenReturn(employeeId == null ? Optional.empty() : Optional.of(employee(employeeId)));
    }

    private UUID request(UUID employeeId, WfhRequestStatus status, LocalDate from) {
        WfhRequest r = new WfhRequest();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setEmployeeId(employeeId);
        r.setStatus(status);
        r.setFromDate(from);
        r.setToDate(from);
        r.setWorkingDays(BigDecimal.ONE);
        store.put(r.getId(), r);
        return r.getId();
    }

    private EmployeeResponse employee(UUID id) {
        return new EmployeeResponse(
                id,
                tenantId,
                "E-" + id.toString().substring(0, 4),
                "First",
                null,
                "Last",
                null,
                LocalDate.of(2024, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                null,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
