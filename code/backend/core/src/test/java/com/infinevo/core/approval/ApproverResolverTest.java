package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.authz.Role;
import com.infinevo.core.authz.RoleRepository;
import com.infinevo.core.authz.UserRole;
import com.infinevo.core.authz.UserRoleRepository;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.ReportingLineService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * W-15.2, spec section 7 — {@code ApproverResolverTest}.
 *
 * <p>Proves that each approver kind resolves properly and an employee with no manager yields
 * an unassignable step rather than stalling.
 */
class ApproverResolverTest {

    private ReportingLineService reportingLineService;
    private RoleRepository roleRepository;
    private UserRoleRepository userRoleRepository;
    private EmployeeRepository employeeRepository;
    private CoreApproverResolver resolver;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID manager1Id = UUID.randomUUID();
    private final UUID manager2Id = UUID.randomUUID();
    private final UUID manager3Id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reportingLineService = mock(ReportingLineService.class);
        roleRepository = mock(RoleRepository.class);
        userRoleRepository = mock(UserRoleRepository.class);
        employeeRepository = mock(EmployeeRepository.class);

        resolver = new CoreApproverResolver(
                reportingLineService, roleRepository, userRoleRepository, employeeRepository, Collections.emptyList());
    }

    @Test
    @DisplayName("REPORTING_MANAGER resolves to immediate manager")
    void reportingManagerResolves() {
        Employee m1 = mock(Employee.class);
        when(m1.getId()).thenReturn(manager1Id);
        when(reportingLineService.chainAbove(eq(employeeId), any())).thenReturn(List.of(m1));

        Optional<UUID> resolved = resolver.resolve(tenantId, employeeId, ApproverKind.REPORTING_MANAGER, null);

        assertThat(resolved).contains(manager1Id);
    }

    @Test
    @DisplayName("INDIRECT_MANAGER resolves to second-level manager")
    void indirectManagerResolves() {
        Employee m1 = mock(Employee.class);
        when(m1.getId()).thenReturn(manager1Id);
        Employee m2 = mock(Employee.class);
        when(m2.getId()).thenReturn(manager2Id);
        when(reportingLineService.chainAbove(eq(employeeId), any())).thenReturn(List.of(m1, m2));

        Optional<UUID> resolved = resolver.resolve(tenantId, employeeId, ApproverKind.INDIRECT_MANAGER, null);

        assertThat(resolved).contains(manager2Id);
    }

    @Test
    @DisplayName("APPROVER_LEVEL_1, 2, 3 resolve to chain depths 0, 1, 2")
    void approverLevelsResolve() {
        Employee m1 = mock(Employee.class);
        when(m1.getId()).thenReturn(manager1Id);
        Employee m2 = mock(Employee.class);
        when(m2.getId()).thenReturn(manager2Id);
        Employee m3 = mock(Employee.class);
        when(m3.getId()).thenReturn(manager3Id);
        when(reportingLineService.chainAbove(eq(employeeId), any())).thenReturn(List.of(m1, m2, m3));

        assertThat(resolver.resolve(tenantId, employeeId, ApproverKind.APPROVER_LEVEL_1, null))
                .contains(manager1Id);
        assertThat(resolver.resolve(tenantId, employeeId, ApproverKind.APPROVER_LEVEL_2, null))
                .contains(manager2Id);
        assertThat(resolver.resolve(tenantId, employeeId, ApproverKind.APPROVER_LEVEL_3, null))
                .contains(manager3Id);
    }

    @Test
    @DisplayName("Employee with no manager yields an unassignable step (empty Optional)")
    void employeeWithNoManagerYieldsUnassignableStep() {
        when(reportingLineService.chainAbove(eq(employeeId), any())).thenReturn(Collections.emptyList());

        Optional<UUID> resolved = resolver.resolve(tenantId, employeeId, ApproverKind.REPORTING_MANAGER, null);

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("NAMED_EMPLOYEE resolves to parsed UUID")
    void namedEmployeeResolves() {
        UUID targetId = UUID.randomUUID();
        Optional<UUID> resolved =
                resolver.resolve(tenantId, employeeId, ApproverKind.NAMED_EMPLOYEE, targetId.toString());

        assertThat(resolved).contains(targetId);
    }

    @Test
    @DisplayName("ROLE resolves to an active employee holding that role")
    void roleResolves() {
        UUID roleId = UUID.randomUUID();
        UUID userAccountId = UUID.randomUUID();
        UUID hrEmpId = UUID.randomUUID();

        Role role = mock(Role.class);
        when(role.getId()).thenReturn(roleId);
        when(roleRepository.findByTenantIdAndCode(tenantId, "hr")).thenReturn(Optional.of(role));

        UserRole userRole = mock(UserRole.class);
        when(userRole.getUserAccountId()).thenReturn(userAccountId);
        when(userRoleRepository.findByTenantIdAndRoleId(tenantId, roleId)).thenReturn(List.of(userRole));

        Employee hrEmp = mock(Employee.class);
        when(hrEmp.getId()).thenReturn(hrEmpId);
        when(employeeRepository.findByTenantIdAndUserAccountIdAndDeletedFalse(tenantId, userAccountId))
                .thenReturn(Optional.of(hrEmp));

        Optional<UUID> resolved = resolver.resolve(tenantId, employeeId, ApproverKind.ROLE, "hr");

        assertThat(resolved).contains(hrEmpId);
    }

    @Test
    @DisplayName("PROJECT_MANAGER without custom resolver bean yields unassignable (empty Optional)")
    void projectManagerWithoutBeanYieldsEmpty() {
        Optional<UUID> resolved = resolver.resolve(tenantId, employeeId, ApproverKind.PROJECT_MANAGER, null);
        assertThat(resolved).isEmpty();
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    /**
     * Starts a one-step flow of the given kind through {@link ApprovalService}, with the given item references, and
     * returns what the step's approver was resolved with.
     */
    private String contextRefPassedFor(ApprovalStepDefinition step, List<String> itemRefs) {
        ApprovalDefinitionRepository definitions = mock(ApprovalDefinitionRepository.class);
        ApprovalInstanceRepository instances = mock(ApprovalInstanceRepository.class);
        ApprovalStepRepository steps = mock(ApprovalStepRepository.class);
        CoreApproverResolver approverResolver = mock(CoreApproverResolver.class);
        when(approverResolver.resolve(any(), any(), any(), any())).thenReturn(Optional.empty());
        // An unsaved definition has no id, which an instance requires, so the definition is a stand-in.
        ApprovalDefinition definition = mock(ApprovalDefinition.class);
        when(definition.getId()).thenReturn(UUID.randomUUID());
        when(definition.getSteps()).thenReturn(List.of(step));
        when(definition.getStepOrdering()).thenReturn(StepOrdering.SEQUENTIAL);
        when(definitions.findEffectiveDefinitions(eq(tenantId), eq(ApprovalFlowType.TIMESHEET), any()))
                .thenReturn(List.of(definition));
        when(instances.save(any())).thenAnswer(invocation -> {
            ApprovalInstance saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        ApprovalService service = new ApprovalService(
                definitions,
                instances,
                steps,
                approverResolver,
                mock(EmployeeService.class),
                mock(OutcomeDispatcher.class));

        TenantContext.set(tenantId);
        service.start(
                ApprovalFlowType.TIMESHEET,
                new SubjectRef("hrms.timesheet_project_entry", UUID.randomUUID()),
                employeeId,
                itemRefs);

        ArgumentCaptor<String> contextRef = ArgumentCaptor.forClass(String.class);
        verify(approverResolver).resolve(eq(tenantId), eq(employeeId), eq(step.getKind()), contextRef.capture());
        return contextRef.getValue();
    }

    @Test
    @DisplayName("W-42.2: a per-item PROJECT_MANAGER step is resolved by its item, the project id")
    void projectManagerStepIsResolvedByItsItem() {
        UUID projectId = UUID.randomUUID();

        String passed = contextRefPassedFor(
                new ApprovalStepDefinition(ApproverKind.PROJECT_MANAGER, null, 3, true), List.of(projectId.toString()));

        assertThat(passed).isEqualTo(projectId.toString());
    }

    @Test
    @DisplayName("W-42.2: a per-item ROLE step still gets its assignee (\"hr\"), not its item")
    void perItemRoleStepStillGetsItsAssignee() {
        String passed =
                contextRefPassedFor(new ApprovalStepDefinition(ApproverKind.ROLE, "hr", 3, true), List.of("item-1"));

        assertThat(passed).isEqualTo("hr");
    }

    @Test
    @DisplayName("W-42.2: a PROJECT_MANAGER step started with no item reference passes null, as before")
    void projectManagerStepWithoutItemPassesItsAssignee() {
        String passed = contextRefPassedFor(
                new ApprovalStepDefinition(ApproverKind.PROJECT_MANAGER, null, 3, true), Collections.emptyList());

        assertThat(passed).isNull();
    }
}
