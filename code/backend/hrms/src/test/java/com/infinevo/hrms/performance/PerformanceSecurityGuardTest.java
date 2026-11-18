package com.infinevo.hrms.performance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class PerformanceSecurityGuardTest {

    private PerformanceCycleRepository cycleRepository;
    private PerformanceReviewRepository reviewRepository;
    private EmployeeRepository employeeRepository;
    private ReportingLineRepository reportingLineRepository;
    private EmployeeService employeeService;
    private PermissionService permissionService;
    private PerformanceReviewServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeA = UUID.randomUUID();
    private final UUID employeeB = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID cycleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        cycleRepository = mock(PerformanceCycleRepository.class);
        reviewRepository = mock(PerformanceReviewRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        reportingLineRepository = mock(ReportingLineRepository.class);
        employeeService = mock(EmployeeService.class);
        permissionService = mock(PermissionService.class);

        service = new PerformanceReviewServiceImpl(
                cycleRepository,
                reviewRepository,
                employeeRepository,
                reportingLineRepository,
                employeeService,
                permissionService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Employee cannot submit self-review for another employee's appraisal record")
    void employeeCannotSubmitSelfReviewForAnotherEmployee() {
        UUID reviewId = UUID.randomUUID();
        PerformanceReview reviewForB = new PerformanceReview(tenantId, cycleId, employeeB, managerId, "system");

        EmployeeResponse callerA = mock(EmployeeResponse.class);
        when(callerA.id()).thenReturn(employeeA);
        when(callerA.portalEnabled()).thenReturn(true);

        when(employeeService.currentEmployee()).thenReturn(Optional.of(callerA));
        when(reviewRepository.findByIdAndTenantId(reviewId, tenantId)).thenReturn(Optional.of(reviewForB));

        assertThatThrownBy(() -> service.submitSelfReview(reviewId, new SelfReviewRequest(4, "My self review")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You can only submit self-review for your own appraisal record");
    }

    @Test
    @DisplayName("Non-manager cannot submit manager evaluation without hrms.performance.manage action")
    void nonManagerCannotSubmitManagerReviewWithoutManagePermission() {
        UUID reviewId = UUID.randomUUID();
        UUID assignedManagerId = UUID.randomUUID();
        UUID unauthorizedCallerId = UUID.randomUUID();

        PerformanceReview review = new PerformanceReview(tenantId, cycleId, employeeA, assignedManagerId, "system");

        EmployeeResponse unauthorizedCaller = mock(EmployeeResponse.class);
        when(unauthorizedCaller.id()).thenReturn(unauthorizedCallerId);
        when(unauthorizedCaller.portalEnabled()).thenReturn(true);

        when(employeeService.currentEmployee()).thenReturn(Optional.of(unauthorizedCaller));
        when(reviewRepository.findByIdAndTenantId(reviewId, tenantId)).thenReturn(Optional.of(review));
        when(permissionService.holds("hrms.performance.manage")).thenReturn(false);

        assertThatThrownBy(() -> service.submitManagerReview(
                        reviewId, new ManagerReviewRequest(3, "Attempting to rate non-direct report")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(
                        "Only the assigned reporting manager or HR administrator can submit manager rating");
    }

    @Test
    @DisplayName("Review submission rejected if cycle is not ACTIVE")
    void reviewRejectedIfCycleIsNotActive() {
        UUID reviewId = UUID.randomUUID();
        PerformanceReview review = new PerformanceReview(tenantId, cycleId, employeeA, managerId, "system");

        PerformanceCycle closedCycle = new PerformanceCycle(
                tenantId,
                "Closed Review",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 3, 31),
                5,
                "admin");
        closedCycle.activate("admin");
        closedCycle.close("admin");

        EmployeeResponse callerA = mock(EmployeeResponse.class);
        when(callerA.id()).thenReturn(employeeA);
        when(callerA.portalEnabled()).thenReturn(true);

        when(employeeService.currentEmployee()).thenReturn(Optional.of(callerA));
        when(reviewRepository.findByIdAndTenantId(reviewId, tenantId)).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(closedCycle));

        assertThatThrownBy(() -> service.submitSelfReview(reviewId, new SelfReviewRequest(4, "Too late")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reviews can only be submitted for ACTIVE review cycles");
    }
}
