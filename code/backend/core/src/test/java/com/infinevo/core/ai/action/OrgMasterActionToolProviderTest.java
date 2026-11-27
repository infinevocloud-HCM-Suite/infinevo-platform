package com.infinevo.core.ai.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.ai.action.OrgMasterActionToolProvider.Existing;
import com.infinevo.core.org.DepartmentRequest;
import com.infinevo.core.org.DepartmentResponse;
import com.infinevo.core.org.DepartmentService;
import com.infinevo.core.org.DesignationService;
import com.infinevo.core.org.OrgMasterService;
import com.infinevo.core.org.WorkLocationRequest;
import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

/** W-114.28 steps 1-3, Tests: the department, designation and work location tools and executors. */
class OrgMasterActionToolProviderTest {

    private final ObjectMapper json = new ObjectMapper();
    private final List<Existing> departments = new ArrayList<>();
    private DepartmentService departmentService;
    private WorkLocationService locationService;
    private AdminStepsFixture f;
    private Map<String, AiTool> tools;

    @BeforeEach
    void setUp() {
        departmentService = mock(DepartmentService.class);
        locationService = mock(WorkLocationService.class);
        DesignationService designationService = mock(DesignationService.class);
        f = new AdminStepsFixture(List.of(
                new OrgMasterExecutors.Department(departmentService),
                new OrgMasterExecutors.Designation(designationService),
                new OrgMasterExecutors.Location(locationService)));
        departments.add(new Existing("FIN", "Finance"));
        OrgMasterActionToolProvider provider = new OrgMasterActionToolProvider(
                () -> departments.stream(),
                () -> List.<Existing>of().stream(),
                () -> List.<Existing>of().stream(),
                f.actions,
                TransactionOperations.withoutTransaction());
        tools = new java.util.HashMap<>();
        provider.tools().forEach(t -> tools.put(t.name(), t));
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    private AiToolResult run(String tool, String input) throws Exception {
        return tools.get(tool).execute(f.ctx, json.readTree(input));
    }

    @Test
    @DisplayName("three tools, each with the screens' action core.org.manage; executors need no employee record")
    void declarations() {
        assertThat(tools.keySet())
                .containsExactlyInAnyOrder(
                        "core.propose_department", "core.propose_designation", "core.propose_work_location");
        tools.values().forEach(t -> assertThat(t.requiredAction()).isEqualTo("core.org.manage"));
        OrgMasterExecutors.Department exec = new OrgMasterExecutors.Department(departmentService);
        assertThat(exec.requiredAction()).isEqualTo("core.org.manage");
        assertThat(exec.requiresEmployeeProfile()).isFalse();
        assertThat(exec.actionCode()).isEqualTo("core.org.department.create");
    }

    @Test
    @DisplayName("a proposal writes nothing; the payload holds only code and name; no id reaches the model")
    void propose_writesNothing() throws Exception {
        AiToolResult result = run("core.propose_department", "{\"code\":\"ops\",\"name\":\"Operations\"}");

        assertThat(result.ok()).isTrue();
        assertThat(AdminStepsFixture.UUID_TEXT.matcher(result.data().toString()).find())
                .isFalse();
        assertThat(result.data().toString())
                .contains("Add department Operations")
                .contains("Confirm");
        assertThat(f.stored).hasSize(1);
        assertThat(f.stored.get(0).getPayload().toString()).isEqualTo("{\"code\":\"OPS\",\"name\":\"Operations\"}");
        verify(departmentService, never()).create(any());
    }

    @Test
    @DisplayName("a code or a name already in use, any case, is refused before a card")
    void duplicates() throws Exception {
        assertThat(run("core.propose_department", "{\"code\":\"fin\",\"name\":\"Money\"}")
                        .error())
                .contains("code FIN already exists".replace("FIN", "fin"));
        assertThat(run("core.propose_department", "{\"code\":\"MON\",\"name\":\" finance \"}")
                        .error())
                .contains("already exists");
        assertThat(f.stored).isEmpty();
    }

    @Test
    @DisplayName("code and name are asked for; an unknown field is refused")
    void inputs() throws Exception {
        assertThat(run("core.propose_designation", "{\"name\":\"Manager\"}").error())
                .contains("Ask the user");
        assertThat(run("core.propose_designation", "{\"code\":\" \",\"name\":\"Manager\"}")
                        .error())
                .contains("Ask the user");
        assertThat(run("core.propose_designation", "{\"code\":\"M\",\"name\":\"Manager\",\"id\":\"x\"}")
                        .ok())
                .isFalse();
        assertThat(f.stored).isEmpty();
    }

    @Test
    @DisplayName("one proposal per message")
    void oneAtATime() throws Exception {
        assertThat(run("core.propose_department", "{\"code\":\"A\",\"name\":\"Alpha\"}")
                        .ok())
                .isTrue();
        AiToolResult second = run("core.propose_designation", "{\"code\":\"B\",\"name\":\"Beta\"}");
        assertThat(second.ok()).isFalse();
        assertThat(second.error()).contains("One step at a time");
        f.nextMessage();
        assertThat(run("core.propose_designation", "{\"code\":\"B\",\"name\":\"Beta\"}")
                        .ok())
                .isTrue();
    }

    @Test
    @DisplayName("a work location carries the address fields the form takes, and none it does not")
    void location() throws Exception {
        AiToolResult result = run(
                "core.propose_work_location",
                "{\"code\":\"pun\",\"name\":\"Pune\",\"city\":\"Pune\",\"stateCode\":\"27\"}");
        assertThat(result.ok()).isTrue();
        assertThat(f.stored.get(0).getPayload().path("city").asText()).isEqualTo("Pune");
        assertThat(f.stored.get(0).getPayload().path("addressLine1").isNull()).isTrue();
        assertThat(run("core.propose_department", "{\"code\":\"X\",\"name\":\"X\",\"city\":\"Pune\"}")
                        .ok())
                .isFalse();

        WorkLocationResponse pun = location("PUN");
        when(locationService.create(any())).thenReturn(pun);
        new OrgMasterExecutors.Location(locationService)
                .execute(f.ctx, f.stored.get(0).getPayload());
        ArgumentCaptor<WorkLocationRequest> sent = ArgumentCaptor.forClass(WorkLocationRequest.class);
        verify(locationService).create(sent.capture());
        assertThat(sent.getValue().code()).isEqualTo("PUN");
        assertThat(sent.getValue().city()).isEqualTo("Pune");
        assertThat(sent.getValue().stateCode()).isEqualTo("27");
    }

    @Test
    @DisplayName("Confirm calls the screen's create; the service's refusals become FAILED reasons")
    void execute() throws Exception {
        OrgMasterExecutors.Department exec = new OrgMasterExecutors.Department(departmentService);
        var payload = json.readTree("{\"code\":\"OPS\",\"name\":\"Operations\"}");
        UUID id = UUID.randomUUID();
        when(departmentService.create(any()))
                .thenReturn(new DepartmentResponse(
                        id, f.tenantId, "OPS", "Operations", true, Instant.now(), Instant.now()));

        assertThat(exec.execute(f.ctx, payload).path("id").asText()).isEqualTo(id.toString());
        ArgumentCaptor<DepartmentRequest> sent = ArgumentCaptor.forClass(DepartmentRequest.class);
        verify(departmentService).create(sent.capture());
        assertThat(sent.getValue().code()).isEqualTo("OPS");

        doThrow(new OrgMasterService.DuplicateCodeException("Department", "OPS"))
                .when(departmentService)
                .create(any());
        assertThatThrownBy(() -> exec.execute(f.ctx, payload)).isInstanceOf(IllegalStateException.class);

        doThrow(new OrgMasterService.ValidationException(Map.of("name", "name is too long")))
                .when(departmentService)
                .create(any());
        assertThatThrownBy(() -> exec.execute(f.ctx, payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name is too long");
    }

    private WorkLocationResponse location(String code) {
        WorkLocationResponse r = mock(WorkLocationResponse.class);
        when(r.id()).thenReturn(UUID.randomUUID());
        when(r.code()).thenReturn(code);
        return r;
    }
}
