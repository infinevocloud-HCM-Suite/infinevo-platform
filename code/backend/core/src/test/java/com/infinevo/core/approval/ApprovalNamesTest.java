package com.infinevo.core.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * D-79: the approval instance detail, its history and the delegation list name every person they
 * carry an id for, read with one batch {@code displayNames} call - never one per step or per row.
 */
class ApprovalNamesTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();
    private final UUID requester = UUID.randomUUID();
    private final UUID manager = UUID.randomUUID();
    private final UUID hr = UUID.randomUUID();
    private final UUID original = UUID.randomUUID();

    private ApprovalInstanceRepository instanceRepository;
    private ApprovalStepRepository stepRepository;
    private EmployeeService employeeService;
    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        instanceRepository = mock(ApprovalInstanceRepository.class);
        stepRepository = mock(ApprovalStepRepository.class);
        employeeService = mock(EmployeeService.class);
        approvalService = new ApprovalService(
                mock(ApprovalDefinitionRepository.class),
                instanceRepository,
                stepRepository,
                mock(CoreApproverResolver.class),
                employeeService,
                mock(OutcomeDispatcher.class));

        ApprovalInstance instance = new ApprovalInstance(
                tenantId,
                ApprovalFlowType.LEAVE,
                UUID.randomUUID(),
                new SubjectRef("core.leave_request", UUID.randomUUID()),
                requester);
        instance.setId(instanceId);

        ApprovalStep first = new ApprovalStep(tenantId, instanceId, 0, null, ApproverKind.REPORTING_MANAGER, manager);
        first.setId(UUID.randomUUID());
        first.setDecision(ApprovalDecision.APPROVED);
        ApprovalStep second = new ApprovalStep(tenantId, instanceId, 1, null, ApproverKind.ROLE, hr);
        second.setId(UUID.randomUUID());
        second.setReassignedFromEmployeeId(original);
        second.setDelegatedFromEmployeeId(original);
        second.setEscalatedFromEmployeeId(manager);

        when(instanceRepository.findByTenantIdAndId(tenantId, instanceId)).thenReturn(Optional.of(instance));
        when(stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instanceId))
                .thenReturn(List.of(first, second));
        when(employeeService.displayNames(anyCollection()))
                .thenReturn(Map.of(
                        requester, "Asha Rao",
                        manager, "Vikram Shah",
                        hr, "Meera Iyer",
                        original, "Ravi Kumar"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("instance detail names the requester and every person on every step, in one read")
    void instanceDetailCarriesNames() {
        ApprovalInstanceDetailResponse detail = approvalService.getInstance(instanceId);

        assertThat(detail.subjectEmployeeId()).isEqualTo(requester);
        assertThat(detail.subjectEmployeeName()).isEqualTo("Asha Rao");
        assertThat(detail.steps())
                .extracting(ApprovalStepResponse::assigneeEmployeeName)
                .containsExactly("Vikram Shah", "Meera Iyer");
        ApprovalStepResponse second = detail.steps().get(1);
        assertThat(second.reassignedFromEmployeeName()).isEqualTo("Ravi Kumar");
        assertThat(second.delegatedFromEmployeeName()).isEqualTo("Ravi Kumar");
        assertThat(second.escalatedFromEmployeeName()).isEqualTo("Vikram Shah");
        assertThat(second.subjectEmployeeName()).isEqualTo("Asha Rao");
        assertThat(detail.steps().get(0).reassignedFromEmployeeName()).isNull();

        assertOneBatchOf(Set.of(requester, manager, hr, original));
    }

    @Test
    @DisplayName("history names the requester, each assignee and who a step came from, in one read")
    void historyCarriesNames() {
        ApprovalHistoryResponse history = approvalService.getHistory(instanceId);

        assertThat(history.subjectEmployeeName()).isEqualTo("Asha Rao");
        assertThat(history.steps())
                .extracting(ApprovalHistoryResponse.ApprovalHistoryStepResponse::assigneeName)
                .containsExactly("Vikram Shah", "Meera Iyer");
        ApprovalHistoryResponse.ApprovalHistoryStepResponse second =
                history.steps().get(1);
        assertThat(second.reassignedFromEmployeeId()).isEqualTo(original);
        assertThat(second.reassignedFromEmployeeName()).isEqualTo("Ravi Kumar");
        assertThat(second.delegatedFromEmployeeName()).isEqualTo("Ravi Kumar");
        assertThat(second.escalatedFromEmployeeName()).isEqualTo("Vikram Shah");

        assertOneBatchOf(Set.of(requester, manager, hr, original));
    }

    @Test
    @DisplayName("a person the name read does not return is sent with a null name, the id kept")
    void unknownPersonHasNullName() {
        when(employeeService.displayNames(anyCollection())).thenReturn(Map.of());

        ApprovalInstanceDetailResponse detail = approvalService.getInstance(instanceId);

        assertThat(detail.subjectEmployeeId()).isEqualTo(requester);
        assertThat(detail.subjectEmployeeName()).isNull();
        assertThat(detail.steps().get(0).assigneeEmployeeId()).isEqualTo(manager);
        assertThat(detail.steps().get(0).assigneeEmployeeName()).isNull();
    }

    @Test
    @DisplayName("the delegation list names delegator and delegate with one read for the whole list")
    void delegationsCarryNames() {
        ApprovalDelegationRepository delegations = mock(ApprovalDelegationRepository.class);
        DelegationServiceImpl service = new DelegationServiceImpl(
                delegations, mock(com.infinevo.core.employee.EmployeeRepository.class), employeeService);
        LocalDate today = LocalDate.of(2026, 10, 13);
        ApprovalDelegation mine = new ApprovalDelegation(tenantId, manager, hr, null, today, today.plusDays(5));
        mine.setId(UUID.randomUUID());
        ApprovalDelegation toMe = new ApprovalDelegation(tenantId, original, manager, "LEAVE", today, today);
        toMe.setId(UUID.randomUUID());
        when(delegations.searchDelegations(tenantId, manager, null)).thenReturn(List.of(mine, toMe));

        List<DelegationResponse> rows = service.getDelegations(tenantId, manager, null);

        assertThat(rows)
                .extracting(DelegationResponse::delegatorEmployeeName)
                .containsExactly("Vikram Shah", "Ravi Kumar");
        assertThat(rows)
                .extracting(DelegationResponse::delegateEmployeeName)
                .containsExactly("Meera Iyer", "Vikram Shah");
        assertThat(rows.get(0).delegatorEmployeeId()).isEqualTo(manager);
        // seenBy keeps the names.
        assertThat(rows.get(0).seenBy(manager).delegateEmployeeName()).isEqualTo("Meera Iyer");
        assertOneBatchOf(Set.of(manager, hr, original));
    }

    @SuppressWarnings("unchecked")
    private void assertOneBatchOf(Set<UUID> expected) {
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(employeeService, times(1)).displayNames(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrderElementsOf(expected);
    }
}
