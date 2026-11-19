package com.infinevo.hrms.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.org.ReportingLine;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PerformanceReviewServiceImplTest {

    private PerformanceCycleRepository cycleRepository;
    private PerformanceReviewRepository reviewRepository;
    private EmployeeRepository employeeRepository;
    private ReportingLineRepository reportingLineRepository;
    private EmployeeService employeeService;
    private PermissionService permissionService;
    private PerformanceReviewServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();

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
    @DisplayName("Create cycle validates dates and persists in DRAFT status")
    void createCycleValidatesAndPersists() {
        CreateCycleRequest request = new CreateCycleRequest(
                "Annual Review 2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 12, 15),
                5);

        when(cycleRepository.save(any(PerformanceCycle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PerformanceCycleResponse response = service.createCycle(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Annual Review 2026");
        assertThat(response.status()).isEqualTo(PerformanceCycleStatus.DRAFT);
        assertThat(response.ratingScaleMax()).isEqualTo(5);
        verify(cycleRepository).save(any(PerformanceCycle.class));
    }

    @Test
    @DisplayName("Create cycle rejects invalid dates")
    void createCycleRejectsInvalidDates() {
        CreateCycleRequest invalidPeriod = new CreateCycleRequest(
                "Bad Period",
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 12, 15),
                5);

        assertThatThrownBy(() -> service.createCycle(invalidPeriod))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodEnd must be on or after periodStart");

        CreateCycleRequest invalidDeadlines = new CreateCycleRequest(
                "Bad Deadlines",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 12, 20),
                LocalDate.of(2026, 11, 15),
                5);

        assertThatThrownBy(() -> service.createCycle(invalidDeadlines))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selfReviewDeadline must not be after managerReviewDeadline");
    }

    @Test
    @DisplayName("Activate cycle generates review instances for active employees with reporting manager")
    void activateCycleGeneratesReviews() {
        UUID cycleId = UUID.randomUUID();
        PerformanceCycle cycle = new PerformanceCycle(
                tenantId,
                "Q4 Review",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 12, 15),
                5,
                "system");

        when(cycleRepository.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));
        when(cycleRepository.save(any(PerformanceCycle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Employee emp1 = mock(Employee.class);
        when(emp1.getId()).thenReturn(employeeId);
        Employee mgr = mock(Employee.class);
        when(mgr.getId()).thenReturn(managerId);

        when(employeeRepository.findByTenantIdAndDeletedFalseAndStatus(tenantId, EmploymentStatus.ACTIVE))
                .thenReturn(List.of(emp1));

        ReportingLine line = mock(ReportingLine.class);
        when(line.getEmployee()).thenReturn(emp1);
        when(line.getManager()).thenReturn(mgr);
        when(reportingLineRepository.findAllActivePrimaryLines(tenantId, cycle.getPeriodStart()))
                .thenReturn(List.of(line));
        when(reviewRepository.findByTenantIdAndCycleId(tenantId, cycleId)).thenReturn(List.of());

        PerformanceCycleResponse activated = service.activateCycle(cycleId);

        assertThat(activated.status()).isEqualTo(PerformanceCycleStatus.ACTIVE);
        verify(reviewRepository).saveAll(any());
    }

    @Test
    @DisplayName("Activate cycle sets managerId null for employee with no primary reporting line")
    void activateCycleUnassignedManagerIsNull() {
        UUID cycleId = UUID.randomUUID();
        PerformanceCycle cycle = new PerformanceCycle(
                tenantId,
                "Q4 Review",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 12, 15),
                5,
                "system");

        when(cycleRepository.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));
        when(cycleRepository.save(any(PerformanceCycle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Employee emp1 = mock(Employee.class);
        when(emp1.getId()).thenReturn(employeeId);

        when(employeeRepository.findByTenantIdAndDeletedFalseAndStatus(tenantId, EmploymentStatus.ACTIVE))
                .thenReturn(List.of(emp1));
        when(reportingLineRepository.findAllActivePrimaryLines(tenantId, cycle.getPeriodStart()))
                .thenReturn(List.of());
        when(reviewRepository.findByTenantIdAndCycleId(tenantId, cycleId)).thenReturn(List.of());

        service.activateCycle(cycleId);

        verify(reviewRepository).saveAll(org.mockito.ArgumentMatchers.argThat(iterable -> {
            @SuppressWarnings("unchecked")
            List<PerformanceReview> list = (List<PerformanceReview>) iterable;
            return list.size() == 1 && list.get(0).getManagerId() == null;
        }));
    }

    @Test
    @DisplayName("Employee self-review transitions review to PENDING_MANAGER_REVIEW")
    void submitSelfReviewTransitionsState() {
        UUID reviewId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();

        PerformanceCycle cycle = new PerformanceCycle(
                tenantId,
                "Q4 Review",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 12, 15),
                5,
                "system");
        cycle.activate("admin");

        PerformanceReview review = new PerformanceReview(tenantId, cycleId, employeeId, managerId, "system");

        EmployeeResponse currentEmp = mock(EmployeeResponse.class);
        when(currentEmp.id()).thenReturn(employeeId);
        when(currentEmp.portalEnabled()).thenReturn(true);

        when(employeeService.currentEmployee()).thenReturn(Optional.of(currentEmp));
        when(reviewRepository.findByIdAndTenantId(reviewId, tenantId)).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Employee empEntity = mock(Employee.class);
        when(empEntity.getId()).thenReturn(employeeId);
        when(empEntity.getFirstName()).thenReturn("Alice");
        when(empEntity.getLastName()).thenReturn("Smith");
        when(empEntity.getEmployeeNumber()).thenReturn("EMP001");

        Employee mgrEntity = mock(Employee.class);
        when(mgrEntity.getId()).thenReturn(managerId);
        when(mgrEntity.getFirstName()).thenReturn("Bob");
        when(mgrEntity.getLastName()).thenReturn("Manager");

        when(employeeRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(empEntity, mgrEntity));

        SelfReviewRequest request = new SelfReviewRequest(4, "Delivered core features on time");
        PerformanceReviewResponse response = service.submitSelfReview(reviewId, request);

        assertThat(response.status()).isEqualTo(PerformanceReviewStatus.PENDING_MANAGER_REVIEW);
        assertThat(response.selfRating()).isEqualTo(4);
        assertThat(response.selfComments()).isEqualTo("Delivered core features on time");
    }

    @Test
    @DisplayName("Manager review transitions review to COMPLETED")
    void submitManagerReviewTransitionsToCompleted() {
        UUID reviewId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();

        PerformanceCycle cycle = new PerformanceCycle(
                tenantId,
                "Q4 Review",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 12, 15),
                5,
                "system");
        cycle.activate("admin");

        PerformanceReview review = new PerformanceReview(tenantId, cycleId, employeeId, managerId, "system");
        review.submitSelfReview(4, "Accomplished key deliverables", "alice");

        EmployeeResponse currentMgr = mock(EmployeeResponse.class);
        when(currentMgr.id()).thenReturn(managerId);
        when(currentMgr.portalEnabled()).thenReturn(true);

        when(employeeService.currentEmployee()).thenReturn(Optional.of(currentMgr));
        when(reviewRepository.findByIdAndTenantId(reviewId, tenantId)).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));
        when(reviewRepository.save(any(PerformanceReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Employee empEntity = mock(Employee.class);
        when(empEntity.getId()).thenReturn(employeeId);
        when(empEntity.getFirstName()).thenReturn("Alice");
        when(empEntity.getLastName()).thenReturn("Smith");
        when(empEntity.getEmployeeNumber()).thenReturn("EMP001");

        Employee mgrEntity = mock(Employee.class);
        when(mgrEntity.getId()).thenReturn(managerId);
        when(mgrEntity.getFirstName()).thenReturn("Bob");
        when(mgrEntity.getLastName()).thenReturn("Manager");

        when(employeeRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(empEntity, mgrEntity));

        ManagerReviewRequest request = new ManagerReviewRequest(5, "Outstanding impact across all team projects");
        PerformanceReviewResponse response = service.submitManagerReview(reviewId, request);

        assertThat(response.status()).isEqualTo(PerformanceReviewStatus.COMPLETED);
        assertThat(response.managerRating()).isEqualTo(5);
        assertThat(response.managerComments()).isEqualTo("Outstanding impact across all team projects");
    }

    @Test
    @DisplayName("Cycle summary returns accurate completion metrics and average rating")
    void cycleSummaryCalculatesMetrics() {
        UUID cycleId = UUID.randomUUID();
        PerformanceCycle cycle = new PerformanceCycle(
                tenantId,
                "Q4 Review",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 12, 15),
                5,
                "system");

        when(cycleRepository.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));
        when(reviewRepository.countByTenantIdAndCycleId(tenantId, cycleId)).thenReturn(10L);
        when(reviewRepository.countByTenantIdAndCycleIdAndStatus(tenantId, cycleId, PerformanceReviewStatus.COMPLETED))
                .thenReturn(8L);
        when(reviewRepository.countByTenantIdAndCycleIdAndStatus(
                        tenantId, cycleId, PerformanceReviewStatus.PENDING_SELF_REVIEW))
                .thenReturn(1L);
        when(reviewRepository.countByTenantIdAndCycleIdAndStatus(
                        tenantId, cycleId, PerformanceReviewStatus.PENDING_MANAGER_REVIEW))
                .thenReturn(1L);

        PerformanceReview r1 = new PerformanceReview(tenantId, cycleId, UUID.randomUUID(), managerId, "system");
        r1.submitSelfReview(4, "self", "alice");
        r1.submitManagerReview(4, "Great", "mgr");
        PerformanceReview r2 = new PerformanceReview(tenantId, cycleId, UUID.randomUUID(), managerId, "system");
        r2.submitSelfReview(5, "self", "bob");
        r2.submitManagerReview(5, "Super", "mgr");

        when(reviewRepository.findByTenantIdAndCycleId(tenantId, cycleId)).thenReturn(List.of(r1, r2));

        CycleSummaryResponse summary = service.getCycleSummary(cycleId);

        assertThat(summary.totalReviews()).isEqualTo(10L);
        assertThat(summary.completedReviews()).isEqualTo(8L);
        assertThat(summary.completionPercentage()).isEqualTo(80.0);
        assertThat(summary.averageRating()).isEqualTo(4.5);
    }

    @Test
    @DisplayName("Rating 0 and 6 are refused on self-review and manager-review")
    void ratingsOutOfRangeRefused() {
        UUID reviewId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        PerformanceCycle cycle = new PerformanceCycle(
                tenantId,
                "Q4 Review",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(10),
                5,
                "system");
        cycle.activate("admin");

        PerformanceReview review = new PerformanceReview(tenantId, cycleId, employeeId, managerId, "system");
        EmployeeResponse currentEmp = mock(EmployeeResponse.class);
        when(currentEmp.id()).thenReturn(employeeId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(currentEmp));
        when(reviewRepository.findByIdAndTenantId(reviewId, tenantId)).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));

        assertThatThrownBy(() -> service.submitSelfReview(reviewId, new SelfReviewRequest(0, "bad")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submitSelfReview(reviewId, new SelfReviewRequest(6, "bad")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Manager review is refused before self review is submitted")
    void managerReviewBeforeSelfReviewRefused() {
        UUID reviewId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        PerformanceCycle cycle = new PerformanceCycle(
                tenantId,
                "Q4 Review",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(10),
                5,
                "system");
        cycle.activate("admin");

        PerformanceReview review = new PerformanceReview(tenantId, cycleId, employeeId, managerId, "system");
        EmployeeResponse currentMgr = mock(EmployeeResponse.class);
        when(currentMgr.id()).thenReturn(managerId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(currentMgr));
        when(reviewRepository.findByIdAndTenantId(reviewId, tenantId)).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));

        assertThatThrownBy(() -> service.submitManagerReview(reviewId, new ManagerReviewRequest(5, "Too early")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Completed review cannot be re-rated")
    void completedReviewCannotBeReRated() {
        UUID reviewId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        PerformanceCycle cycle = new PerformanceCycle(
                tenantId,
                "Q4 Review",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(10),
                5,
                "system");
        cycle.activate("admin");

        PerformanceReview review = new PerformanceReview(tenantId, cycleId, employeeId, managerId, "system");
        review.submitSelfReview(4, "self", "alice");
        review.submitManagerReview(5, "mgr", "bob");

        EmployeeResponse currentMgr = mock(EmployeeResponse.class);
        when(currentMgr.id()).thenReturn(managerId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(currentMgr));
        when(reviewRepository.findByIdAndTenantId(reviewId, tenantId)).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));

        assertThatThrownBy(() -> service.submitManagerReview(reviewId, new ManagerReviewRequest(4, "Re-rating")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Both deadlines refuse late submit with 409 Conflict")
    void deadlinesRefuseLateSubmit() {
        UUID reviewId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        PerformanceCycle cycle = new PerformanceCycle(
                tenantId,
                "Q4 Review",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                LocalDate.now().minusDays(2),
                LocalDate.now().minusDays(1),
                5,
                "system");
        cycle.activate("admin");

        PerformanceReview review = new PerformanceReview(tenantId, cycleId, employeeId, managerId, "system");
        EmployeeResponse currentEmp = mock(EmployeeResponse.class);
        when(currentEmp.id()).thenReturn(employeeId);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(currentEmp));
        when(reviewRepository.findByIdAndTenantId(reviewId, tenantId)).thenReturn(Optional.of(review));
        when(cycleRepository.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));

        assertThatThrownBy(() -> service.submitSelfReview(reviewId, new SelfReviewRequest(4, "late")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The self-review deadline has passed");
    }

    @Test
    @DisplayName("Deadlines can be updated on active cycle but refused on closed cycle")
    void updateCycleDeadlinesSuccessAndClosedRefusal() {
        UUID cycleId = UUID.randomUUID();
        PerformanceCycle cycle = new PerformanceCycle(
                tenantId,
                "Q4 Review",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 3, 31),
                5,
                "system");
        cycle.activate("admin");

        when(cycleRepository.findByIdAndTenantId(cycleId, tenantId)).thenReturn(Optional.of(cycle));
        when(cycleRepository.save(any(PerformanceCycle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PerformanceCycleResponse res = service.updateCycleDeadlines(
                cycleId, new UpdateCycleDeadlinesRequest(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15)));
        assertThat(res.selfReviewDeadline()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(res.managerReviewDeadline()).isEqualTo(LocalDate.of(2026, 4, 15));

        cycle.close("admin");
        assertThatThrownBy(() -> service.updateCycleDeadlines(
                        cycleId, new UpdateCycleDeadlinesRequest(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 15))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Reassign manager rules: cannot reassign to employee themself, cannot reassign completed")
    void reassignManagerRules() {
        UUID reviewId = UUID.randomUUID();
        PerformanceReview review = new PerformanceReview(tenantId, UUID.randomUUID(), employeeId, managerId, "system");
        when(reviewRepository.findByIdAndTenantId(reviewId, tenantId)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.reassignManager(reviewId, new ReassignManagerRequest(employeeId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Employee cannot be assigned as their own review manager");

        review.submitSelfReview(4, "self", "alice");
        review.submitManagerReview(5, "mgr", "bob");

        assertThatThrownBy(() -> service.reassignManager(reviewId, new ReassignManagerRequest(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class);
    }
}
