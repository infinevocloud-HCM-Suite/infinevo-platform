package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DelegationServiceTest {

    private ApprovalDelegationRepository delegationRepository;
    private EmployeeRepository employeeRepository;
    private DelegationServiceImpl delegationService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID delegatorId = UUID.randomUUID();
    private final UUID delegateId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        delegationRepository = mock(ApprovalDelegationRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        delegationService = new DelegationServiceImpl(delegationRepository, employeeRepository);
    }

    @Test
    @DisplayName("an active delegation redirects to the delegate")
    void activeDelegationRedirects() {
        LocalDate today = LocalDate.of(2026, 9, 20);
        ApprovalDelegation delegation =
                new ApprovalDelegation(tenantId, delegatorId, delegateId, null, today.minusDays(1), today.plusDays(5));

        when(delegationRepository.findActiveByDelegator(tenantId, delegatorId, today))
                .thenReturn(List.of(delegation));

        Optional<UUID> resolved =
                delegationService.resolveDelegate(tenantId, delegatorId, ApprovalFlowType.LEAVE, today);

        assertThat(resolved).isPresent().contains(delegateId);
    }

    @Test
    @DisplayName("an expired delegation does not redirect")
    void expiredDelegationDoesNotRedirect() {
        LocalDate today = LocalDate.of(2026, 9, 20);

        when(delegationRepository.findActiveByDelegator(tenantId, delegatorId, today))
                .thenReturn(Collections.emptyList());

        Optional<UUID> resolved =
                delegationService.resolveDelegate(tenantId, delegatorId, ApprovalFlowType.LEAVE, today);

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("a flow-scoped delegation redirects only that flow")
    void flowScopedDelegationRedirectsOnlyCoveredFlow() {
        LocalDate today = LocalDate.of(2026, 9, 20);
        ApprovalDelegation delegation = new ApprovalDelegation(
                tenantId, delegatorId, delegateId, "LEAVE,REIMBURSEMENT", today.minusDays(1), today.plusDays(5));

        when(delegationRepository.findActiveByDelegator(tenantId, delegatorId, today))
                .thenReturn(List.of(delegation));

        Optional<UUID> leaveDelegate =
                delegationService.resolveDelegate(tenantId, delegatorId, ApprovalFlowType.LEAVE, today);
        Optional<UUID> reimbursementDelegate =
                delegationService.resolveDelegate(tenantId, delegatorId, ApprovalFlowType.REIMBURSEMENT, today);
        Optional<UUID> timesheetDelegate =
                delegationService.resolveDelegate(tenantId, delegatorId, ApprovalFlowType.TIMESHEET, today);

        assertThat(leaveDelegate).isPresent().contains(delegateId);
        assertThat(reimbursementDelegate).isPresent().contains(delegateId);
        assertThat(timesheetDelegate).isEmpty();
    }

    @Test
    @DisplayName("self-delegation is refused")
    void selfDelegationRefused() {
        DelegationCreateRequest request =
                new DelegationCreateRequest(delegatorId, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 25), "LEAVE");

        assertThatThrownBy(() -> delegationService.createDelegation(tenantId, delegatorId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Delegator and delegate cannot be the same employee");
    }

    @Test
    @DisplayName("delegation chain is refused: delegate already has active delegation")
    void delegationChainRefusedWhenDelegateAlreadyDelegator() {
        LocalDate from = LocalDate.of(2026, 9, 20);
        LocalDate to = LocalDate.of(2026, 9, 25);
        DelegationCreateRequest request = new DelegationCreateRequest(delegateId, from, to, null);

        Employee mockEmp = mock(Employee.class);
        when(mockEmp.getId()).thenReturn(delegateId);
        when(mockEmp.getTenantId()).thenReturn(tenantId);
        when(mockEmp.isDeleted()).thenReturn(false);
        when(employeeRepository.findById(delegateId)).thenReturn(Optional.of(mockEmp));

        ApprovalDelegation existingChain =
                new ApprovalDelegation(tenantId, delegateId, UUID.randomUUID(), null, from, to);
        when(delegationRepository.findActiveOverlappingForEmployee(tenantId, delegateId, from, to))
                .thenReturn(List.of(existingChain));

        assertThatThrownBy(() -> delegationService.createDelegation(tenantId, delegatorId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Delegation chains are not allowed");
    }
}
