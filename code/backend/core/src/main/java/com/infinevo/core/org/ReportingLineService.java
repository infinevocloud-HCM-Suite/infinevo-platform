package com.infinevo.core.org;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing reporting lines and resolving manager chains (W-14.2).
 */
@Service
public class ReportingLineService {

    private static final int MAX_CHAIN_DEPTH = 50;

    private final ReportingLineRepository repository;
    private final EmployeeRepository employeeRepository;

    public ReportingLineService(ReportingLineRepository repository, EmployeeRepository employeeRepository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
    }

    /**
     * Assigns or updates a reporting line for an employee.
     */
    @Transactional
    public ReportingLineResponse putReportingLine(UUID employeeId, ReportingLineRequest request) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        if (employeeId.equals(request.managerId())) {
            throw new IllegalArgumentException("Self-management refused: an employee cannot report to themselves");
        }

        Employee employee = employeeRepository
                .findById(employeeId)
                .filter(e -> e.getTenantId().equals(tenantId) && !e.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found in tenant: " + employeeId));

        Employee manager = employeeRepository
                .findById(request.managerId())
                .filter(m -> m.getTenantId().equals(tenantId) && !m.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Manager not found in tenant: " + request.managerId()));

        if (manager.getStatus() != EmploymentStatus.ACTIVE) {
            throw new IllegalArgumentException("Manager must be an active employee");
        }

        ReportingLineKind kind = request.resolvedKind();
        LocalDate effectiveFrom = request.effectiveFrom();
        String actor = currentActor();

        // Cycle detection: walk chain above managerId to check if employeeId is reached (including future lines)
        detectCycle(tenantId, employeeId, manager.getId(), effectiveFrom);

        // Decision 1: Single primary manager in force at a time
        if (kind == ReportingLineKind.PRIMARY) {
            List<ReportingLine> existingLines =
                    repository.findOpenOrFutureLines(tenantId, employeeId, ReportingLineKind.PRIMARY, LocalDate.EPOCH);
            for (ReportingLine existing : existingLines) {
                if (existing.getEffectiveTo() == null
                        || !existing.getEffectiveTo().isBefore(effectiveFrom)) {
                    existing.setEffectiveTo(effectiveFrom.minusDays(1));
                    existing.setUpdatedBy(actor);
                    repository.save(existing);
                }
            }
        }

        ReportingLine line = new ReportingLine(tenantId, employee, manager, kind, effectiveFrom, actor);
        if (request.effectiveTo() != null) {
            line.setEffectiveTo(request.effectiveTo());
        }

        ReportingLine saved = repository.save(line);
        return ReportingLineResponse.from(saved);
    }

    /**
     * Resolves the ordered chain of managers above an employee as of a specific date (W-14.2, spec section 3).
     *
     * @param employeeId the target employee
     * @param asOf       the effective date to evaluate (required)
     * @return ordered list of managers (immediate manager first, top-most last)
     */
    @Transactional(readOnly = true)
    public List<Employee> chainAbove(UUID employeeId, LocalDate asOf) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(asOf, "asOf date must not be null");

        List<Employee> chain = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        visited.add(employeeId);

        UUID currentEmpId = employeeId;
        int depth = 0;

        while (depth < MAX_CHAIN_DEPTH) {
            List<ReportingLine> lines =
                    repository.findActiveLines(tenantId, currentEmpId, ReportingLineKind.PRIMARY, asOf);
            if (lines.isEmpty()) {
                break;
            }
            Employee manager = lines.get(0).getManager();
            if (manager.isDeleted() || manager.getStatus() != EmploymentStatus.ACTIVE) {
                break;
            }
            if (visited.contains(manager.getId())) {
                // Prevent infinite loop if data inconsistency exists
                break;
            }
            chain.add(manager);
            visited.add(manager.getId());
            currentEmpId = manager.getId();
            depth++;
        }

        return chain;
    }

    /**
     * Gets active reporting lines for an employee as of a date.
     */
    @Transactional(readOnly = true)
    public List<ReportingLineResponse> getReportingLines(UUID employeeId, LocalDate asOf) {
        UUID tenantId = TenantContext.require();
        LocalDate targetDate = asOf != null ? asOf : LocalDate.now();

        List<ReportingLine> lines = repository.findActiveLines(tenantId, employeeId, null, targetDate);
        return lines.stream().map(ReportingLineResponse::from).toList();
    }

    /**
     * Gets the manager chain for an employee as of a date.
     */
    @Transactional(readOnly = true)
    public List<ReportingLineResponse> getManagerChain(UUID employeeId, LocalDate asOf) {
        LocalDate targetDate = asOf != null ? asOf : LocalDate.now();
        List<Employee> managers = chainAbove(employeeId, targetDate);
        UUID tenantId = TenantContext.require();

        List<ReportingLineResponse> result = new ArrayList<>();
        UUID currentEmpId = employeeId;
        for (Employee manager : managers) {
            List<ReportingLine> lines =
                    repository.findActiveLines(tenantId, currentEmpId, ReportingLineKind.PRIMARY, targetDate);
            if (!lines.isEmpty()) {
                result.add(ReportingLineResponse.from(lines.get(0)));
            } else {
                result.add(new ReportingLineResponse(
                        null,
                        currentEmpId,
                        manager.getId(),
                        manager.getFirstName() + (manager.getLastName() != null ? " " + manager.getLastName() : ""),
                        ReportingLineKind.PRIMARY,
                        targetDate,
                        null));
            }
            currentEmpId = manager.getId();
        }
        return result;
    }

    /**
     * Walks upward starting from candidateManagerId to detect if employeeId is reached (now or in future).
     */
    private void detectCycle(UUID tenantId, UUID employeeId, UUID candidateManagerId, LocalDate effectiveFrom) {
        Set<UUID> visited = new HashSet<>();
        visited.add(employeeId);

        java.util.Queue<UUID> queue = new java.util.ArrayDeque<>();
        queue.add(candidateManagerId);

        int count = 0;
        while (!queue.isEmpty() && count < MAX_CHAIN_DEPTH * 10) {
            UUID currentId = queue.poll();
            if (currentId == null) {
                continue;
            }
            if (currentId.equals(employeeId)) {
                throw new IllegalArgumentException(
                        "Reporting line cycle detected: assigning this manager creates a loop");
            }
            if (!visited.add(currentId)) {
                continue;
            }
            count++;

            List<ReportingLine> lines =
                    repository.findOpenOrFutureLines(tenantId, currentId, ReportingLineKind.PRIMARY, effectiveFrom);
            for (ReportingLine line : lines) {
                if (line.getManager() != null) {
                    UUID mgrId = line.getManager().getId();
                    if (mgrId.equals(employeeId)) {
                        throw new IllegalArgumentException(
                                "Reporting line cycle detected: assigning this manager creates a loop");
                    }
                    if (!visited.contains(mgrId)) {
                        queue.add(mgrId);
                    }
                }
            }
        }
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            String name = auth.getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return "system";
    }
}
