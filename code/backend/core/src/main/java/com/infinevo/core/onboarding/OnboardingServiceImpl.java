package com.infinevo.core.onboarding;

import com.infinevo.core.document.Document;
import com.infinevo.core.document.DocumentRepository;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.ReportingLine;
import com.infinevo.core.org.ReportingLineKind;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.shared.authz.PermissionService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OnboardingServiceImpl implements OnboardingService {

    private final OnboardingTaskTemplateRepository templateRepository;
    private final EmployeeOnboardingTaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final DocumentRepository documentRepository;
    private final ReportingLineRepository reportingLineRepository;
    private final EmployeeService employeeService;
    private final PermissionService permissionService;

    public OnboardingServiceImpl(
            OnboardingTaskTemplateRepository templateRepository,
            EmployeeOnboardingTaskRepository taskRepository,
            EmployeeRepository employeeRepository,
            DocumentRepository documentRepository,
            ReportingLineRepository reportingLineRepository,
            EmployeeService employeeService,
            PermissionService permissionService) {
        this.templateRepository = Objects.requireNonNull(templateRepository, "templateRepository must not be null");
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.documentRepository = Objects.requireNonNull(documentRepository, "documentRepository must not be null");
        this.reportingLineRepository =
                Objects.requireNonNull(reportingLineRepository, "reportingLineRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<OnboardingTaskTemplateResponse> listTemplates(UUID tenantId) {
        return templateRepository.findByTenantIdOrderBySortOrderAsc(tenantId).stream()
                .map(OnboardingTaskTemplateResponse::from)
                .toList();
    }

    @Override
    public OnboardingTaskTemplateResponse createTemplate(UUID tenantId, CreateTemplateRequest request, String actor) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("Template title is required");
        }
        if (request.title().length() > 200) {
            throw new IllegalArgumentException("Template title must not exceed 200 characters");
        }
        if (request.dueDaysAfterJoining() != null && request.dueDaysAfterJoining() < 0) {
            throw new IllegalArgumentException("dueDaysAfterJoining must be 0 or more");
        }
        if (request.taskType() == null) {
            throw new IllegalArgumentException("Task type is required");
        }
        if (request.taskType() == OnboardingTaskType.DOCUMENT_UPLOAD
                && (request.documentLabel() == null || request.documentLabel().isBlank())) {
            throw new IllegalArgumentException("documentLabel is required when taskType is DOCUMENT_UPLOAD");
        }

        OnboardingAssignedRole assignedRole =
                request.assignedRole() != null ? request.assignedRole() : OnboardingAssignedRole.EMPLOYEE;
        boolean isMandatory = request.isMandatory() == null || request.isMandatory();
        int dueDays = request.dueDaysAfterJoining() != null ? request.dueDaysAfterJoining() : 7;
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : 0;

        OnboardingTaskTemplate template = new OnboardingTaskTemplate(
                tenantId,
                request.title().trim(),
                request.description(),
                request.taskType(),
                request.documentLabel(),
                assignedRole,
                isMandatory,
                dueDays,
                sortOrder,
                actor);

        OnboardingTaskTemplate saved = templateRepository.save(template);
        return OnboardingTaskTemplateResponse.from(saved);
    }

    @Override
    public OnboardingTaskTemplateResponse updateTemplate(
            UUID tenantId, UUID templateId, UpdateTemplateRequest request, String actor) {
        Objects.requireNonNull(request, "request must not be null");
        OnboardingTaskTemplate template = templateRepository
                .findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Onboarding template not found with id: " + templateId));

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new IllegalArgumentException("Template title is required");
            }
            if (request.title().length() > 200) {
                throw new IllegalArgumentException("Template title must not exceed 200 characters");
            }
            template.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            template.setDescription(request.description());
        }
        if (request.taskType() != null) {
            template.setTaskType(request.taskType());
        }
        if (template.getTaskType() == OnboardingTaskType.DOCUMENT_UPLOAD) {
            String docLabel = request.documentLabel() != null ? request.documentLabel() : template.getDocumentLabel();
            if (docLabel == null || docLabel.isBlank()) {
                throw new IllegalArgumentException("documentLabel is required when taskType is DOCUMENT_UPLOAD");
            }
        }
        if (request.documentLabel() != null) {
            template.setDocumentLabel(request.documentLabel());
        }
        if (request.assignedRole() != null) {
            template.setAssignedRole(request.assignedRole());
        }
        if (request.isMandatory() != null) {
            template.setMandatory(request.isMandatory());
        }
        if (request.dueDaysAfterJoining() != null) {
            if (request.dueDaysAfterJoining() < 0) {
                throw new IllegalArgumentException("dueDaysAfterJoining must be 0 or more");
            }
            template.setDueDaysAfterJoining(request.dueDaysAfterJoining());
        }
        if (request.sortOrder() != null) {
            template.setSortOrder(request.sortOrder());
        }
        if (request.isActive() != null) {
            template.setActive(request.isActive());
        }
        template.setUpdatedBy(actor);

        OnboardingTaskTemplate saved = templateRepository.save(template);
        return OnboardingTaskTemplateResponse.from(saved);
    }

    @Override
    public void deleteTemplate(UUID tenantId, UUID templateId) {
        OnboardingTaskTemplate template = templateRepository
                .findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Onboarding template not found with id: " + templateId));
        template.setActive(false);
        templateRepository.save(template);
    }

    @Override
    public List<EmployeeOnboardingTaskResponse> assignDefaultTasks(Employee employee, String actor) {
        Objects.requireNonNull(employee, "employee must not be null");
        UUID tenantId = employee.getTenantId();
        List<OnboardingTaskTemplate> templates =
                templateRepository.findByTenantIdAndIsActiveTrueOrderBySortOrderAsc(tenantId);
        if (templates.isEmpty()) {
            return List.of();
        }

        LocalDate baseDate = employee.getDateOfJoining() != null ? employee.getDateOfJoining() : LocalDate.now();

        List<EmployeeOnboardingTask> tasksToSave = new ArrayList<>();
        for (OnboardingTaskTemplate template : templates) {
            LocalDate dueDate = baseDate.plusDays(template.getDueDaysAfterJoining());
            EmployeeOnboardingTask task = new EmployeeOnboardingTask(
                    tenantId,
                    employee.getId(),
                    template.getId(),
                    template.getTitle(),
                    template.getDescription(),
                    template.getTaskType(),
                    template.getDocumentLabel(),
                    template.getAssignedRole(),
                    template.isMandatory(),
                    dueDate,
                    actor);
            tasksToSave.add(task);
        }

        return taskRepository.saveAll(tasksToSave).stream()
                .map(EmployeeOnboardingTaskResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OnboardingProgressResponse getEmployeeProgress(UUID tenantId, UUID employeeId) {
        employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Employee not found with id: " + employeeId));

        List<EmployeeOnboardingTask> tasks =
                taskRepository.findByTenantIdAndEmployeeIdOrderByDueDateAscCreatedAtAsc(tenantId, employeeId);
        List<EmployeeOnboardingTaskResponse> taskResponses =
                tasks.stream().map(EmployeeOnboardingTaskResponse::from).toList();

        int totalTasks = taskResponses.size();
        int completedTasks = (int) taskResponses.stream()
                .filter(t -> t.status() == OnboardingTaskStatus.COMPLETED || t.status() == OnboardingTaskStatus.WAIVED)
                .count();
        int pendingTasks = totalTasks - completedTasks;
        int progressPercentage = totalTasks == 0 ? 0 : (int) Math.round(((double) completedTasks / totalTasks) * 100);

        return new OnboardingProgressResponse(
                employeeId, totalTasks, completedTasks, pendingTasks, progressPercentage, taskResponses);
    }

    @Override
    public OnboardingProgressResponse assignTasksToEmployee(UUID tenantId, UUID employeeId, String actor) {
        Employee employee = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Employee not found with id: " + employeeId));

        long existingCount = taskRepository.countByTenantIdAndEmployeeId(tenantId, employeeId);
        if (existingCount == 0) {
            assignDefaultTasks(employee, actor);
        }
        return getEmployeeProgress(tenantId, employeeId);
    }

    @Override
    public EmployeeOnboardingTaskResponse updateTaskStatus(
            UUID tenantId,
            UUID employeeId,
            UUID taskId,
            UpdateTaskStatusRequest request,
            UUID actorUserUuid,
            String actor) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.status() == null) {
            throw new IllegalArgumentException("Status is required");
        }

        EmployeeOnboardingTask task = taskRepository
                .findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Onboarding task not found with id: " + taskId));

        if (!task.getEmployeeId().equals(employeeId)) {
            throw new NoSuchElementException("Task does not belong to employee: " + employeeId);
        }

        UUID callerEmployeeId =
                employeeService.currentEmployee().map(EmployeeResponse::id).orElse(null);
        boolean isHR = permissionService.holds("core.onboarding.manage");
        boolean isSelf = callerEmployeeId != null && callerEmployeeId.equals(employeeId);
        boolean isPrimaryManager = isPrimaryManagerOf(tenantId, callerEmployeeId, employeeId);

        if (!isHR && !isSelf && !isPrimaryManager) {
            throw new AccessDeniedException("Access denied: cannot update another employee's onboarding tasks");
        }

        OnboardingTaskStatus fromStatus = task.getStatus();
        OnboardingTaskStatus toStatus = request.status();
        OnboardingTaskType taskType = task.getTaskType();
        OnboardingAssignedRole assignedRole = task.getAssignedRole();

        if (isSelf && !isHR) {
            if (assignedRole != OnboardingAssignedRole.EMPLOYEE) {
                throw new AccessDeniedException("Employee cannot modify tasks assigned to " + assignedRole);
            }
            if (toStatus == OnboardingTaskStatus.WAIVED) {
                throw new IllegalArgumentException("Only HR may waive onboarding tasks");
            }

            if (taskType == OnboardingTaskType.DOCUMENT_UPLOAD) {
                if (toStatus == OnboardingTaskStatus.COMPLETED) {
                    throw new IllegalArgumentException(
                            "Employee cannot complete document upload task; submit document for verification");
                }
                if (fromStatus == OnboardingTaskStatus.PENDING && toStatus == OnboardingTaskStatus.SUBMITTED) {
                    if (request.documentId() == null) {
                        throw new IllegalArgumentException("documentId is required when submitting a document task");
                    }
                    Document doc = documentRepository
                            .findByIdAndTenantIdAndDeletedFalse(request.documentId(), tenantId)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Document not found: " + request.documentId()));
                    if (!employeeId.equals(doc.getEmployeeId())) {
                        throw new IllegalArgumentException("Document does not belong to employee: " + employeeId);
                    }
                    task.setDocumentId(request.documentId());
                } else {
                    throw new IllegalArgumentException(
                            "Invalid status transition from " + fromStatus + " to " + toStatus);
                }
            } else {
                if (fromStatus == OnboardingTaskStatus.PENDING && toStatus == OnboardingTaskStatus.COMPLETED) {
                    // allowed
                } else {
                    throw new IllegalArgumentException(
                            "Invalid status transition from " + fromStatus + " to " + toStatus);
                }
            }
        } else if (isPrimaryManager && !isHR) {
            if (assignedRole != OnboardingAssignedRole.MANAGER) {
                throw new AccessDeniedException("Manager can only update tasks assigned to MANAGER");
            }
            if (taskType == OnboardingTaskType.DOCUMENT_UPLOAD) {
                throw new IllegalArgumentException("Manager cannot complete document upload tasks");
            }
            if (fromStatus == OnboardingTaskStatus.PENDING && toStatus == OnboardingTaskStatus.COMPLETED) {
                // allowed
            } else {
                throw new IllegalArgumentException("Invalid status transition from " + fromStatus + " to " + toStatus);
            }
        } else if (isHR) {
            if (taskType == OnboardingTaskType.DOCUMENT_UPLOAD) {
                if (fromStatus == OnboardingTaskStatus.SUBMITTED && toStatus == OnboardingTaskStatus.COMPLETED) {
                    // HR approves document
                } else if (fromStatus == OnboardingTaskStatus.SUBMITTED && toStatus == OnboardingTaskStatus.PENDING) {
                    // HR rejects document -> remarks required, clear documentId
                    if (request.remarks() == null || request.remarks().isBlank()) {
                        throw new IllegalArgumentException(
                                "remarks are required when rejecting a submitted document task");
                    }
                    task.setDocumentId(null);
                } else if ((fromStatus == OnboardingTaskStatus.PENDING || fromStatus == OnboardingTaskStatus.SUBMITTED)
                        && toStatus == OnboardingTaskStatus.WAIVED) {
                    // HR waives
                } else if ((fromStatus == OnboardingTaskStatus.COMPLETED || fromStatus == OnboardingTaskStatus.WAIVED)
                        && toStatus == OnboardingTaskStatus.PENDING) {
                    // reopen
                } else {
                    throw new IllegalArgumentException(
                            "Invalid status transition from " + fromStatus + " to " + toStatus);
                }
            } else {
                if (fromStatus == OnboardingTaskStatus.PENDING && toStatus == OnboardingTaskStatus.COMPLETED) {
                    // allowed
                } else if (fromStatus == OnboardingTaskStatus.PENDING && toStatus == OnboardingTaskStatus.WAIVED) {
                    // allowed
                } else if ((fromStatus == OnboardingTaskStatus.COMPLETED || fromStatus == OnboardingTaskStatus.WAIVED)
                        && toStatus == OnboardingTaskStatus.PENDING) {
                    // reopen
                } else {
                    throw new IllegalArgumentException(
                            "Invalid status transition from " + fromStatus + " to " + toStatus);
                }
            }
        }

        if (toStatus == OnboardingTaskStatus.COMPLETED || toStatus == OnboardingTaskStatus.WAIVED) {
            task.setCompletedAt(Instant.now());
            task.setCompletedBy(actorUserUuid != null ? actorUserUuid : callerEmployeeId);
        } else {
            task.setCompletedAt(null);
            task.setCompletedBy(null);
        }

        if (request.remarks() != null) {
            task.setRemarks(request.remarks());
        }

        task.setStatus(toStatus);
        task.setUpdatedBy(actor);

        EmployeeOnboardingTask saved = taskRepository.save(task);
        return EmployeeOnboardingTaskResponse.from(saved);
    }

    private boolean isPrimaryManagerOf(UUID tenantId, UUID managerEmployeeId, UUID targetEmployeeId) {
        if (managerEmployeeId == null || targetEmployeeId == null) {
            return false;
        }
        return reportingLineRepository
                .findCurrentOpenLine(tenantId, targetEmployeeId, ReportingLineKind.PRIMARY)
                .map(ReportingLine::getManager)
                .map(Employee::getId)
                .filter(managerEmployeeId::equals)
                .isPresent();
    }
}
