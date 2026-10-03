package com.infinevo.hrms.timesheet;

import com.infinevo.core.approval.ApproverKind;
import com.infinevo.core.approval.ApproverResolver;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.org.ReportingLineService;
import com.infinevo.hrms.project.Project;
import com.infinevo.hrms.project.ProjectRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves a {@code PROJECT_MANAGER} approval step to the manager of the project named on the step (W-42.3 §4).
 *
 * <p>{@code core} cannot know about projects, so it asks through {@link ApproverResolver}; {@code contextRef} is the
 * project id as a string (W-42.2 puts the step's item reference there).
 *
 * <ol>
 *   <li>No such project, or none that is live, resolves to nobody.
 *   <li>A project with no manager resolves to nobody: the step is unassigned, and an administrator holding
 *       {@code core.approval.manage} decides it (founder decision 2).
 *   <li>When the manager is the employee themself, the employee's reporting manager approves instead (decision 3), or
 *       nobody when there is none.
 *   <li>Otherwise the project's manager.
 * </ol>
 */
@Component
public class TimesheetProjectManagerResolver implements ApproverResolver {

    private final ProjectRepository projectRepository;
    private final ReportingLineService reportingLineService;

    public TimesheetProjectManagerResolver(
            ProjectRepository projectRepository, ReportingLineService reportingLineService) {
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
        this.reportingLineService =
                Objects.requireNonNull(reportingLineService, "reportingLineService must not be null");
    }

    @Override
    public ApproverKind kind() {
        return ApproverKind.PROJECT_MANAGER;
    }

    @Override
    public Optional<UUID> resolve(UUID tenantId, UUID employeeId, String contextRef) {
        UUID projectId = parse(contextRef);
        if (projectId == null) {
            return Optional.empty();
        }
        Optional<Project> project = projectRepository.findByIdAndTenantIdAndDeletedFalse(projectId, tenantId);
        if (project.isEmpty() || project.get().getManagerEmployeeId() == null) {
            return Optional.empty();
        }
        UUID manager = project.get().getManagerEmployeeId();
        if (!manager.equals(employeeId)) {
            return Optional.of(manager);
        }
        // Nobody approves their own hours: the employee's reporting manager does.
        List<Employee> chain = reportingLineService.chainAbove(employeeId, LocalDate.now());
        return chain == null || chain.isEmpty()
                ? Optional.empty()
                : Optional.of(chain.get(0).getId());
    }

    private static UUID parse(String contextRef) {
        if (contextRef == null || contextRef.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(contextRef.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
