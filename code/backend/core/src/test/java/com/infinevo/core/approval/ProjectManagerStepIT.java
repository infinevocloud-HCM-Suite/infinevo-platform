package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-42.2, spec section 7 — {@code ProjectManagerStepIT}.
 *
 * <p>A {@code PROJECT_MANAGER} step goes to the manager of the project named on that step, and one project's rejection
 * leaves another project's instance alone. {@code hrms} owns the real resolver (W-42.3); here a stub maps a project id
 * to its manager, the way that one will.
 */
@SpringBootTest(classes = ApprovalTestApp.class)
@Import(ProjectManagerStepIT.ProjectManagers.class)
class ProjectManagerStepIT extends AbstractIntegrationTest {

    private static final UUID TENANT_A = ApprovalTestSchema.TENANT_A;

    /** Project id (as the step's item reference) to the employee who manages it. */
    static final Map<String, UUID> MANAGER_OF_PROJECT = new ConcurrentHashMap<>();

    @TestConfiguration
    static class ProjectManagers {

        @Bean
        ApproverResolver stubProjectManagerResolver() {
            return new ApproverResolver() {
                @Override
                public ApproverKind kind() {
                    return ApproverKind.PROJECT_MANAGER;
                }

                @Override
                public Optional<UUID> resolve(UUID tenantId, UUID employeeId, String contextRef) {
                    return Optional.ofNullable(contextRef).map(MANAGER_OF_PROJECT::get);
                }
            };
        }

        @Bean
        ApprovalOutcomeHandler stubTimesheetOutcomeHandler() {
            return new ApprovalOutcomeHandler() {
                @Override
                public ApprovalFlowType flowType() {
                    return ApprovalFlowType.TIMESHEET;
                }

                @Override
                public void onApproved(UUID instanceId, List<StepDecision> decisions) {}

                @Override
                public void onRejected(UUID instanceId, List<StepDecision> decisions) {}
            };
        }
    }

    @Autowired
    private ApprovalDefinitionService definitionService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalStepRepository stepRepository;

    @Autowired
    private ApprovalInstanceRepository instanceRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    @MockitoBean
    private EmployeeService employeeService;

    private TransactionTemplate tx;
    private UUID requesterId;
    private UUID manager1;
    private UUID manager2;
    private final UUID projectA = UUID.randomUUID();
    private final UUID projectB = UUID.randomUUID();

    @BeforeAll
    static void setupSchema() throws Exception {
        ApprovalTestSchema.apply();
    }

    @BeforeEach
    void setUp() throws Exception {
        tx = new TransactionTemplate(txManager);
        ApprovalTestSchema.seedTenants();
        ApprovalTestSchema.clearAll();
        TenantContext.set(TENANT_A);

        requesterId = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-201", "Requester", "req201@infinevo.test");
        manager1 = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-202", "Manager One", "m1-202@infinevo.test");
        manager2 = ApprovalTestSchema.insertEmployee(TENANT_A, "EMP-203", "Manager Two", "m2-203@infinevo.test");
        MANAGER_OF_PROJECT.clear();
        MANAGER_OF_PROJECT.put(projectA.toString(), manager1);
        MANAGER_OF_PROJECT.put(projectB.toString(), manager2);

        definitionService.saveDefinition(
                TENANT_A,
                ApprovalFlowType.TIMESHEET,
                new ApprovalDefinitionRequest(
                        StepOrdering.SEQUENTIAL,
                        CommentScope.PER_STEP,
                        LocalDate.of(2024, 1, 1),
                        List.of(new ApprovalStepDefinition(ApproverKind.PROJECT_MANAGER, null, 3, true))));
    }

    @AfterEach
    void tearDown() throws Exception {
        TenantContext.clear();
        MANAGER_OF_PROJECT.clear();
        // Shared database: instances left behind block the employee tests' deletes through their foreign keys.
        ApprovalTestSchema.clearDefinitions();
    }

    @AfterAll
    static void clearApprovalRows() throws Exception {
        ApprovalTestSchema.clearAll();
    }

