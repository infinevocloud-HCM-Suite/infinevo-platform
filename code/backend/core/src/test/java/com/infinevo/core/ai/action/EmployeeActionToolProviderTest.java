package com.infinevo.core.ai.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiAccessGate;
import com.infinevo.core.ai.AiActionIntent;
import com.infinevo.core.ai.AiActionIntentRepository;
import com.infinevo.core.ai.AiChatTurn;
import com.infinevo.core.ai.AiChatTurns;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.Department;
import com.infinevo.core.org.DepartmentRepository;
import com.infinevo.core.org.DesignationRepository;
import com.infinevo.core.org.WorkLocationRepository;
import com.infinevo.shared.audit.AuditWriter;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

/**
 * W-114.20 spec, Tests: {@code core.propose_employee_create} with a real {@link AiActionServiceImpl} over an in-memory
 * intent store. The employee service is a mock, so "nothing written" is "create is never called".
 */
class EmployeeActionToolProviderTest {

    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-27T09:00:00Z");
    private final List<AiActionIntent> stored = new ArrayList<>();
    private final ObjectMapper json = new ObjectMapper();

    private EmployeeService employeeService;
    private DepartmentRepository departments;
    private AiTool tool;
    private AiToolContext ctx;
    private AiChatTurn turn;
    private Department finance;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        employeeService = mock(EmployeeService.class);
        departments = mock(DepartmentRepository.class);
        DesignationRepository designations = mock(DesignationRepository.class);
        WorkLocationRepository locations = mock(WorkLocationRepository.class);
        finance = department("FIN", "Finance");
        Department hr = department("HR", "Human Resources");
        when(departments.findByTenantIdAndActiveTrueOrderByCodeAsc(tenantId)).thenReturn(List.of(finance, hr));
        when(departments.findByIdAndTenantId(finance.getId(), tenantId)).thenReturn(Optional.of(finance));

        AiActionIntentRepository intents = mock(AiActionIntentRepository.class);
        when(intents.save(any(AiActionIntent.class))).thenAnswer(inv -> {
            AiActionIntent i = inv.getArgument(0);
            if (i.getId() == null) {
                ReflectionTestUtils.setField(i, "id", UUID.randomUUID());
                stored.add(i);
            }
            return i;
        });
        AiActionServiceImpl actions = new AiActionServiceImpl(
                intents,
                List.of(new EmployeeCreateExecutor(employeeService, departments, designations, locations)),
                mock(AiAccessGate.class),
                mock(PermissionService.class),
                mock(EntitlementService.class),
                mock(EmployeeService.class),
                mock(AuditWriter.class),
                TransactionOperations.withoutTransaction(),
                Clock.fixed(now, ZoneOffset.UTC));
        tool = new EmployeeActionToolProvider(
                        employeeService,
                        departments,
                        designations,
                        locations,
                        actions,
                        TransactionOperations.withoutTransaction())
                .tools()
                .get(0);
        ctx = new AiToolContext(tenantId, userId, null);
        turn = AiChatTurns.open(tenantId, userId, UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        turn.close();
        TenantContext.clear();
    }

    private Department department(String code, String name) {
        Department d = mock(Department.class);
        UUID id = UUID.randomUUID();
        when(d.getId()).thenReturn(id);
        when(d.getCode()).thenReturn(code);
        when(d.getName()).thenReturn(name);
        return d;
    }

    private AiToolResult run(String input) throws Exception {
        return tool.execute(ctx, json.readTree(input));
    }

    private static final String PRIYA = "\"employeeNumber\":\"E-207\",\"firstName\":\"Priya\",\"lastName\":\"Nair\","
            + "\"dateOfJoining\":\"2026-12-01\",\"workEmail\":\"priya@acme.in\"";

    @Test
    @DisplayName("the tool: needs core.employee.create, no employee record; the three required fields")
    void declaration() {
        assertThat(tool.name()).isEqualTo("core.propose_employee_create");
        assertThat(tool.requiredAction()).isEqualTo("core.employee.create");
        assertThat(tool.needsEmployee()).isFalse();
        assertThat(tool.inputSchema().path("required").toString())
                .contains("employeeNumber")
                .contains("firstName")
                .contains("dateOfJoining");
    }

