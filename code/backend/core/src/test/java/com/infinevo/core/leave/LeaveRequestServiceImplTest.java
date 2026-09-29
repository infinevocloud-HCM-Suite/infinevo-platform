package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.employee.EmployeeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LeaveRequestServiceImpl} (W-16.3, spec section 7).
 */
class LeaveRequestServiceImplTest {

    private LeaveRequestRepository leaveRequestRepository;
    private LeaveRequestDocumentRepository leaveRequestDocumentRepository;
    private LeaveTypeRepository leaveTypeRepository;
    private LeavePolicyRepository leavePolicyRepository;
    private LeaveEligibilityService leaveEligibilityService;
    private LeaveBalanceService leaveBalanceService;
    private WorkingDayCalculator workingDayCalculator;
    private ApprovalService approvalService;
    private ApprovalInstanceRepository approvalInstanceRepository;
    private EmployeeRepository employeeRepository;

    private LeaveRequestServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID leaveTypeId = UUID.randomUUID();

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
        approvalInstanceRepository = mock(ApprovalInstanceRepository.class);
        employeeRepository = mock(EmployeeRepository.class);

        service = new LeaveRequestServiceImpl(
                leaveRequestRepository,
                leaveRequestDocumentRepository,
                leaveTypeRepository,
                leavePolicyRepository,
                leaveEligibilityService,
                leaveBalanceService,
                workingDayCalculator,
                approvalService,
                approvalInstanceRepository,
                employeeRepository);
    }

    private LeaveType createLeaveType(String code, String name) {
        return new LeaveType(
                tenantId,
                code,
                name,
                true,
                LeaveUnit.DAYS,
                true,
                LocalDate.now().minusYears(1),
                null,
                true);
    }

    private LeavePolicy createLeavePolicy(BigDecimal annualDays) {
        LeavePolicy policy = new LeavePolicy();
        policy.setTenantId(tenantId);
        policy.setLeaveTypeId(leaveTypeId);
        policy.setAnnualDays(annualDays);
        policy.setEffectiveFrom(LocalDate.now().minusMonths(1));
        return policy;
    }

    @Test
    @DisplayName("an ineligible leave type is refused")
    void ineligibleTypeRefused() {
        LocalDate from = LocalDate.now().plusDays(2);
        LocalDate to = LocalDate.now().plusDays(4);

        LeaveType type = createLeaveType("AL", "Annual Leave");
        when(leaveTypeRepository.findByTenantIdAndId(tenantId, leaveTypeId)).thenReturn(Optional.of(type));
        when(leaveEligibilityService.isEligible(tenantId, employeeId, leaveTypeId, from))
                .thenReturn(false);

        LeaveApplyRequest req = new LeaveApplyRequest(leaveTypeId, from, to, false, null, "Vacation", null, true);

        assertThatThrownBy(() -> service.createRequest(tenantId, employeeId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not eligible");
    }

    @Test
    @DisplayName("a request beyond the future-booking limit is refused")
    void futureBookingLimitRefused() {
        LocalDate from = LocalDate.now().plusDays(30);
        LocalDate to = LocalDate.now().plusDays(32);

        LeaveType type = createLeaveType("AL", "Annual Leave");
        when(leaveTypeRepository.findByTenantIdAndId(tenantId, leaveTypeId)).thenReturn(Optional.of(type));
        when(leaveEligibilityService.isEligible(tenantId, employeeId, leaveTypeId, from))
                .thenReturn(true);

        LeavePolicy policy = createLeavePolicy(new BigDecimal("20.00"));
        policy.setFutureBookingLimitDays(14); // Limit is 14 days
        when(leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                eq(tenantId), eq(leaveTypeId), eq(from)))
                .thenReturn(Optional.of(policy));

        LeaveApplyRequest req =
                new LeaveApplyRequest(leaveTypeId, from, to, false, null, "Future Vacation", null, true);

        assertThatThrownBy(() -> service.createRequest(tenantId, employeeId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds future booking limit");
    }

    @Test
    @DisplayName("overlapping active requests are refused")
    void overlappingRequestsRefused() {
        LocalDate from = LocalDate.now().plusDays(2);
        LocalDate to = LocalDate.now().plusDays(4);

        LeaveType type = createLeaveType("AL", "Annual Leave");
        when(leaveTypeRepository.findByTenantIdAndId(tenantId, leaveTypeId)).thenReturn(Optional.of(type));
        when(leaveEligibilityService.isEligible(tenantId, employeeId, leaveTypeId, from))
                .thenReturn(true);

        LeavePolicy policy = createLeavePolicy(new BigDecimal("20.00"));
        when(leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                any(), any(), any()))
                .thenReturn(Optional.of(policy));
        when(workingDayCalculator.calculateWorkingDays(any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(new BigDecimal("3.00"));

        LeaveRequest existing = new LeaveRequest(
                tenantId,
                employeeId,
                leaveTypeId,
                from,
                to,
                false,
                null,
                new BigDecimal("3.00"),
                "Existing",
                LeaveRequestStatus.PENDING,
                null,
                false);
        when(leaveRequestRepository.findOverlapping(eq(tenantId), eq(employeeId), eq(from), eq(to), any(), any()))
                .thenReturn(List.of(existing));

        LeaveApplyRequest req = new LeaveApplyRequest(leaveTypeId, from, to, false, null, "Overlap", null, true);

        assertThatThrownBy(() -> service.createRequest(tenantId, employeeId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    @DisplayName("submission requiring documents fails if none attached")
    void mandatoryDocumentCheck() {
        LocalDate from = LocalDate.now().plusDays(2);
        LocalDate to = LocalDate.now().plusDays(4);

        LeaveType type = createLeaveType("SL", "Sick Leave");
        when(leaveTypeRepository.findByTenantIdAndId(tenantId, leaveTypeId)).thenReturn(Optional.of(type));
        when(leaveEligibilityService.isEligible(tenantId, employeeId, leaveTypeId, from))
                .thenReturn(true);

        LeavePolicy policy = createLeavePolicy(new BigDecimal("12.00"));
        policy.setRequiresDocument(true);
        when(leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                any(), any(), any()))
                .thenReturn(Optional.of(policy));

        LeaveApplyRequest req = new LeaveApplyRequest(leaveTypeId, from, to, false, null, "Medical", null, true);

        assertThatThrownBy(() -> service.createRequest(tenantId, employeeId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires supporting documents");
    }

    @Test
    @DisplayName("yearEndLimit refuses when balance would fall below negative limit")
    void yearEndLimitRefusesPastLimit() {
        LocalDate from = LocalDate.now().plusDays(2);
        LocalDate to = LocalDate.now().plusDays(4);

        LeaveType type = createLeaveType("SL", "Sick Leave");
        when(leaveTypeRepository.findByTenantIdAndId(tenantId, leaveTypeId)).thenReturn(Optional.of(type));
        when(leaveEligibilityService.isEligible(tenantId, employeeId, leaveTypeId, from))
                .thenReturn(true);

        LeavePolicy policy = createLeavePolicy(new BigDecimal("12.00"));
        policy.setExceedBalanceMode(ExceedBalanceMode.YEAR_END_LIMIT);
        policy.setExceedBalanceLimitDays(new BigDecimal("2.00")); // can go negative down to -2.00
        when(leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                any(), any(), any()))
                .thenReturn(Optional.of(policy));

        // Remaining balance is 0.00
        LeaveBalanceResponse bal = new LeaveBalanceResponse(
                employeeId,
                leaveTypeId,
                "SL",
                "Sick Leave",
                "2026",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null);
        when(leaveBalanceService.getBalance(tenantId, employeeId, leaveTypeId, from))
                .thenReturn(Optional.of(bal));

        // Request is for 3.00 days -> projected -3.00 < -2.00 limit -> refuses!
        when(workingDayCalculator.calculateWorkingDays(any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(new BigDecimal("3.00"));

        LeaveApplyRequest req = new LeaveApplyRequest(leaveTypeId, from, to, false, null, "Need rest", null, true);

        assertThatThrownBy(() -> service.createRequest(tenantId, employeeId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds allowable negative balance limit");
    }

    @Test
    @DisplayName("noLimit and markAsLOP allow request even with zero balance")
    void noLimitAndMarkAsLopAllowed() {
        LocalDate from = LocalDate.now().plusDays(2);
        LocalDate to = LocalDate.now().plusDays(4);

        LeaveType type = createLeaveType("CL", "Casual Leave");
        when(leaveTypeRepository.findByTenantIdAndId(tenantId, leaveTypeId)).thenReturn(Optional.of(type));
        when(leaveEligibilityService.isEligible(tenantId, employeeId, leaveTypeId, from))
                .thenReturn(true);

        // Test with NO_LIMIT
        LeavePolicy policyNoLimit = createLeavePolicy(new BigDecimal("10.00"));
        policyNoLimit.setExceedBalanceMode(ExceedBalanceMode.NO_LIMIT);
        when(leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                any(), any(), any()))
                .thenReturn(Optional.of(policyNoLimit));
        when(workingDayCalculator.calculateWorkingDays(any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(new BigDecimal("3.00"));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> {
            LeaveRequest r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(approvalService.start(any(ApprovalFlowType.class), any(), any())).thenReturn(UUID.randomUUID());

        LeaveApplyRequest req1 = new LeaveApplyRequest(leaveTypeId, from, to, false, null, "No limit test", null, true);
        LeaveRequestResponse resp1 = service.createRequest(tenantId, employeeId, req1);
        assertThat(resp1.status()).isEqualTo(LeaveRequestStatus.PENDING);

        // Test with MARK_AS_LOP
        LeavePolicy policyLop = createLeavePolicy(new BigDecimal("10.00"));
        policyLop.setExceedBalanceMode(ExceedBalanceMode.MARK_AS_LOP);
        when(leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                any(), any(), any()))
                .thenReturn(Optional.of(policyLop));

        LeaveApplyRequest req2 = new LeaveApplyRequest(leaveTypeId, from, to, false, null, "LOP test", null, true);
        LeaveRequestResponse resp2 = service.createRequest(tenantId, employeeId, req2);
        assertThat(resp2.status()).isEqualTo(LeaveRequestStatus.PENDING);
    }

    @Test
    @DisplayName("cancel on or after from_date is refused")
    void cancelOnOrAfterStartDateRefused() {
        UUID reqId = UUID.randomUUID();
        LocalDate pastOrToday = LocalDate.now();

        LeaveRequest req = new LeaveRequest(
                tenantId,
                employeeId,
                leaveTypeId,
                pastOrToday,
                pastOrToday.plusDays(2),
                false,
                null,
                new BigDecimal("3.00"),
                "Trip",
                LeaveRequestStatus.APPROVED,
                null,
                false);
        when(leaveRequestRepository.findByIdAndTenantId(reqId, tenantId)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.cancel(tenantId, reqId, employeeId, "Change of mind"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be cancelled on or after start date");
    }

    @Test
    @DisplayName("withdraw after a decision is refused")
    void withdrawAfterDecisionRefused() {
        UUID reqId = UUID.randomUUID();
        LocalDate future = LocalDate.now().plusDays(5);

        LeaveRequest req = new LeaveRequest(
                tenantId,
                employeeId,
                leaveTypeId,
                future,
                future.plusDays(2),
                false,
                null,
                new BigDecimal("3.00"),
                "Trip",
                LeaveRequestStatus.APPROVED,
                null,
                false);
        when(leaveRequestRepository.findByIdAndTenantId(reqId, tenantId)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.withdraw(tenantId, reqId, employeeId, "Changed mind"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only PENDING leave requests can be withdrawn");
    }
}
