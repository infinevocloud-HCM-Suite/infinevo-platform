package com.infinevo.hrms.exit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.shared.tenant.TenantContext;
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
import org.springframework.context.ApplicationEventPublisher;

class ExitServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID EMP_ID = UUID.randomUUID();

    private ExitRequestRepository exitRepo;
    private ExitClearanceItemRepository clearanceRepo;
    private EmployeeService employeeService;
    private ApplicationEventPublisher publisher;
    private ExitServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        exitRepo = mock(ExitRequestRepository.class);
        clearanceRepo = mock(ExitClearanceItemRepository.class);
        employeeService = mock(EmployeeService.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new ExitServiceImpl(exitRepo, clearanceRepo, employeeService, publisher);

        EmployeeResponse emp = new EmployeeResponse(
                EMP_ID,
                TENANT_ID,
                "EMP-001",
                "Asha",
                null,
                "Kulkarni",
                "FEMALE",
                LocalDate.of(2022, 1, 10),
                null,
                EmploymentStatus.ACTIVE,
                "asha@example.com",
                null,
                true,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
        when(employeeService.currentEmployee()).thenReturn(Optional.of(emp));
        when(employeeService.get(EMP_ID)).thenReturn(emp);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Rejects resignation when requestedLwd is before resignationDate")
    void rejectsLwdBeforeResignationDate() {
        SubmitResignationRequest invalid = new SubmitResignationRequest(
                LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 1), "RESIGNATION", "PERSONAL", null);

        assertThatThrownBy(() -> service.submitResignation(invalid)).isInstanceOf(ExitValidationException.class);
    }

    @Test
    @DisplayName("Rejects duplicate active resignation for same employee")
    void rejectsDuplicateActiveResignation() {
        when(exitRepo.existsByTenantIdAndEmployeeIdAndStatusNotIn(any(), any(), any()))
                .thenReturn(true);

        SubmitResignationRequest req = new SubmitResignationRequest(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "RESIGNATION", "BETTER_OPPORTUNITY", null);

        assertThatThrownBy(() -> service.submitResignation(req)).isInstanceOf(ExitConflictException.class);
    }

    @Test
    @DisplayName("Approving exit generates 4 departmental checklists and clearing all publishes ExitClearedEvent")
    void approveAndClearAllDepartments() {
        UUID exitId = UUID.randomUUID();
        ExitRequest entity = new ExitRequest();
        entity.setId(exitId);
        entity.setTenantId(TENANT_ID);
        entity.setEmployeeId(EMP_ID);
        entity.setResignationDate(LocalDate.of(2026, 10, 1));
        entity.setRequestedLwd(LocalDate.of(2026, 10, 31));
        entity.setExitType("RESIGNATION");
        entity.setReasonCategory("CAREER_GROWTH");
        entity.setStatus(ExitStatus.SUBMITTED);

        when(exitRepo.findByIdAndTenantId(exitId, TENANT_ID)).thenReturn(Optional.of(entity));
        when(exitRepo.saveAndFlush(any(ExitRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(clearanceRepo.saveAllAndFlush(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ExitRequestResponse approved = service.approveExit(exitId, new ApproveExitRequest(LocalDate.of(2026, 10, 30)));
        assertThat(approved.status()).isEqualTo(ExitStatus.CLEARANCE_IN_PROGRESS);
        assertThat(approved.approvedLwd()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(approved.clearanceItems()).hasSize(8);
        assertThat(approved.clearanceItems())
                .extracting(ExitClearanceItemResponse::department)
                .containsOnly(
                        ClearanceDepartment.IT,
                        ClearanceDepartment.FINANCE,
                        ClearanceDepartment.ADMIN,
                        ClearanceDepartment.HR);

        // Sign off IT department while others remain pending
        ExitClearanceItem itItem = new ExitClearanceItem();
        itItem.setId(UUID.randomUUID());
        itItem.setTenantId(TENANT_ID);
        itItem.setExitRequestId(exitId);
        itItem.setDepartment(ClearanceDepartment.IT);
        itItem.setItemName("Laptop & Hardware Return");
        itItem.setStatus(ClearanceItemStatus.PENDING);

        when(clearanceRepo.findByExitRequestIdAndTenantIdAndDepartment(exitId, TENANT_ID, ClearanceDepartment.IT))
                .thenReturn(List.of(itItem));
        when(clearanceRepo.existsByExitRequestIdAndTenantIdAndStatus(exitId, TENANT_ID, ClearanceItemStatus.PENDING))
                .thenReturn(false);

        ExitRequestResponse ready = service.signOffDepartmentClearance(
                exitId,
                ClearanceDepartment.IT,
                new UpdateClearanceRequest(ClearanceItemStatus.CLEARED, "All returned"));
        assertThat(ready.status()).isEqualTo(ExitStatus.READY_FOR_FNF);

        ArgumentCaptor<ExitClearedEvent> eventCaptor = ArgumentCaptor.forClass(ExitClearedEvent.class);
        verify(publisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().exitRequestId()).isEqualTo(exitId);
        assertThat(eventCaptor.getValue().exitDate()).isEqualTo(LocalDate.of(2026, 10, 30));

        PayrollHandOffPayload handOff = service.handOffToPayroll(exitId);
        assertThat(handOff.employeeId()).isEqualTo(EMP_ID);
        assertThat(handOff.exitDate()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(handOff.exitStatus()).isEqualTo(ExitStatus.SETTLED);
    }
}