    @Test
    @DisplayName("a card with the resolved department by name; the payload holds its id, the model's answer none")
    void propose_resolvesDepartment() throws Exception {
        AiToolResult result = run("{" + PRIYA + ",\"department\":\"  finance \"}");

        assertThat(result.ok()).isTrue();
        assertThat(UUID_TEXT.matcher(result.data().toString()).find()).isFalse();
        assertThat(result.data().toString()).contains("Finance").contains("Not set here");
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getPayload().path("departmentId").asText())
                .isEqualTo(finance.getId().toString());
        ArgumentCaptor<EmployeeRequest> checked = ArgumentCaptor.forClass(EmployeeRequest.class);
        verify(employeeService).checkCreate(checked.capture());
        assertThat(checked.getValue().departmentId()).isEqualTo(finance.getId());
        verify(employeeService, never()).create(any(EmployeeRequest.class));
    }

    @Test
    @DisplayName("an unknown department lists the valid names; no intent")
    void unknownDepartment() throws Exception {
        AiToolResult result = run("{" + PRIYA + ",\"department\":\"Finanse\"}");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("Finanse").contains("Finance").contains("Human Resources");
        assertThat(stored).isEmpty();
    }

    @Test
    @DisplayName("two departments with the same name: the helper is told to ask which")
    void twoDepartmentsSameName() throws Exception {
        Department other = department("FIN2", "Finance");
        when(departments.findByTenantIdAndActiveTrueOrderByCodeAsc(tenantId)).thenReturn(List.of(finance, other));

        AiToolResult result = run("{" + PRIYA + ",\"department\":\"Finance\"}");

        assertThat(result.error())
                .contains("More than one")
                .contains("FIN")
                .contains("FIN2")
                .contains("ask HR");
        assertThat(stored).isEmpty();
    }

    @Test
    @DisplayName("a missing required field is named; no intent")
    void missingRequired() throws Exception {
        AiToolResult result = run("{\"employeeNumber\":\"E-207\",\"firstName\":\"Priya\"}");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("dateOfJoining");
        assertThat(stored).isEmpty();
    }

    @Test
    @DisplayName("a duplicate number: the service's message, no intent")
    void duplicateNumber() throws Exception {
        doThrow(new EmployeeService.DuplicateEmployeeNumberException("E-207"))
                .when(employeeService)
                .checkCreate(any());

        AiToolResult result = run("{" + PRIYA + "}");

        assertThat(result.error()).contains("E-207").contains("already in use");
        assertThat(stored).isEmpty();
    }

    @Test
    @DisplayName("mobile, status, portal, salary or PAN in the call: refused with where to set them; never stored")
    void neverFromChat() throws Exception {
        for (String extra : List.of(
                "\"mobile\":\"[hidden]\"",
                "\"status\":\"TERMINATED\"",
                "\"portalEnabled\":true",
                "\"salary\":\"60000\"",
                "\"pan\":\"[PAN]\"")) {
            AiToolResult result = run("{" + PRIYA + "," + extra + "}");
            assertThat(result.ok()).isFalse();
            assertThat(result.error()).isEqualTo(EmployeeActionToolProvider.NOT_HERE);
        }
        AiToolResult manager = run("{" + PRIYA + ",\"reportingManager\":\"Ravi\"}");
        assertThat(manager.error()).isEqualTo(EmployeeActionToolProvider.MANAGER_NOT_HERE);
        assertThat(stored).isEmpty();
        verify(employeeService, never()).checkCreate(any());
    }

    @Test
    @DisplayName("the stored payload holds only fields chat may set")
    void payloadShape() throws Exception {
        run("{" + PRIYA + "}");

        JsonNode payload = stored.get(0).getPayload();
        for (String forbidden : List.of(
                "mobile", "status", "portalEnabled", "terminationDate", "terminationReason", "terminationRemarks")) {
            assertThat(payload.has(forbidden)).as(forbidden).isFalse();
        }
        EmployeeRequest rebuilt = EmployeeCreateExecutor.request(payload);
        assertThat(rebuilt.mobile()).isNull();
        assertThat(rebuilt.status()).isNull();
        assertThat(rebuilt.portalEnabled()).isNull();
        assertThat(rebuilt.employeeNumber()).isEqualTo("E-207");
    }
}
