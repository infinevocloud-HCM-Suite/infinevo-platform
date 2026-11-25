package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.employee.EmployeeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-114.8 spec, Tests: {@link LeaveRequestService#validateApply} refuses exactly what {@code createRequest} refuses,
 * with the same exception and message, and saves nothing. {@code LeaveRequestServiceImplTest} is unchanged.
 */
class LeaveRequestValidateApplyTest {

    private LeaveRequestRepository leaveRequestRepository;
    private LeaveRequestDocumentRepository leaveRequestDocumentRepository;
    private LeaveTypeRepository leaveTypeRepository;
    private LeavePolicyRepository leavePolicyRepository;
    private LeaveEligibilityService leaveEligibilityService;
    private LeaveBalanceService leaveBalanceService;
    private WorkingDayCalculator workingDayCalculator;
    private ApprovalService approvalService;
    private LeaveRequestServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID leaveTypeId = UUID.randomUUID();
    private final LocalDate from = LocalDate.now().plusDays(3);

    @BeforeEach
    void setUp() {
        leaveRequestRepository = mock(LeaveRequestRepository.class);
        leaveRequestDocumentRepository = mock(LeaveRequestDocumentRepository.class);
        leaveTypeRepository = mock(LeaveTypeRepository.class);
        leavePolicyRepository = mock(LeavePolicyRepository.class);
        leaveEligibilityService = mock(LeaveEligibilityService.class);
        leaveBalanceService = mock(LeaveBalanceService.class);
        workingDayCalculator = mock(WorkingDayCalculator.class);
        approvalService = mock(ApprovalService.class);
        service = new LeaveRequestServiceImpl(
                leaveRequestRepository,
                leaveRequestDocumentRepository,
                leaveTypeRepository,
                leavePolicyRepository,
                leaveEligibilityService,
                leaveBalanceService,
                workingDayCalculator,
                approvalService,
                mock(ApprovalInstanceRepository.class),
                mock(EmployeeRepository.class),
                mock(LeaveConsumptionService.class));

        LeaveType type = new LeaveType(
                tenantId,
                "CL",
                "Casual Leave",
                true,
                LeaveUnit.DAYS,
                false,
                LocalDate.now().minusYears(1),
                null,
                true);
        type.setId(leaveTypeId);
        when(leaveTypeRepository.findByTenantIdAndId(tenantId, leaveTypeId)).thenReturn(Optional.of(type));
        when(leaveEligibilityService.isEligible(tenantId, employeeId, leaveTypeId, from))
                .thenReturn(true);
        when(workingDayCalculator.calculateWorkingDays(any(), any(), anyBoolean(), any(), any()))
                .thenReturn(new BigDecimal("2.00"));
        when(leaveRequestRepository.findOverlapping(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    private LeaveApplyRequest request(LocalDate to, boolean halfDay, HalfDayPeriod period) {
        return new LeaveApplyRequest(leaveTypeId, from, to, halfDay, period, "Family", null, true);
    }

    /** Both methods refuse the request with the same exception type and message; neither saves anything. */
    private void bothRefuse(LeaveApplyRequest request, Class<? extends RuntimeException> type, String message) {
        Throwable fromValidate = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.validateApply(tenantId, employeeId, request));
        Throwable fromCreate = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.createRequest(tenantId, employeeId, request));
        assertThat(fromValidate).isInstanceOf(type).hasMessageContaining(message);
        assertThat(fromCreate).isInstanceOf(type).hasMessage(fromValidate.getMessage());
        verify(leaveRequestRepository, never()).save(any());
        verifyNoInteractions(approvalService);
    }

    private void withPolicy(Consumer<LeavePolicy> setUp) {
        LeavePolicy policy = new LeavePolicy();
        policy.setTenantId(tenantId);
        policy.setLeaveTypeId(leaveTypeId);
        policy.setAnnualDays(new BigDecimal("12.00"));
        policy.setEffectiveFrom(LocalDate.now().minusMonths(1));
        setUp.accept(policy);
        when(leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                eq(tenantId), eq(leaveTypeId), eq(from)))
                .thenReturn(Optional.of(policy));
    }

    @Test
    @DisplayName("an accepted request: the type, its name, the half-day flag and the working days; nothing saved")
    void accepted_returnsCheckAndSavesNothing() {
        LeaveApplyCheck check = service.validateApply(tenantId, employeeId, request(from.plusDays(1), false, null));

        assertThat(check.leaveTypeId()).isEqualTo(leaveTypeId);
        assertThat(check.leaveTypeName()).isEqualTo("Casual Leave");
        assertThat(check.halfDay()).isFalse();
        assertThat(check.workingDays()).isEqualByComparingTo("2");
        verify(leaveRequestRepository, never()).save(any());
        verifyNoInteractions(approvalService);
    }

    @Test
    @DisplayName("from after to: both refuse")
    void datesReversed() {
        bothRefuse(request(from.minusDays(1), false, null), IllegalArgumentException.class, "cannot be after");
    }

    @Test
    @DisplayName("half day over two dates, without a period, or a period without half day: both refuse")
    void halfDayRules() {
        bothRefuse(request(from.plusDays(1), true, HalfDayPeriod.FIRST), IllegalArgumentException.class, "single date");
        bothRefuse(request(from, true, null), IllegalArgumentException.class, "halfDayPeriod is required");
        bothRefuse(request(from, false, HalfDayPeriod.FIRST), IllegalArgumentException.class, "must be null");
    }

    @Test
    @DisplayName("half day on a type that does not allow it: both refuse")
    void halfDayNotAllowed() {
        bothRefuse(request(from, true, HalfDayPeriod.SECOND), IllegalArgumentException.class, "not allowed");
    }

    @Test
    @DisplayName("an unknown or inactive leave type: both refuse")
    void unknownOrInactiveType() {
        when(leaveTypeRepository.findByTenantIdAndId(tenantId, leaveTypeId)).thenReturn(Optional.empty());
        bothRefuse(request(from, false, null), IllegalArgumentException.class, "Leave type not found");

        LeaveType inactive = new LeaveType(
                tenantId,
                "CL",
                "Casual Leave",
                true,
                LeaveUnit.DAYS,
                false,
                LocalDate.now().minusYears(1),
                null,
                false);
        when(leaveTypeRepository.findByTenantIdAndId(tenantId, leaveTypeId)).thenReturn(Optional.of(inactive));
        bothRefuse(request(from, false, null), IllegalArgumentException.class, "not active");
    }

    @Test
    @DisplayName("not eligible: both refuse")
    void notEligible() {
        when(leaveEligibilityService.isEligible(tenantId, employeeId, leaveTypeId, from))
                .thenReturn(false);
        bothRefuse(request(from, false, null), IllegalArgumentException.class, "not eligible");
    }

    @Test
    @DisplayName("a policy needing a document, with none: both refuse")
    void documentRequired() {
        withPolicy(p -> p.setRequiresDocument(true));
        bothRefuse(request(from, false, null), IllegalArgumentException.class, "requires supporting documents");
    }

    @Test
    @DisplayName("over the policy's maximum days per application: both refuse")
    void overMaxDays() {
        withPolicy(p -> p.setMaxDaysPerApplication(new BigDecimal("1.00")));
        bothRefuse(request(from.plusDays(1), false, null), IllegalArgumentException.class, "max days per application");
    }

    @Test
    @DisplayName("overlapping an active request: both refuse")
    void overlap() {
        when(leaveRequestRepository.findOverlapping(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(mock(LeaveRequest.class)));
        bothRefuse(request(from, false, null), IllegalStateException.class, "overlaps");
    }

    @Test
    @DisplayName("past the year-end negative limit: both refuse")
    void overBalance() {
        withPolicy(p -> {
            p.setExceedBalanceMode(ExceedBalanceMode.YEAR_END_LIMIT);
            p.setExceedBalanceLimitDays(BigDecimal.ZERO);
        });
        when(leaveBalanceService.getBalance(tenantId, employeeId, leaveTypeId, from))
                .thenReturn(Optional.empty());
        bothRefuse(request(from.plusDays(1), false, null), IllegalStateException.class, "negative balance limit");
    }

    @Test
    @DisplayName("createRequest still saves an accepted request and starts its approval")
    void createRequest_stillSaves() {
        when(leaveRequestRepository.save(any())).thenAnswer(inv -> {
            LeaveRequest saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(leaveTypeRepository.findByTenantIdAndIdIn(any(), any())).thenReturn(List.of());

        LeaveRequestResponse created = service.createRequest(tenantId, employeeId, request(from, false, null));

        assertThat(created.status()).isEqualTo(LeaveRequestStatus.PENDING);
        assertThat(created.workingDays()).isEqualByComparingTo("2");
        verify(approvalService).start(any(), any(), eq(employeeId));
    }

    @Test
    @DisplayName("a null tenant is refused before anything is read")
    void nullTenant() {
        assertThatThrownBy(() -> service.validateApply(null, employeeId, request(from, false, null)))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(leaveTypeRepository);
    }
}
