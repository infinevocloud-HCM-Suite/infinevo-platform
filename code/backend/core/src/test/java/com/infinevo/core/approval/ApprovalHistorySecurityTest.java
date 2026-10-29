package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * D-126: Self-service approval trail security and caller isolation tests.
 * Proves that an employee can read their own approval trail, but cannot view another
 * colleague's approval instance trail unless they hold admin or decider permissions.
 */
class ApprovalHistorySecurityTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();
    private final UUID requesterEmpId = UUID.randomUUID();
    private final UUID otherEmpId = UUID.randomUUID();

    private ApprovalInstanceRepository instanceRepository;
    private ApprovalStepRepository stepRepository;
    private EmployeeService employeeService;
    private PermissionService permissionService;
    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        instanceRepository = mock(ApprovalInstanceRepository.class);
        stepRepository = mock(ApprovalStepRepository.class);
        employeeService = mock(EmployeeService.class);
        permissionService = mock(PermissionService.class);

        approvalService = new ApprovalService(
                mock(ApprovalDefinitionRepository.class),
                instanceRepository,
                stepRepository,
                mock(CoreApproverResolver.class),
                employeeService,
                mock(OutcomeDispatcher.class),
                mock(DelegationService.class),
                permissionService);

        ApprovalInstance instance = new ApprovalInstance(
                tenantId,
                ApprovalFlowType.WORK_FROM_HOME,
                UUID.randomUUID(),
                new SubjectRef("core.wfh_request", UUID.randomUUID()),
                requesterEmpId);

        when(instanceRepository.findByTenantIdAndId(tenantId, instanceId)).thenReturn(Optional.of(instance));
        when(stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instanceId))
                .thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Employee viewing their own WFH request approval history succeeds (D-126)")
    void requesterCanViewOwnHistory() {
        EmployeeResponse requester = mock(EmployeeResponse.class);
        when(requester.id()).thenReturn(requesterEmpId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(requester));

        when(permissionService.holds("core.approval.read")).thenReturn(false);
        when(permissionService.holds("core.approval.manage")).thenReturn(false);
        when(permissionService.holds("core.approval.decide")).thenReturn(false);

        ApprovalHistoryResponse response = approvalService.getHistory(instanceId);
        assertThat(response).isNotNull();
        assertThat(response.subjectEmployeeId()).isEqualTo(requesterEmpId);
    }

    @Test
    @DisplayName(
            "Employee viewing another employee's WFH request approval history is refused 403 AccessDeniedException (D-126)")
    void colleagueCannotViewOthersHistory() {
        EmployeeResponse colleague = mock(EmployeeResponse.class);
        when(colleague.id()).thenReturn(otherEmpId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(colleague));

        when(permissionService.holds("core.approval.read")).thenReturn(false);
        when(permissionService.holds("core.approval.manage")).thenReturn(false);
        when(permissionService.holds("core.approval.decide")).thenReturn(false);

        assertThatThrownBy(() -> approvalService.getHistory(instanceId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Caller is not authorized to view approval history for this instance");
    }

    @Test
    @DisplayName("HR holding core.approval.read can view any approval history")
    void hrCanViewAnyHistory() {
        when(permissionService.holds("core.approval.read")).thenReturn(true);

        ApprovalHistoryResponse response = approvalService.getHistory(instanceId);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("Approver holding core.approval.decide can view approval history")
    void approverCanViewHistory() {
        when(permissionService.holds("core.approval.read")).thenReturn(false);
        when(permissionService.holds("core.approval.manage")).thenReturn(false);
        when(permissionService.holds("core.approval.decide")).thenReturn(true);

        ApprovalHistoryResponse response = approvalService.getHistory(instanceId);
        assertThat(response).isNotNull();
    }
}
