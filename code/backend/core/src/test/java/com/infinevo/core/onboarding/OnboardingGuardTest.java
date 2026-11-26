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
import com.infinevo.core.org.ReportingLine;
import com.infinevo.core.org.ReportingLineKind;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class OnboardingGuardTest {

    private OnboardingTaskTemplateRepository templateRepository;
    private EmployeeOnboardingTaskRepository taskRepository;
    private EmployeeRepository employeeRepository;
    private DocumentRepository documentRepository;
    private ReportingLineRepository reportingLineRepository;
    private EmployeeService employeeService;
    private PermissionService permissionService;
    private OnboardingServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeA = UUID.randomUUID();
    private final UUID employeeB = UUID.randomUUID();
    private final UUID managerA = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
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

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private EmployeeResponse mockEmployee(UUID id, String empNumber) {
        return new EmployeeResponse(
                id,
                tenantId,
                empNumber,
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
    @DisplayName("Employee cannot update another employee's onboarding task (403)")
    void employeeCannotUpdateAnotherEmployeesTask() {
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mockEmployee(employeeA, "EMP-A")));
        when(permissionService.holds("core.onboarding.manage")).thenReturn(false);

        EmployeeOnboardingTask taskB = new EmployeeOnboardingTask(
                tenantId,
                employeeB,
                null,
                "Task of B",
                "Desc",
                OnboardingTaskType.MANUAL_TASK,
                null,
                OnboardingAssignedRole.EMPLOYEE,
                true,
                LocalDate.now().plusDays(7),
                "system");

        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(eq(taskId), eq(tenantId))).thenReturn(Optional.of(taskB));

        UpdateTaskStatusRequest req =
                new UpdateTaskStatusRequest(OnboardingTaskStatus.COMPLETED, null, "Done by outsider");

        assertThatThrownBy(() -> service.updateTaskStatus(tenantId, employeeB, taskId, req, null, "empA"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("cannot update another employee");
    }

    @Test
    @DisplayName("Employee cannot waive own task (400)")
    void employeeCannotWaiveOwnTask() {
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mockEmployee(employeeA, "EMP-A")));
        when(permissionService.holds("core.onboarding.manage")).thenReturn(false);

        EmployeeOnboardingTask taskA = new EmployeeOnboardingTask(
                tenantId,
                employeeA,
                null,
                "Policy Task",
                "Desc",
                OnboardingTaskType.POLICY_ACKNOWLEDGEMENT,
                null,
                OnboardingAssignedRole.EMPLOYEE,
                true,
                LocalDate.now().plusDays(3),
                "system");

        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(eq(taskId), eq(tenantId))).thenReturn(Optional.of(taskA));

        UpdateTaskStatusRequest req = new UpdateTaskStatusRequest(OnboardingTaskStatus.WAIVED, null, null);

        assertThatThrownBy(() -> service.updateTaskStatus(tenantId, employeeA, taskId, req, null, "empA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only HR may waive");
    }

    @Test
    @DisplayName("Employee cannot mark DOCUMENT_UPLOAD task as COMPLETED directly (400)")
    void employeeCannotCompleteDocumentUploadTask() {
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mockEmployee(employeeA, "EMP-A")));
        when(permissionService.holds("core.onboarding.manage")).thenReturn(false);

        EmployeeOnboardingTask docTask = new EmployeeOnboardingTask(
                tenantId,
                employeeA,
                null,
                "Upload ID",
                "Desc",
                OnboardingTaskType.DOCUMENT_UPLOAD,
                "Gov ID",
                OnboardingAssignedRole.EMPLOYEE,
                true,
                LocalDate.now().plusDays(7),
                "system");

        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(eq(taskId), eq(tenantId))).thenReturn(Optional.of(docTask));

        UpdateTaskStatusRequest req = new UpdateTaskStatusRequest(OnboardingTaskStatus.COMPLETED, null, null);

        assertThatThrownBy(() -> service.updateTaskStatus(tenantId, employeeA, taskId, req, null, "empA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Employee cannot complete document upload task");
    }

    @Test
    @DisplayName("Employee cannot touch task assigned to HR, MANAGER or IT (403)")
    void employeeCannotTouchManagerHrOrItTask() {
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mockEmployee(employeeA, "EMP-A")));
        when(permissionService.holds("core.onboarding.manage")).thenReturn(false);

        EmployeeOnboardingTask mgrTask = new EmployeeOnboardingTask(
                tenantId,
                employeeA,
                null,
                "Manager Orientation",
                "Desc",
                OnboardingTaskType.MANUAL_TASK,
                null,
                OnboardingAssignedRole.MANAGER,
                true,
                LocalDate.now().plusDays(7),
                "system");

        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(eq(taskId), eq(tenantId))).thenReturn(Optional.of(mgrTask));

        UpdateTaskStatusRequest req = new UpdateTaskStatusRequest(OnboardingTaskStatus.COMPLETED, null, null);

        assertThatThrownBy(() -> service.updateTaskStatus(tenantId, employeeA, taskId, req, null, "empA"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Employee cannot modify tasks assigned to MANAGER");
    }

    @Test
    @DisplayName("Primary manager can complete MANAGER assigned task")
    void primaryManagerCanCompleteManagerTask() {
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mockEmployee(managerA, "MGR-A")));
        when(permissionService.holds("core.onboarding.manage")).thenReturn(false);

        Employee mgr = mock(Employee.class);
        when(mgr.getId()).thenReturn(managerA);
        ReportingLine line = mock(ReportingLine.class);
        when(line.getManager()).thenReturn(mgr);
        when(reportingLineRepository.findCurrentOpenLine(eq(tenantId), eq(employeeA), eq(ReportingLineKind.PRIMARY)))
                .thenReturn(Optional.of(line));

        EmployeeOnboardingTask mgrTask = new EmployeeOnboardingTask(
                tenantId,
                employeeA,
                null,
                "Manager Orientation",
                "Desc",
                OnboardingTaskType.MANUAL_TASK,
                null,
                OnboardingAssignedRole.MANAGER,
                true,
                LocalDate.now().plusDays(7),
                "system");

        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(eq(taskId), eq(tenantId))).thenReturn(Optional.of(mgrTask));
        when(taskRepository.save(any(EmployeeOnboardingTask.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTaskStatusRequest req =
                new UpdateTaskStatusRequest(OnboardingTaskStatus.COMPLETED, null, "Conducted 1:1");

        EmployeeOnboardingTaskResponse response =
                service.updateTaskStatus(tenantId, employeeA, taskId, req, null, "mgrA");

        assertThat(response.status()).isEqualTo(OnboardingTaskStatus.COMPLETED);
    }

    @Test
    @DisplayName("Submitting document task with another employee's document is rejected (400)")
    void submitDocumentWithOtherEmployeesDocRejected() {
        when(employeeService.currentEmployee()).thenReturn(Optional.of(mockEmployee(employeeA, "EMP-A")));
        when(permissionService.holds("core.onboarding.manage")).thenReturn(false);

        EmployeeOnboardingTask docTask = new EmployeeOnboardingTask(
                tenantId,
                employeeA,
                null,
                "Upload ID",
                "Desc",
                OnboardingTaskType.DOCUMENT_UPLOAD,
                "Gov ID",
                OnboardingAssignedRole.EMPLOYEE,
                true,
                LocalDate.now().plusDays(7),
                "system");

        UUID taskId = UUID.randomUUID();
        UUID otherDocId = UUID.randomUUID();
        when(taskRepository.findByIdAndTenantId(eq(taskId), eq(tenantId))).thenReturn(Optional.of(docTask));

        Document otherDoc = mock(Document.class);
        when(otherDoc.getEmployeeId()).thenReturn(employeeB); // Belongs to B!
        when(documentRepository.findByIdAndTenantIdAndDeletedFalse(eq(otherDocId), eq(tenantId)))
                .thenReturn(Optional.of(otherDoc));

        UpdateTaskStatusRequest req =
                new UpdateTaskStatusRequest(OnboardingTaskStatus.SUBMITTED, otherDocId, "My file");

        assertThatThrownBy(() -> service.updateTaskStatus(tenantId, employeeA, taskId, req, null, "empA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document does not belong to employee");
    }

    @Test
    @DisplayName("Unknown employee id throws NoSuchElementException (404) on getEmployeeProgress")
    void unknownEmployeeThrowsNotFound() {
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(eq(employeeB), eq(tenantId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEmployeeProgress(tenantId, employeeB))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Employee not found");
    }
}
