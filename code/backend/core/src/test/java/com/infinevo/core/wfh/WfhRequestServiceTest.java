package com.infinevo.core.wfh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.SubjectRef;
import com.infinevo.core.attendance.Attendance;
import com.infinevo.core.attendance.AttendanceRepository;
import com.infinevo.core.attendance.AttendanceSource;
import com.infinevo.core.attendance.AttendanceStatus;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.leave.LeaveRequest;
import com.infinevo.core.leave.LeaveRequestRepository;
import com.infinevo.core.leave.WorkingDayCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WfhRequestServiceTest {

    private WfhRequestRepository requestRepository;
    private LeaveRequestRepository leaveRequestRepository;
    private WfhBalanceService balanceService;
    private WfhPolicyService policyService;
    private WfhConsumptionRepository consumptionRepository;
    private EmployeeRepository employeeRepository;
    private WorkingDayCalculator workingDayCalculator;
    private AttendanceRepository attendanceRepository;
    private ApprovalService approvalService;

    private WfhRequestServiceImpl requestService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private Employee employee;

    @BeforeEach
    void setUp() {
        requestRepository = mock(WfhRequestRepository.class);
        leaveRequestRepository = mock(LeaveRequestRepository.class);
        balanceService = mock(WfhBalanceService.class);
        policyService = mock(WfhPolicyService.class);
        consumptionRepository = mock(WfhConsumptionRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        workingDayCalculator = mock(WorkingDayCalculator.class);
        attendanceRepository = mock(AttendanceRepository.class);
        approvalService = mock(ApprovalService.class);

        requestService = new WfhRequestServiceImpl(
                requestRepository,
                leaveRequestRepository,
                balanceService,
                policyService,
                consumptionRepository,
                employeeRepository,
                workingDayCalculator,
                attendanceRepository,
                approvalService);

        employee = mock(Employee.class);
        when(employee.getId()).thenReturn(employeeId);
        when(employee.getTenantId()).thenReturn(tenantId);
        when(employee.getFirstName()).thenReturn("Krushna");
        when(employee.getLastName()).thenReturn("Sonalkar");
        when(employee.getEmployeeNumber()).thenReturn("EMP001");

        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(Optional.of(employee));
    }

    @Test
    @DisplayName("fromDate after toDate throws IllegalArgumentException")
    void applyWithReversedDatesThrows() {
        ApplyWfhRequest req = new ApplyWfhRequest(
                employeeId, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 5), false, null, "Focus sprint");

        assertThatThrownBy(() -> requestService.apply(tenantId, employeeId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromDate cannot be after toDate");
    }

    @Test
    @DisplayName("Half day request with multi-day span throws IllegalArgumentException")
    void applyHalfDayMultiDayThrows() {
        ApplyWfhRequest req = new ApplyWfhRequest(
                employeeId, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6), true, "first", "Half day");

        assertThatThrownBy(() -> requestService.apply(tenantId, employeeId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Half-day WFH request must have identical fromDate and toDate");
    }

    @Test
    @DisplayName("Overlapping active WFH request throws IllegalArgumentException")
    void applyOverlappingWfhThrows() {
        LocalDate from = LocalDate.of(2026, 11, 5);
        LocalDate to = LocalDate.of(2026, 11, 6);

        when(workingDayCalculator.calculateWorkingDays(
                        eq(from), eq(to), anyBoolean(), anyBoolean(), anyBoolean(), nullable(UUID.class)))
                .thenReturn(new BigDecimal("2.00"));
        when(requestRepository.existsOverlappingRequest(eq(tenantId), eq(employeeId), any(), eq(from), eq(to)))
                .thenReturn(true);

        ApplyWfhRequest req = new ApplyWfhRequest(employeeId, from, to, false, null, "Focus sprint");

        assertThatThrownBy(() -> requestService.apply(tenantId, employeeId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlapping these dates");
    }

    @Test
    @DisplayName("Overlapping leave request throws IllegalArgumentException (Mutual Exclusion)")
    void applyOverlappingLeaveThrows() {
        LocalDate from = LocalDate.of(2026, 11, 5);
        LocalDate to = LocalDate.of(2026, 11, 6);

        when(workingDayCalculator.calculateWorkingDays(
                        eq(from), eq(to), anyBoolean(), anyBoolean(), anyBoolean(), nullable(UUID.class)))
                .thenReturn(new BigDecimal("2.00"));
        when(requestRepository.existsOverlappingRequest(eq(tenantId), eq(employeeId), any(), eq(from), eq(to)))
                .thenReturn(false);

        LeaveRequest overlappingLeave = new LeaveRequest();
        when(leaveRequestRepository.findOverlapping(eq(tenantId), eq(employeeId), eq(from), eq(to), any(), any()))
                .thenReturn(List.of(overlappingLeave));

        ApplyWfhRequest req = new ApplyWfhRequest(employeeId, from, to, false, null, "Focus sprint");

        assertThatThrownBy(() -> requestService.apply(tenantId, employeeId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Employee already has a pending or approved leave request");
    }

    @Test
    @DisplayName("Insufficient balance throws IllegalArgumentException")
    void applyInsufficientBalanceThrows() {
        LocalDate from = LocalDate.of(2026, 11, 5);
        LocalDate to = LocalDate.of(2026, 11, 6);

        when(workingDayCalculator.calculateWorkingDays(
                        eq(from), eq(to), anyBoolean(), anyBoolean(), anyBoolean(), nullable(UUID.class)))
                .thenReturn(new BigDecimal("2.00"));
        when(requestRepository.existsOverlappingRequest(any(), any(), any(), any(), any()))
                .thenReturn(false);
        when(leaveRequestRepository.findOverlapping(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        // Balance is only 1.00 day
        when(balanceService.getAvailableDays(tenantId, employeeId, from)).thenReturn(new BigDecimal("1.00"));

        ApplyWfhRequest req = new ApplyWfhRequest(employeeId, from, to, false, null, "Focus sprint");

        assertThatThrownBy(() -> requestService.apply(tenantId, employeeId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient WFH balance");
    }

    @Test
    @DisplayName("Exceeding monthly limit throws IllegalArgumentException")
    void applyExceedsMonthlyLimitThrows() {
        LocalDate from = LocalDate.of(2026, 11, 5);
        LocalDate to = LocalDate.of(2026, 11, 6);

        when(workingDayCalculator.calculateWorkingDays(
                        eq(from), eq(to), anyBoolean(), anyBoolean(), anyBoolean(), nullable(UUID.class)))
                .thenReturn(new BigDecimal("2.00"));
        when(requestRepository.existsOverlappingRequest(any(), any(), any(), any(), any()))
                .thenReturn(false);
        when(leaveRequestRepository.findOverlapping(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(balanceService.getAvailableDays(tenantId, employeeId, from)).thenReturn(new BigDecimal("10.00"));
        // Monthly velocity cap fails
        when(balanceService.isWithinMonthlyLimit(tenantId, employeeId, from, new BigDecimal("2.00")))
                .thenReturn(false);

        ApplyWfhRequest req = new ApplyWfhRequest(employeeId, from, to, false, null, "Focus sprint");

        assertThatThrownBy(() -> requestService.apply(tenantId, employeeId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request exceeds maximum allowable WFH days for the month");
    }

    @Test
    @DisplayName("Successful apply persists PENDING request and triggers WORK_FROM_HOME approval flow")
    void applySuccessInitiatesApprovalFlow() {
        LocalDate from = LocalDate.of(2026, 11, 5);
        LocalDate to = LocalDate.of(2026, 11, 6);

        when(workingDayCalculator.calculateWorkingDays(
                        eq(from), eq(to), anyBoolean(), anyBoolean(), anyBoolean(), nullable(UUID.class)))
                .thenReturn(new BigDecimal("2.00"));
        when(requestRepository.existsOverlappingRequest(any(), any(), any(), any(), any()))
                .thenReturn(false);
        when(leaveRequestRepository.findOverlapping(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(balanceService.getAvailableDays(tenantId, employeeId, from)).thenReturn(new BigDecimal("10.00"));
        when(balanceService.isWithinMonthlyLimit(tenantId, employeeId, from, new BigDecimal("2.00")))
                .thenReturn(true);

        when(requestRepository.save(any(WfhRequest.class))).thenAnswer(inv -> {
            WfhRequest r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });

        UUID expectedInstanceId = UUID.randomUUID();
        when(approvalService.start(eq(ApprovalFlowType.WORK_FROM_HOME), any(SubjectRef.class), eq(employeeId)))
                .thenReturn(expectedInstanceId);

        ApplyWfhRequest req = new ApplyWfhRequest(employeeId, from, to, false, null, "Focus sprint");
        WfhRequestResponse res = requestService.apply(tenantId, employeeId, req);

        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo(WfhRequestStatus.PENDING);
        assertThat(res.workingDays()).isEqualByComparingTo("2.00");
        assertThat(res.employeeName()).isEqualTo("Krushna Sonalkar");

        verify(approvalService).start(eq(ApprovalFlowType.WORK_FROM_HOME), any(SubjectRef.class), eq(employeeId));
    }

    @Test
    @DisplayName("Withdraw PENDING request transitions to WITHDRAWN and cancels approval flow")
    void withdrawPendingRequestCancelsApproval() {
        UUID reqId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();

        WfhRequest req = new WfhRequest();
        req.setId(reqId);
        req.setTenantId(tenantId);
        req.setEmployeeId(employeeId);
        req.setStatus(WfhRequestStatus.PENDING);
        req.setApprovalInstanceId(instanceId);

        when(requestRepository.findByTenantIdAndId(tenantId, reqId)).thenReturn(Optional.of(req));
        when(requestRepository.save(any(WfhRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        WfhRequestResponse res = requestService.withdraw(tenantId, reqId, employeeId, "Change of plans");
        assertThat(res.status()).isEqualTo(WfhRequestStatus.WITHDRAWN);

        verify(approvalService).cancelInstance(tenantId, instanceId, "Change of plans");
    }

    @Test
    @DisplayName("Cancel APPROVED request for future date reverses consumption and clears attendance")
    void cancelApprovedRequestReversesConsumption() {
        UUID reqId = UUID.randomUUID();
        LocalDate futureDate = LocalDate.now().plusDays(5);

        WfhRequest req = new WfhRequest();
        req.setId(reqId);
        req.setTenantId(tenantId);
        req.setEmployeeId(employeeId);
        req.setStatus(WfhRequestStatus.APPROVED);
        req.setFromDate(futureDate);
        req.setToDate(futureDate);
        req.setWorkingDays(BigDecimal.ONE);

        when(requestRepository.findByTenantIdAndId(tenantId, reqId)).thenReturn(Optional.of(req));
        when(requestRepository.save(any(WfhRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID allocId = UUID.randomUUID();
        WfhConsumption originalConsumption = new WfhConsumption();
        originalConsumption.setId(UUID.randomUUID());
        originalConsumption.setTenantId(tenantId);
        originalConsumption.setEmployeeId(employeeId);
        originalConsumption.setAllocationId(allocId);
        originalConsumption.setWfhRequestId(reqId);
        originalConsumption.setConsumedDays(BigDecimal.ONE);
        originalConsumption.setPeriod("2026-11");

        when(consumptionRepository.findByTenantIdAndWfhRequestIdAndReversesIdIsNull(tenantId, reqId))
                .thenReturn(Optional.of(originalConsumption));

        Attendance att = new Attendance(
                tenantId,
                employee,
                futureDate,
                AttendanceStatus.PRESENT,
                AttendanceSource.ADMIN,
                "Work From Home",
                "system");
        when(attendanceRepository.findByTenantIdAndEmployeeIdAndAttendanceDate(tenantId, employeeId, futureDate))
                .thenReturn(Optional.of(att));

        WfhRequestResponse res = requestService.cancel(tenantId, reqId, employeeId, "Project rescheduled");
        assertThat(res.status()).isEqualTo(WfhRequestStatus.CANCELLED);

        // Verify consumption reversal was saved
        verify(consumptionRepository).save(any(WfhConsumption.class));
        // Verify attendance record was deleted
        verify(attendanceRepository).delete(att);
    }
}
