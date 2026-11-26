package com.infinevo.core.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.document.Document;
import com.infinevo.core.document.DocumentRepository;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.shared.authz.PermissionService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OnboardingServiceImplTest {

    private OnboardingTaskTemplateRepository templateRepository;
    private EmployeeOnboardingTaskRepository taskRepository;
    private EmployeeRepository employeeRepository;
    private DocumentRepository documentRepository;
    private ReportingLineRepository reportingLineRepository;
    private EmployeeService employeeService;
    private PermissionService permissionService;
    private OnboardingServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        templateRepository = mock(OnboardingTaskTemplateRepository.class);
        taskRepository = mock(EmployeeOnboardingTaskRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        documentRepository = mock(DocumentRepository.class);
        reportingLineRepository = mock(ReportingLineRepository.class);
        employeeService = mock(EmployeeService.class);
        permissionService = mock(PermissionService.class);

        service = new OnboardingServiceImpl(
                templateRepository,
                taskRepository,
                employeeRepository,
                documentRepository,
                reportingLineRepository,
                employeeService,
                permissionService);
    }

    private EmployeeResponse mockEmployee(UUID id) {
        return new EmployeeResponse(
                id,
                tenantId,
                "EMP-001",
                "First",
                null,
                "Last",
                "MALE",
                LocalDate.now(),
                null,
                EmploymentStatus.ACTIVE,
                "emp@example.com",
                null,
                true,
                UUID.randomUUID(),
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
    }

    @Test
    @DisplayName("createTemplate creates and saves template with valid inputs")
    void createTemplateSuccess() {
        CreateTemplateRequest request = new CreateTemplateRequest(
                "Submit Aadhaar",
                "Upload clear photo",
                OnboardingTaskType.DOCUMENT_UPLOAD,
                "ID_PROOF",
                OnboardingAssignedRole.EMPLOYEE,
                true,
                5,
                1);

        when(templateRepository.save(any(OnboardingTaskTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

        OnboardingTaskTemplateResponse response = service.createTemplate(tenantId, request, "tester");

        assertThat(response.title()).isEqualTo("Submit Aadhaar");
        assertThat(response.taskType()).isEqualTo(OnboardingTaskType.DOCUMENT_UPLOAD);
        assertThat(response.dueDaysAfterJoining()).isEqualTo(5);
        assertThat(response.isMandatory()).isTrue();
    }

    @Test
    @DisplayName("createTemplate throws on empty or null title")
    void createTemplateInvalidTitle() {
        CreateTemplateRequest request = new CreateTemplateRequest(
                "   ", "Desc", OnboardingTaskType.MANUAL_TASK, null, OnboardingAssignedRole.EMPLOYEE, true, 7, 0);

        assertThatThrownBy(() -> service.createTemplate(tenantId, request, "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template title is required");
    }

    @Test
    @DisplayName("createTemplate rejects negative dueDaysAfterJoining (400)")
    void createTemplateRejectsNegativeDueDays() {
        CreateTemplateRequest request = new CreateTemplateRequest(
                "Title", "Desc", OnboardingTaskType.MANUAL_TASK, null, OnboardingAssignedRole.EMPLOYEE, true, -1, 0);

        assertThatThrownBy(() -> service.createTemplate(tenantId, request, "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dueDaysAfterJoining must be 0 or more");
    }

    @Test
    @DisplayName("createTemplate requires documentLabel when taskType is DOCUMENT_UPLOAD (400)")
    void createTemplateRequiresDocumentLabelForDocUpload() {
        CreateTemplateRequest request = new CreateTemplateRequest(
                "Upload Docs",
                "Desc",
                OnboardingTaskType.DOCUMENT_UPLOAD,
                null,
                OnboardingAssignedRole.EMPLOYEE,
                true,
                7,
                0);

        assertThatThrownBy(() -> service.createTemplate(tenantId, request, "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("documentLabel is required when taskType is DOCUMENT_UPLOAD");
    }

    @Test
    @DisplayName("assignDefaultTasks generates tasks with due dates relative to dateOfJoining")
    void assignDefaultTasksSuccess() {
        Employee employee = mock(Employee.class);
        when(employee.getTenantId()).thenReturn(tenantId);
        when(employee.getId()).thenReturn(employeeId);
        LocalDate joiningDate = LocalDate.of(2026, 11, 1);
        when(employee.getDateOfJoining()).thenReturn(joiningDate);

        OnboardingTaskTemplate t1 = new OnboardingTaskTemplate(
                tenantId,
                "Task 1",
                "Desc 1",
                OnboardingTaskType.DOCUMENT_UPLOAD,
                "ID",
                OnboardingAssignedRole.EMPLOYEE,
                true,
                7,
                1,
                "sys");
        OnboardingTaskTemplate t2 = new OnboardingTaskTemplate(
                tenantId,
                "Task 2",
                "Desc 2",
                OnboardingTaskType.MANUAL_TASK,
                null,
                OnboardingAssignedRole.EMPLOYEE,
                false,
                14,
                2,
                "sys");

        when(templateRepository.findByTenantIdAndIsActiveTrueOrderBySortOrderAscCreatedAtAsc(tenantId))
                .thenReturn(List.of(t1, t2));
        when(taskRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        List<EmployeeOnboardingTaskResponse> assigned = service.assignDefaultTasks(employee, "admin");

        assertThat(assigned).hasSize(2);
        assertThat(assigned.get(0).title()).isEqualTo("Task 1");
        assertThat(assigned.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(assigned.get(1).title()).isEqualTo("Task 2");
        assertThat(assigned.get(1).dueDate()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    @DisplayName("A created employee gets one task per active template, through EmployeeCreatedEvent")
    void createdEventAssignsTheChecklist() {
        Employee employee = mock(Employee.class);
        when(employee.getTenantId()).thenReturn(tenantId);
        when(employee.getId()).thenReturn(employeeId);
        when(employee.getDateOfJoining()).thenReturn(LocalDate.of(2026, 11, 1));
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(java.util.Optional.of(employee));
        OnboardingTaskTemplate t = new OnboardingTaskTemplate(
                tenantId,
                "Policy",
                null,
                OnboardingTaskType.POLICY_ACKNOWLEDGEMENT,
                null,
                OnboardingAssignedRole.EMPLOYEE,
                true,
                3,
                1,
                "sys");
        when(templateRepository.findByTenantIdAndIsActiveTrueOrderBySortOrderAscCreatedAtAsc(tenantId))
                .thenReturn(List.of(t));
        when(taskRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.onEmployeeCreated(new com.infinevo.core.employee.EmployeeCreatedEvent(tenantId, employeeId, "hr"));

        org.mockito.Mockito.verify(taskRepository).saveAllAndFlush(org.mockito.ArgumentMatchers.argThat(tasks -> {
            List<EmployeeOnboardingTask> list = new java.util.ArrayList<>();
            tasks.forEach(list::add);
            return list.size() == 1 && list.get(0).getDueDate().equals(LocalDate.of(2026, 11, 4));
        }));
    }

    @Test
    @DisplayName("getEmployeeProgress computes correct percentages and counts")
    void getEmployeeProgressCalculation() {
        Employee emp = mock(Employee.class);
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(eq(employeeId), eq(tenantId)))
                .thenReturn(Optional.of(emp));

        EmployeeOnboardingTask task1 = new EmployeeOnboardingTask(
                tenantId,
                employeeId,
                UUID.randomUUID(),
                "Task 1",
                null,
                OnboardingTaskType.MANUAL_TASK,
                null,
                OnboardingAssignedRole.EMPLOYEE,
                true,
                LocalDate.now(),
                "sys");
        task1.setStatus(OnboardingTaskStatus.COMPLETED);

        EmployeeOnboardingTask task2 = new EmployeeOnboardingTask(
                tenantId,
                employeeId,
                UUID.randomUUID(),
                "Task 2",
                null,
                OnboardingTaskType.MANUAL_TASK,
                null,
                OnboardingAssignedRole.EMPLOYEE,
                true,
                LocalDate.now(),
                "sys");
        task2.setStatus(OnboardingTaskStatus.PENDING);

        when(taskRepository.findByTenantIdAndEmployeeIdOrderByDueDateAscCreatedAtAsc(tenantId, employeeId))
                .thenReturn(List.of(task1, task2));

        OnboardingProgressResponse progress = service.getEmployeeProgress(tenantId, employeeId);

        assertThat(progress.totalTasks()).isEqualTo(2);
        assertThat(progress.completedTasks()).isEqualTo(1);
        assertThat(progress.pendingTasks()).isEqualTo(1);
        assertThat(progress.progressPercentage()).isEqualTo(50);
        assertThat(progress.tasks()).hasSize(2);
    }

    @Test
    @DisplayName("getEmployeeProgress with zero tasks returns 0 percent (not 100)")
    void getEmployeeProgressZeroTasksReturnsZeroPercent() {
        Employee emp = mock(Employee.class);
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(eq(employeeId), eq(tenantId)))
                .thenReturn(Optional.of(emp));
        when(taskRepository.findByTenantIdAndEmployeeIdOrderByDueDateAscCreatedAtAsc(tenantId, employeeId))
                .thenReturn(List.of());

        OnboardingProgressResponse progress = service.getEmployeeProgress(tenantId, employeeId);

        assertThat(progress.totalTasks()).isZero();
        assertThat(progress.completedTasks()).isZero();
        assertThat(progress.progressPercentage()).isZero();
    }

    @Test
    @DisplayName("HR can approve submitted document task (SUBMITTED -> COMPLETED)")
    void hrApprovesSubmittedDocumentTask() {
        when(permissionService.holds("core.onboarding.manage")).thenReturn(true);

        EmployeeOnboardingTask task = new EmployeeOnboardingTask(
                tenantId,
                employeeId,
                UUID.randomUUID(),
                "Upload ID",
                null,
                OnboardingTaskType.DOCUMENT_UPLOAD,
                "ID",
                OnboardingAssignedRole.EMPLOYEE,
                true,
                LocalDate.now(),
                "sys");
        task.setStatus(OnboardingTaskStatus.SUBMITTED);
        task.setDocumentId(UUID.randomUUID());

        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(taskId, tenantId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(EmployeeOnboardingTask.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTaskStatusRequest request = new UpdateTaskStatusRequest(OnboardingTaskStatus.COMPLETED, null, "Approved");
        EmployeeOnboardingTaskResponse updated =
                service.updateTaskStatus(tenantId, employeeId, taskId, request, UUID.randomUUID(), "hr");

        assertThat(updated.status()).isEqualTo(OnboardingTaskStatus.COMPLETED);
        assertThat(task.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("HR can reject submitted document task (SUBMITTED -> PENDING, requires remarks and clears documentId)")
    void hrRejectsSubmittedDocumentTask() {
        when(permissionService.holds("core.onboarding.manage")).thenReturn(true);

        EmployeeOnboardingTask task = new EmployeeOnboardingTask(
                tenantId,
                employeeId,
                UUID.randomUUID(),
                "Upload ID",
                null,
                OnboardingTaskType.DOCUMENT_UPLOAD,
                "ID",
                OnboardingAssignedRole.EMPLOYEE,
                true,
                LocalDate.now(),
                "sys");
        task.setStatus(OnboardingTaskStatus.SUBMITTED);
        task.setDocumentId(UUID.randomUUID());

        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(taskId, tenantId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(EmployeeOnboardingTask.class))).thenAnswer(inv -> inv.getArgument(0));

        // Rejection without remarks throws 400
        UpdateTaskStatusRequest noRemarksReq = new UpdateTaskStatusRequest(OnboardingTaskStatus.PENDING, null, null);
        assertThatThrownBy(() -> service.updateTaskStatus(tenantId, employeeId, taskId, noRemarksReq, null, "hr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remarks are required when rejecting");

        // Rejection with remarks succeeds and clears documentId
        UpdateTaskStatusRequest rejectReq =
                new UpdateTaskStatusRequest(OnboardingTaskStatus.PENDING, null, "Blurry document");
        EmployeeOnboardingTaskResponse updated =
                service.updateTaskStatus(tenantId, employeeId, taskId, rejectReq, null, "hr");

        assertThat(updated.status()).isEqualTo(OnboardingTaskStatus.PENDING);
        assertThat(task.getDocumentId()).isNull();
    }

    @Test
    @DisplayName("HR reopen clears completed_at and completed_by")
    void hrReopenClearsCompletedMetadata() {
        when(permissionService.holds("core.onboarding.manage")).thenReturn(true);

        EmployeeOnboardingTask task = new EmployeeOnboardingTask(
                tenantId,
                employeeId,
                UUID.randomUUID(),
                "Policy Ack",
                null,
                OnboardingTaskType.POLICY_ACKNOWLEDGEMENT,
                null,
                OnboardingAssignedRole.EMPLOYEE,
                true,
                LocalDate.now(),
                "sys");
        task.setStatus(OnboardingTaskStatus.COMPLETED);
        task.setCompletedAt(java.time.Instant.now());
        task.setCompletedBy(UUID.randomUUID());

        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(taskId, tenantId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(EmployeeOnboardingTask.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTaskStatusRequest request = new UpdateTaskStatusRequest(OnboardingTaskStatus.PENDING, null, "Reopened");
        EmployeeOnboardingTaskResponse updated =
                service.updateTaskStatus(tenantId, employeeId, taskId, request, null, "hr");

        assertThat(updated.status()).isEqualTo(OnboardingTaskStatus.PENDING);
        assertThat(task.getCompletedAt()).isNull();
        assertThat(task.getCompletedBy()).isNull();
    }

    @Test
    @DisplayName("Employee can submit document task with valid documentId (PENDING -> SUBMITTED)")
    void employeeSubmitsDocumentTask() {
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mockEmployee(employeeId)));
        when(permissionService.holds("core.onboarding.manage")).thenReturn(false);

        EmployeeOnboardingTask task = new EmployeeOnboardingTask(
                tenantId,
                employeeId,
                UUID.randomUUID(),
                "Upload ID",
                null,
                OnboardingTaskType.DOCUMENT_UPLOAD,
                "ID",
                OnboardingAssignedRole.EMPLOYEE,
                true,
                LocalDate.now(),
                "sys");

        UUID taskId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(taskId, tenantId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(EmployeeOnboardingTask.class))).thenAnswer(inv -> inv.getArgument(0));

        Document doc = mock(Document.class);
        when(doc.getEmployeeId()).thenReturn(employeeId);
        when(documentRepository.findByIdAndTenantIdAndDeletedFalse(eq(docId), eq(tenantId)))
                .thenReturn(Optional.of(doc));

        UpdateTaskStatusRequest req = new UpdateTaskStatusRequest(OnboardingTaskStatus.SUBMITTED, docId, "My ID");
        EmployeeOnboardingTaskResponse updated =
                service.updateTaskStatus(tenantId, employeeId, taskId, req, null, "emp");

        assertThat(updated.status()).isEqualTo(OnboardingTaskStatus.SUBMITTED);
        assertThat(task.getDocumentId()).isEqualTo(docId);
    }

    @Test
    @DisplayName("createTemplate refuses a document label the upload endpoint cannot take (400)")
    void createTemplateRejectsUnknownDocumentLabel() {
        CreateTemplateRequest request = new CreateTemplateRequest(
                "Upload Docs",
                "Desc",
                OnboardingTaskType.DOCUMENT_UPLOAD,
                "Identity & Address Proof",
                OnboardingAssignedRole.EMPLOYEE,
                true,
                7,
                0);

        assertThatThrownBy(() -> service.createTemplate(tenantId, request, "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("documentLabel must be one of");
    }

    @Test
    @DisplayName("createTemplate without a sort order goes after the last template, so reorder always moves it")
    void createTemplateDefaultsSortOrderAfterTheLast() {
        when(templateRepository.findByTenantIdOrderBySortOrderAscCreatedAtAsc(tenantId))
                .thenReturn(List.of(
                        new OnboardingTaskTemplate(
                                tenantId,
                                "A",
                                null,
                                OnboardingTaskType.MANUAL_TASK,
                                null,
                                OnboardingAssignedRole.EMPLOYEE,
                                true,
                                7,
                                1,
                                "sys"),
                        new OnboardingTaskTemplate(
                                tenantId,
                                "B",
                                null,
                                OnboardingTaskType.MANUAL_TASK,
                                null,
                                OnboardingAssignedRole.EMPLOYEE,
                                true,
                                7,
                                5,
                                "sys")));
        when(templateRepository.save(any(OnboardingTaskTemplate.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateTemplateRequest request = new CreateTemplateRequest(
                "C", null, OnboardingTaskType.MANUAL_TASK, null, OnboardingAssignedRole.EMPLOYEE, true, 7, null);

        assertThat(service.createTemplate(tenantId, request, "tester").sortOrder())
                .isEqualTo(6);
    }

    @Test
    @DisplayName("An HR user who is the new joiner can submit their own document, checked as theirs")
    void hrNewJoinerSubmitsOwnDocument() {
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mockEmployee(employeeId)));
        when(permissionService.holds("core.onboarding.manage")).thenReturn(true);
        EmployeeOnboardingTask task = documentTask();
        UUID taskId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(taskId, tenantId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(EmployeeOnboardingTask.class))).thenAnswer(inv -> inv.getArgument(0));
        Document doc = mock(Document.class);
        when(doc.getEmployeeId()).thenReturn(employeeId);
        when(documentRepository.findByIdAndTenantIdAndDeletedFalse(docId, tenantId))
                .thenReturn(Optional.of(doc));

        EmployeeOnboardingTaskResponse updated = service.updateTaskStatus(
                tenantId,
                employeeId,
                taskId,
                new UpdateTaskStatusRequest(OnboardingTaskStatus.SUBMITTED, docId, null),
                null,
                "hr");

        assertThat(updated.status()).isEqualTo(OnboardingTaskStatus.SUBMITTED);
        assertThat(task.getDocumentId()).isEqualTo(docId);
    }

    @Test
    @DisplayName("An HR new joiner cannot submit someone else's document on their own task (400)")
    void hrNewJoinerCannotSubmitAnotherEmployeesDocument() {
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mockEmployee(employeeId)));
        when(permissionService.holds("core.onboarding.manage")).thenReturn(true);
        UUID taskId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(taskId, tenantId)).thenReturn(Optional.of(documentTask()));
        Document doc = mock(Document.class);
        when(doc.getEmployeeId()).thenReturn(UUID.randomUUID());
        when(documentRepository.findByIdAndTenantIdAndDeletedFalse(docId, tenantId))
                .thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.updateTaskStatus(
                        tenantId,
                        employeeId,
                        taskId,
                        new UpdateTaskStatusRequest(OnboardingTaskStatus.SUBMITTED, docId, null),
                        null,
                        "hr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document does not belong to employee");
    }

    private EmployeeOnboardingTask documentTask() {
        return new EmployeeOnboardingTask(
                tenantId,
                employeeId,
                UUID.randomUUID(),
                "Upload ID",
                null,
                OnboardingTaskType.DOCUMENT_UPLOAD,
                "ID_PROOF",
                OnboardingAssignedRole.EMPLOYEE,
                true,
                LocalDate.now(),
                "sys");
    }
}
