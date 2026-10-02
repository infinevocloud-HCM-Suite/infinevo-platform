package com.infinevo.hrms.project;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link AssignmentService} (W-41).
 */
@Service
@Transactional
public class AssignmentServiceImpl implements AssignmentService {

    private static final String INDEX_ASSIGNMENT_UNIQUE = "uk_assignment_tenant_project_employee";

    private final AssignmentRepository assignmentRepository;
    private final ProjectRepository projectRepository;
    private final EmployeeService employeeService;

    public AssignmentServiceImpl(
            AssignmentRepository assignmentRepository,
            ProjectRepository projectRepository,
            EmployeeService employeeService) {
        this.assignmentRepository =
                Objects.requireNonNull(assignmentRepository, "assignmentRepository must not be null");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponse> listByProject(UUID projectId) {
        UUID tenantId = TenantContext.require();
        projectRepository
                .findByIdAndTenantIdAndDeletedFalse(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No project " + projectId + " found in this tenant"));

        return assignmentRepository.findAllByTenantIdAndProjectIdAndDeletedFalse(tenantId, projectId).stream()
                .map(AssignmentResponse::from)
                .toList();
    }

    @Override
    public AssignmentResponse assign(UUID projectId, AssignmentRequest request) {
        UUID tenantId = TenantContext.require();
        projectRepository
                .findByIdAndTenantIdAndDeletedFalse(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No project " + projectId + " found in this tenant"));

        if (request.employeeId() == null) {
            throw new ValidationException("employee_id", "employee_id is required");
        }

        try {
            employeeService.get(request.employeeId());
        } catch (EmployeeService.NotFoundException e) {
            throw new ValidationException("employee_id", "Employee not found in current tenant");
        }

        boolean alreadyAssigned = assignmentRepository.existsByTenantIdAndProjectIdAndEmployeeIdAndDeletedFalse(
                tenantId, projectId, request.employeeId());
        if (alreadyAssigned) {
            throw new DuplicateAssignmentException(
                    "Employee " + request.employeeId() + " is already assigned to project " + projectId);
        }

        LocalDate assignedOn = request.assignedOn() != null ? request.assignedOn() : LocalDate.now();
        String actor = ProjectActor.currentActor();
        Assignment assignment = new Assignment(tenantId, projectId, request.employeeId(), assignedOn, actor);
        try {
            Assignment saved = assignmentRepository.saveAndFlush(assignment);
            return AssignmentResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            if (namesIndex(e, INDEX_ASSIGNMENT_UNIQUE)) {
                throw new DuplicateAssignmentException(
                        "Employee " + request.employeeId() + " is already assigned to project " + projectId);
            }
            throw e;
        }
    }

    @Override
    public void remove(UUID projectId, UUID employeeId) {
        UUID tenantId = TenantContext.require();
        Assignment assignment = assignmentRepository
                .findByTenantIdAndProjectIdAndEmployeeIdAndDeletedFalse(tenantId, projectId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No assignment found for employee " + employeeId + " on project " + projectId));

        assignment.setDeleted(true);
        assignment.setUpdatedBy(ProjectActor.currentActor());
        assignmentRepository.save(assignment);
    }

    /** Whether an index name appears anywhere in a throwable's cause chain. */
    static boolean namesIndex(Throwable e, String indexName) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains(indexName)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