    private UUID startFor(UUID subjectId, UUID projectId) {
        return approvalService.start(
                ApprovalFlowType.TIMESHEET,
                new SubjectRef("hrms.timesheet_project_entry", subjectId),
                requesterId,
                List.of(projectId.toString()));
    }

    private ApprovalStep onlyStepOf(UUID instanceId) {
        List<ApprovalStep> steps = tx.execute(
                status -> stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(TENANT_A, instanceId));
        assertThat(steps).hasSize(1);
        return steps.get(0);
    }

    private InstanceStatus statusOf(UUID instanceId) {
        return tx.execute(status -> instanceRepository.findById(instanceId).orElseThrow())
                .getStatus();
    }

    private void actAs(UUID employeeId) {
        EmployeeResponse who = mock(EmployeeResponse.class);
        when(who.id()).thenReturn(employeeId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(who));
    }

    @Test
    @DisplayName("Each project's step is assigned to that project's manager, and carries the project as its item")
    void stepGoesToTheManagerOfItsProject() {
        UUID instanceA = startFor(UUID.randomUUID(), projectA);
        UUID instanceB = startFor(UUID.randomUUID(), projectB);

        ApprovalStep stepA = onlyStepOf(instanceA);
        ApprovalStep stepB = onlyStepOf(instanceB);

        assertThat(stepA.getItemRef()).isEqualTo(projectA.toString());
        assertThat(stepA.getAssigneeEmployeeId()).isEqualTo(manager1);
        assertThat(stepB.getItemRef()).isEqualTo(projectB.toString());
        assertThat(stepB.getAssigneeEmployeeId()).isEqualTo(manager2);
    }

    @Test
    @DisplayName("A project with no manager leaves its step unassigned, not given to someone else's manager")
    void projectWithoutManagerIsUnassigned() {
        MANAGER_OF_PROJECT.remove(projectB.toString());

        ApprovalStep step = onlyStepOf(startFor(UUID.randomUUID(), projectB));

        assertThat(step.getAssigneeEmployeeId()).isNull();
    }

    @Test
    @DisplayName(
            "One project's manager rejecting ends that instance only; the other's is pending and its manager approves")
    void rejectingOneProjectLeavesTheOtherPending() {
        UUID instanceA = startFor(UUID.randomUUID(), projectA);
        UUID subjectB = UUID.randomUUID();
        UUID instanceB = startFor(subjectB, projectB);

        actAs(manager2);
        approvalService.decide(
                onlyStepOf(instanceB).getId(), new ApprovalDecideRequest(ApprovalDecision.REJECTED, "Wrong project"));

        assertThat(statusOf(instanceB)).isEqualTo(InstanceStatus.REJECTED);
        assertThat(statusOf(instanceA)).isEqualTo(InstanceStatus.PENDING);

        actAs(manager1);
        approvalService.decide(
                onlyStepOf(instanceA).getId(), new ApprovalDecideRequest(ApprovalDecision.APPROVED, "OK"));

        assertThat(statusOf(instanceA)).isEqualTo(InstanceStatus.APPROVED);
        assertThat(statusOf(instanceB)).isEqualTo(InstanceStatus.REJECTED);

        // After a rejection the employee edits and resubmits: a new instance on the same subject is accepted.
        UUID resubmitted = startFor(subjectB, projectB);
        assertThat(resubmitted).isNotEqualTo(instanceB);
        assertThat(statusOf(resubmitted)).isEqualTo(InstanceStatus.PENDING);
        assertThat(onlyStepOf(resubmitted).getAssigneeEmployeeId()).isEqualTo(manager2);
    }

    @Test
    @DisplayName("A manager of another project cannot decide this project's step")
    void otherProjectsManagerCannotDecide() {
        UUID instanceA = startFor(UUID.randomUUID(), projectA);

        actAs(manager2);
        assertThatThrownBy(() -> approvalService.decide(
                        onlyStepOf(instanceA).getId(), new ApprovalDecideRequest(ApprovalDecision.APPROVED, "Mine?")))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(statusOf(instanceA)).isEqualTo(InstanceStatus.PENDING);
    }
}
