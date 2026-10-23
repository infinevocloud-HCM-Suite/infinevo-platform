package com.infinevo.core.wfh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.attendance.AttendanceRepository;
import com.infinevo.core.attendance.AttendanceStatus;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WfhApprovalOutcomeHandlerTest {

    private ApprovalInstanceRepository instanceRepository;
    private WfhRequestRepository requestRepository;
    private WfhAllocationRepository allocationRepository;
    private WfhConsumptionRepository consumptionRepository;
    private WfhPolicyService policyService;
    private EmployeeRepository employeeRepository;
    private AttendanceRepository attendanceRepository;

    private WfhApprovalOutcomeHandler outcomeHandler;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();
    private Employee employee;

    @BeforeEach
    void setUp() {
        instanceRepository = mock(ApprovalInstanceRepository.class);
        requestRepository = mock(WfhRequestRepository.class);
        allocationRepository = mock(WfhAllocationRepository.class);
        consumptionRepository = mock(WfhConsumptionRepository.class);
        policyService = mock(WfhPolicyService.class);
        employeeRepository = mock(EmployeeRepository.class);
        attendanceRepository = mock(AttendanceRepository.class);

        outcomeHandler = new WfhApprovalOutcomeHandler(
                instanceRepository,
                requestRepository,
                allocationRepository,
                consumptionRepository,
                policyService,
                employeeRepository,
                attendanceRepository);

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
    @DisplayName("flowType is WORK_FROM_HOME")
    void flowTypeMatches() {
        assertThat(outcomeHandler.flowType()).isEqualTo(ApprovalFlowType.WORK_FROM_HOME);
    }

    @Test
    @DisplayName(
            "onApproved transitions request to APPROVED, logs consumption, and upserts PRESENT attendance (Zero LOP)")
    void onApprovedFullDayCreatesConsumptionAndAttendance() {
        ApprovalInstance instance = mock(ApprovalInstance.class);
        when(instance.getId()).thenReturn(instanceId);
        when(instance.getTenantId()).thenReturn(tenantId);
        when(instance.getSubjectId()).thenReturn(requestId);
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        LocalDate wednesday = LocalDate.of(2026, 11, 4);
        WfhRequest req = new WfhRequest();
        req.setId(requestId);
        req.setTenantId(tenantId);
        req.setEmployeeId(employeeId);
        req.setFromDate(wednesday);
        req.setToDate(wednesday);
        req.setHalfDay(false);
        req.setWorkingDays(BigDecimal.ONE);
        req.setStatus(WfhRequestStatus.PENDING);
        req.setReason("Remote architecture sprint");

        when(requestRepository.findByTenantIdAndId(tenantId, requestId)).thenReturn(Optional.of(req));
        when(requestRepository.existsOverlappingRequestExcluding(
                        eq(tenantId), eq(employeeId), any(), eq(wednesday), eq(wednesday), eq(requestId)))
                .thenReturn(false);

        WfhAllocation allocation = new WfhAllocation();
        allocation.setId(UUID.randomUUID());
        allocation.setTenantId(tenantId);
        allocation.setEmployeeId(employeeId);
        allocation.setEntitlementDays(new BigDecimal("12.00"));

        when(allocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                tenantId, employeeId, wednesday, wednesday))
                .thenReturn(Optional.of(allocation));

        when(attendanceRepository.findByTenantIdAndEmployeeIdAndAttendanceDate(tenantId, employeeId, wednesday))
                .thenReturn(Optional.empty());

        outcomeHandler.onApproved(instanceId, List.of());

        // Verify request approved
        assertThat(req.getStatus()).isEqualTo(WfhRequestStatus.APPROVED);
        verify(requestRepository).save(req);

        // Verify consumption row saved
        verify(consumptionRepository)
                .save(argThat(c -> c.getAllocationId().equals(allocation.getId())
                        && c.getConsumedDays().compareTo(BigDecimal.ONE) == 0
                        && c.getPeriod().equals("2026-11")));

        // Verify Attendance upserted as PRESENT with "Work From Home" remark (Zero LOP)
        verify(attendanceRepository)
                .save(argThat(att -> att.getAttendanceDate().equals(wednesday)
                        && att.getStatus() == AttendanceStatus.PRESENT
                        && "Work From Home".equals(att.getRemarks())));
    }

    @Test
    @DisplayName("onApproved with half-day upserts HALF_DAY attendance")
    void onApprovedHalfDayUpsertsHalfDayAttendance() {
        ApprovalInstance instance = mock(ApprovalInstance.class);
        when(instance.getId()).thenReturn(instanceId);
        when(instance.getTenantId()).thenReturn(tenantId);
        when(instance.getSubjectId()).thenReturn(requestId);
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        LocalDate thursday = LocalDate.of(2026, 11, 5);
        WfhRequest req = new WfhRequest();
        req.setId(requestId);
        req.setTenantId(tenantId);
        req.setEmployeeId(employeeId);
        req.setFromDate(thursday);
        req.setToDate(thursday);
        req.setHalfDay(true);
        req.setHalfDayPeriod("first");
        req.setWorkingDays(new BigDecimal("0.50"));
        req.setStatus(WfhRequestStatus.PENDING);

        when(requestRepository.findByTenantIdAndId(tenantId, requestId)).thenReturn(Optional.of(req));
        when(requestRepository.existsOverlappingRequestExcluding(
                        eq(tenantId), eq(employeeId), any(), eq(thursday), eq(thursday), eq(requestId)))
                .thenReturn(false);

        WfhAllocation allocation = new WfhAllocation();
        allocation.setId(UUID.randomUUID());
        allocation.setTenantId(tenantId);
        allocation.setEmployeeId(employeeId);
        allocation.setEntitlementDays(new BigDecimal("12.00"));

        when(allocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                tenantId, employeeId, thursday, thursday))
                .thenReturn(Optional.of(allocation));

        when(attendanceRepository.findByTenantIdAndEmployeeIdAndAttendanceDate(tenantId, employeeId, thursday))
                .thenReturn(Optional.empty());

        outcomeHandler.onApproved(instanceId, List.of());

        assertThat(req.getStatus()).isEqualTo(WfhRequestStatus.APPROVED);

        // Verify attendance status is HALF_DAY
        verify(attendanceRepository)
                .save(argThat(att -> att.getAttendanceDate().equals(thursday)
                        && att.getStatus() == AttendanceStatus.HALF_DAY
                        && "Work From Home".equals(att.getRemarks())));
    }

    @Test
    @DisplayName("onRejected transitions request to REJECTED without consumption or attendance")
    void onRejectedTransitionsStatusOnly() {
        ApprovalInstance instance = mock(ApprovalInstance.class);
        when(instance.getId()).thenReturn(instanceId);
        when(instance.getTenantId()).thenReturn(tenantId);
        when(instance.getSubjectId()).thenReturn(requestId);
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        WfhRequest req = new WfhRequest();
        req.setId(requestId);
        req.setTenantId(tenantId);
        req.setEmployeeId(employeeId);
        req.setStatus(WfhRequestStatus.PENDING);

        when(requestRepository.findByTenantIdAndId(tenantId, requestId)).thenReturn(Optional.of(req));

        outcomeHandler.onRejected(instanceId, List.of());

        assertThat(req.getStatus()).isEqualTo(WfhRequestStatus.REJECTED);
        verify(requestRepository).save(req);
        verify(consumptionRepository, never()).save(any());
        verify(attendanceRepository, never()).save(any());
    }
}
