package com.infinevo.core.ai.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.onboarding.EmployeeOnboardingTaskRepository;
import com.infinevo.core.onboarding.OnboardingProgressResponse;
import com.infinevo.core.onboarding.OnboardingService;
import com.infinevo.core.onboarding.OnboardingTaskTemplate;
import com.infinevo.core.onboarding.OnboardingTaskTemplateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/** W-114.29 step 4, Tests: {@code core.propose_onboarding_assign} and {@link OnboardingAssignExecutor}. */
class OnboardingAssignToolProviderTest {

    private final ObjectMapper json = new ObjectMapper();
    private final List<OnboardingTaskTemplate> checklist = new ArrayList<>();
    private AdminStepsFixture f;
    private HrStepsPeople people;
    private EmployeeOnboardingTaskRepository tasks;
    private OnboardingService service;
    private OnboardingAssignExecutor exec;
    private AiTool tool;
    private UUID asha;

    @BeforeEach
    void setUp() {
        OnboardingTaskTemplateRepository templates = mock(OnboardingTaskTemplateRepository.class);
        when(templates.findByTenantIdAndIsActiveTrueOrderBySortOrderAscCreatedAtAsc(any()))
                .thenAnswer(inv -> List.copyOf(checklist));
        tasks = mock(EmployeeOnboardingTaskRepository.class);
        service = mock(OnboardingService.class);
        people = new HrStepsPeople();
        exec = new OnboardingAssignExecutor(service, templates, tasks, people.repository);
        f = new AdminStepsFixture(List.of(exec));
        asha = people.add("E-001", "Asha", "Rao");
        checklist.add(template("Submit PAN"));
        checklist.add(template("Laptop handover"));
        tool = new OnboardingAssignToolProvider(
                        people.query, templates, tasks, f.actions, TransactionOperations.withoutTransaction())
                .tools()
                .get(0);
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    private static OnboardingTaskTemplate template(String title) {
        OnboardingTaskTemplate t = mock(OnboardingTaskTemplate.class);
        when(t.getTitle()).thenReturn(title);
        return t;
    }

    private AiToolResult run(String input) throws Exception {
        return tool.execute(f.ctx, json.readTree(input));
    }

    @Test
    @DisplayName("the tool and the action need core.onboarding.manage, what the assign endpoint needs")
    void declaration() {
        assertThat(tool.name()).isEqualTo("core.propose_onboarding_assign");
        assertThat(tool.requiredAction()).isEqualTo("core.onboarding.manage");
        assertThat(exec.requiredAction()).isEqualTo("core.onboarding.manage");
        assertThat(exec.actionCode()).isEqualTo("core.onboarding.assign");
        assertThat(exec.requiresEmployeeProfile()).isFalse();
    }

    @Test
    @DisplayName("a proposal writes nothing; the payload is the employee number alone; the card lists the tasks; no id")
    void propose() throws Exception {
        AiToolResult result = run("{\"employee\":\"Asha Rao\"}");

        assertThat(result.ok()).as(String.valueOf(result.error())).isTrue();
        assertThat(AdminStepsFixture.UUID_TEXT.matcher(result.data().toString()).find())
                .isFalse();
        assertThat(result.data().toString())
                .contains("Asha Rao (E-001)")
                .contains("Submit PAN, Laptop handover")
                .contains("2 tasks");
        assertThat(f.stored.get(0).getPayload().toString()).isEqualTo("{\"employeeNumber\":\"E-001\"}");
        verify(service, never()).assignTasksToEmployee(any(), any(), any());
    }

    @Test
    @DisplayName("an unknown person, one who has a checklist, or an empty checklist: no card; one card per message")
    void refusals() throws Exception {
        assertThat(run("{\"employee\":\"E-404\"}").error()).isEqualTo(NamedEmployee.NO_EMPLOYEE);
        when(tasks.countByTenantIdAndEmployeeId(f.tenantId, asha)).thenReturn(3L);
        assertThat(run("{\"employee\":\"E-001\"}").error()).contains("already has an onboarding checklist");
        when(tasks.countByTenantIdAndEmployeeId(f.tenantId, asha)).thenReturn(0L);
        List<OnboardingTaskTemplate> saved = List.copyOf(checklist);
        checklist.clear();
        assertThat(run("{\"employee\":\"E-001\"}").error()).isEqualTo(OnboardingAssignExecutor.NO_CHECKLIST);
        assertThat(f.stored).isEmpty();
        checklist.addAll(saved);

        assertThat(run("{\"employee\":\"E-001\"}").ok()).isTrue();
        assertThat(run("{\"employee\":\"E-001\"}").error()).contains("One step at a time");
        assertThat(f.stored).hasSize(1);
    }

    @Test
    @DisplayName("Confirm calls assignTasksToEmployee with the caller as actor; a checklist made in between FAILS")
    void execute() throws Exception {
        var payload = json.readTree("{\"employeeNumber\":\"E-001\"}");
        OnboardingProgressResponse progress = new OnboardingProgressResponse(asha, 2, 0, 2, 0, List.of());
        when(service.assignTasksToEmployee(eq(f.tenantId), eq(asha), anyString()))
                .thenReturn(progress);

        assertThat(exec.execute(f.ctx, payload).path("tasks").asInt()).isEqualTo(2);
        verify(service).assignTasksToEmployee(f.tenantId, asha, f.userId.toString());

        when(tasks.countByTenantIdAndEmployeeId(f.tenantId, asha)).thenReturn(2L);
        assertThatThrownBy(() -> exec.execute(f.ctx, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(OnboardingAssignExecutor.ALREADY);
        when(tasks.countByTenantIdAndEmployeeId(f.tenantId, asha)).thenReturn(0L);

        doThrow(new NoSuchElementException("Employee not found"))
                .when(service)
                .assignTasksToEmployee(eq(f.tenantId), eq(asha), anyString());
        assertThatThrownBy(() -> exec.execute(f.ctx, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(NamedEmployee.GONE);

        checklist.clear();
        assertThatThrownBy(() -> exec.execute(f.ctx, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(OnboardingAssignExecutor.NO_CHECKLIST);

        people.remove("E-001");
        assertThatThrownBy(() -> exec.execute(f.ctx, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(NamedEmployee.GONE);
    }
}
