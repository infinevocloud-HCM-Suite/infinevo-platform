package com.infinevo.hrms.performance;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.org.ReportingLine;
import com.infinevo.core.org.ReportingLineKind;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementation for performance review cycles and manager appraisals (W-106.1).
 */
@Service
@Transactional
public class PerformanceReviewServiceImpl implements PerformanceReviewService {

    private final PerformanceCycleRepository cycleRepository;
    private final PerformanceReviewRepository reviewRepository;
    private final EmployeeRepository employeeRepository;
    private final ReportingLineRepository reportingLineRepository;
    private final EmployeeService employeeService;
    private final PermissionService permissionService;

    public PerformanceReviewServiceImpl(
            PerformanceCycleRepository cycleRepository,
            PerformanceReviewRepository reviewRepository,
            EmployeeRepository employeeRepository,
            ReportingLineRepository reportingLineRepository,
            EmployeeService employeeService,
            PermissionService permissionService) {
        this.cycleRepository = Objects.requireNonNull(cycleRepository, "cycleRepository must not be null");
        this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.reportingLineRepository =
                Objects.requireNonNull(reportingLineRepository, "reportingLineRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
    }

    @Override
    public PerformanceCycleResponse createCycle(CreateCycleRequest request) {
        UUID tenantId = TenantContext.require();
        validateCycleRequest(request);

        PerformanceCycle cycle = new PerformanceCycle(
                tenantId,
                request.name(),
                request.periodStart(),
                request.periodEnd(),
                request.selfReviewDeadline(),
                request.managerReviewDeadline(),
                request.ratingScaleMax(),
                currentActor());

        PerformanceCycle saved = cycleRepository.save(cycle);
        return PerformanceCycleResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PerformanceCycleResponse> listCycles() {
        UUID tenantId = TenantContext.require();
        return cycleRepository.findByTenantIdOrderByPeriodStartDescPeriodEndDesc(tenantId).stream()
                .map(PerformanceCycleResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PerformanceCycleResponse getCycle(UUID cycleId) {
        UUID tenantId = TenantContext.require();
        PerformanceCycle cycle = cycleRepository
                .findByIdAndTenantId(cycleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Performance cycle not found: " + cycleId));
        return PerformanceCycleResponse.from(cycle);
    }

    @Override
    public PerformanceCycleResponse activateCycle(UUID cycleId) {
        UUID tenantId = TenantContext.require();
        PerformanceCycle cycle = cycleRepository
                .findByIdAndTenantId(cycleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Performance cycle not found: " + cycleId));

        cycle.activate(currentActor());

        List<Employee> activeEmployees =
                employeeRepository.findByTenantIdAndDeletedFalseAndStatus(tenantId, EmploymentStatus.ACTIVE);

        for (Employee emp : activeEmployees) {
            UUID managerId = resolveReportingManager(tenantId, emp.getId(), cycle);
            Optional<PerformanceReview> existing =
                    reviewRepository.findByTenantIdAndCycleIdAndEmployeeId(tenantId, cycleId, emp.getId());
            if (existing.isEmpty()) {
                PerformanceReview review =
                        new PerformanceReview(tenantId, cycleId, emp.getId(), managerId, currentActor());
                reviewRepository.save(review);
            }
        }

        PerformanceCycle saved = cycleRepository.save(cycle);
        return PerformanceCycleResponse.from(saved);
    }

    @Override
    public PerformanceCycleResponse closeCycle(UUID cycleId) {
        UUID tenantId = TenantContext.require();
        PerformanceCycle cycle = cycleRepository
                .findByIdAndTenantId(cycleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Performance cycle not found: " + cycleId));

        cycle.close(currentActor());
        PerformanceCycle saved = cycleRepository.save(cycle);
        return PerformanceCycleResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CycleSummaryResponse getCycleSummary(UUID cycleId) {
        UUID tenantId = TenantContext.require();
        PerformanceCycle cycle = cycleRepository
                .findByIdAndTenantId(cycleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Performance cycle not found: " + cycleId));

        long total = reviewRepository.countByTenantIdAndCycleId(tenantId, cycleId);
        long completed = reviewRepository.countByTenantIdAndCycleIdAndStatus(
                tenantId, cycleId, PerformanceReviewStatus.COMPLETED);
        long pendingSelf = reviewRepository.countByTenantIdAndCycleIdAndStatus(
                tenantId, cycleId, PerformanceReviewStatus.PENDING_SELF_REVIEW);
        long pendingMgr = reviewRepository.countByTenantIdAndCycleIdAndStatus(
                tenantId, cycleId, PerformanceReviewStatus.PENDING_MANAGER_REVIEW);
        double completionPct = total > 0 ? ((double) completed / total) * 100.0 : 0.0;

        List<PerformanceReview> reviews = reviewRepository.findByTenantIdAndCycleId(tenantId, cycleId);
        Double avgRating = reviews.stream()
                .filter(r -> r.getManagerRating() != null)
                .mapToInt(PerformanceReview::getManagerRating)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);

        return new CycleSummaryResponse(
                cycle.getId(),
                cycle.getName(),
                cycle.getStatus(),
                total,
                completed,
                pendingSelf,
                pendingMgr,
                Math.round(completionPct * 100.0) / 100.0,
                avgRating != null ? Math.round(avgRating * 100.0) / 100.0 : null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PerformanceReviewResponse> getMyReviews() {
        UUID tenantId = TenantContext.require();
        EmployeeResponse current = requireCurrentEmployee();
        List<PerformanceReview> reviews =
                reviewRepository.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, current.id());
        return mapReviewsToResponses(tenantId, reviews);
    }

    @Override
    public PerformanceReviewResponse submitSelfReview(UUID reviewId, SelfReviewRequest request) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse current = requireCurrentEmployee();

        PerformanceReview review = reviewRepository
                .findByIdAndTenantId(reviewId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Performance review not found: " + reviewId));

        if (!review.getEmployeeId().equals(current.id())) {
            throw new AccessDeniedException("You can only submit self-review for your own appraisal record");
        }

        PerformanceCycle cycle = cycleRepository
                .findByIdAndTenantId(review.getCycleId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Performance cycle not found: " + review.getCycleId()));

        if (cycle.getStatus() != PerformanceCycleStatus.ACTIVE) {
            throw new IllegalStateException("Reviews can only be submitted for ACTIVE review cycles");
        }

        if (review.getStatus() != PerformanceReviewStatus.PENDING_SELF_REVIEW) {
            throw new IllegalStateException("Self review has already been submitted for this appraisal");
        }

        if (request.selfRating() == null
                || request.selfRating() < 1
                || request.selfRating() > cycle.getRatingScaleMax()) {
            throw new IllegalArgumentException("Self rating must be between 1 and " + cycle.getRatingScaleMax());
        }

        review.submitSelfReview(request.selfRating(), request.comments(), currentActor());
        PerformanceReview saved = reviewRepository.save(review);
        return mapSingleReviewToResponse(tenantId, saved, cycle.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PerformanceReviewResponse> getTeamReviews() {
        UUID tenantId = TenantContext.require();
        EmployeeResponse current = requireCurrentEmployee();
        List<PerformanceReview> reviews =
                reviewRepository.findByTenantIdAndManagerIdOrderByCreatedAtDesc(tenantId, current.id());
        return mapReviewsToResponses(tenantId, reviews);
    }

    @Override
    public PerformanceReviewResponse submitManagerReview(UUID reviewId, ManagerReviewRequest request) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse current = requireCurrentEmployee();

        PerformanceReview review = reviewRepository
                .findByIdAndTenantId(reviewId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Performance review not found: " + reviewId));

        boolean isAssignedManager = review.getManagerId().equals(current.id());
        boolean isHRAdmin = permissionService.holds("hrms.performance.manage");
        if (!isAssignedManager && !isHRAdmin) {
            throw new AccessDeniedException(
                    "Only the assigned reporting manager or HR administrator can submit manager rating");
        }

        PerformanceCycle cycle = cycleRepository
                .findByIdAndTenantId(review.getCycleId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Performance cycle not found: " + review.getCycleId()));

        if (cycle.getStatus() != PerformanceCycleStatus.ACTIVE) {
            throw new IllegalStateException("Manager reviews can only be submitted for ACTIVE review cycles");
        }

        if (request.managerRating() == null
                || request.managerRating() < 1
                || request.managerRating() > cycle.getRatingScaleMax()) {
            throw new IllegalArgumentException("Manager rating must be between 1 and " + cycle.getRatingScaleMax());
        }

        review.submitManagerReview(request.managerRating(), request.comments(), currentActor());
        PerformanceReview saved = reviewRepository.save(review);
        return mapSingleReviewToResponse(tenantId, saved, cycle.getName());
    }

    private UUID resolveReportingManager(UUID tenantId, UUID employeeId, PerformanceCycle cycle) {
        Optional<ReportingLine> openLine =
                reportingLineRepository.findCurrentOpenLine(tenantId, employeeId, ReportingLineKind.PRIMARY);
        if (openLine.isPresent() && openLine.get().getManager() != null) {
            return openLine.get().getManager().getId();
        }

        List<ReportingLine> activeLines = reportingLineRepository.findActiveLines(
                tenantId, employeeId, ReportingLineKind.PRIMARY, cycle.getPeriodStart());
        if (!activeLines.isEmpty() && activeLines.get(0).getManager() != null) {
            return activeLines.get(0).getManager().getId();
        }

        // Fall back to employee themself if unassigned
        return employeeId;
    }

    private void validateCycleRequest(CreateCycleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Cycle name is required");
        }
        if (request.periodStart() == null || request.periodEnd() == null) {
            throw new IllegalArgumentException("periodStart and periodEnd are required");
        }
        if (request.periodStart().isAfter(request.periodEnd())) {
            throw new IllegalArgumentException("periodEnd must be on or after periodStart");
        }
        if (request.selfReviewDeadline() == null || request.managerReviewDeadline() == null) {
            throw new IllegalArgumentException("Self review and manager review deadlines are required");
        }
        if (request.selfReviewDeadline().isAfter(request.managerReviewDeadline())) {
            throw new IllegalArgumentException("selfReviewDeadline must not be after managerReviewDeadline");
        }
        if (request.ratingScaleMax() != null && (request.ratingScaleMax() < 3 || request.ratingScaleMax() > 10)) {
            throw new IllegalArgumentException("ratingScaleMax must be between 3 and 10");
        }
    }

    private List<PerformanceReviewResponse> mapReviewsToResponses(UUID tenantId, List<PerformanceReview> reviews) {
        if (reviews.isEmpty()) {
            return List.of();
        }
        Set<UUID> empIds = new HashSet<>();
        Set<UUID> cycleIds = new HashSet<>();
        for (PerformanceReview r : reviews) {
            empIds.add(r.getEmployeeId());
            empIds.add(r.getManagerId());
            cycleIds.add(r.getCycleId());
        }
        Map<UUID, Employee> empMap = employeeRepository.findByTenantIdAndIdIn(tenantId, empIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e, (a, b) -> a));
        Map<UUID, String> cycleMap = cycleRepository.findAllById(cycleIds).stream()
                .collect(Collectors.toMap(PerformanceCycle::getId, PerformanceCycle::getName, (a, b) -> a));

        return reviews.stream()
                .map(r -> {
                    String cycleName = cycleMap.getOrDefault(r.getCycleId(), "Review Cycle");
                    Employee emp = empMap.get(r.getEmployeeId());
                    Employee mgr = empMap.get(r.getManagerId());
                    String empName = formatEmployeeName(emp);
                    String empNumber = emp != null ? emp.getEmployeeNumber() : "";
                    String mgrName = formatEmployeeName(mgr);
                    return PerformanceReviewResponse.from(r, cycleName, empName, empNumber, mgrName);
                })
                .toList();
    }

    private PerformanceReviewResponse mapSingleReviewToResponse(
            UUID tenantId, PerformanceReview review, String cycleName) {
        Set<UUID> empIds = Set.of(review.getEmployeeId(), review.getManagerId());
        Map<UUID, Employee> empMap = employeeRepository.findByTenantIdAndIdIn(tenantId, empIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e, (a, b) -> a));

        Employee emp = empMap.get(review.getEmployeeId());
        Employee mgr = empMap.get(review.getManagerId());
        String empName = formatEmployeeName(emp);
        String empNumber = emp != null ? emp.getEmployeeNumber() : "";
        String mgrName = formatEmployeeName(mgr);

        return PerformanceReviewResponse.from(review, cycleName, empName, empNumber, mgrName);
    }

    private String formatEmployeeName(Employee e) {
        if (e == null) {
            return "Unknown";
        }
        String first = e.getFirstName() != null ? e.getFirstName() : "";
        String last = e.getLastName() != null ? e.getLastName() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? "Unknown" : full;
    }

    private EmployeeResponse requireCurrentEmployee() {
        return employeeService
                .currentEmployee()
                .orElseThrow(() -> new AccessDeniedException("No employee profile linked to current user account"));
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return "system";
        }
        String name = auth.getName();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}
