package com.infinevo.core.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
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
    private OnboardingServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        templateRepository = mock(OnboardingTaskTemplateRepository.class);
        taskRepository = mock(EmployeeOnboardingTaskRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        service = new OnboardingServiceImpl(templateRepository, taskRepository, employeeRepository);
    }

    @Test
    @DisplayName("createTemplate creates and saves template with valid inputs")
    void createTemplateSuccess() {
        CreateTemplateRequest request = new CreateTemplateRequest(
                "Submit Aadhaar",
                "Upload clear photo",
                OnboardingTaskType.DOCUMENT_UPLOAD,
                "Aadhaar Card",
                "EMPLOYEE",
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
        CreateTemplateRequest request =
                new CreateTemplateRequest("   ", "Desc", OnboardingTaskType.MANUAL_TASK, null, "EMPLOYEE", true, 7, 0);

        assertThatThrownBy(() -> service.createTemplate(tenantId, request, "tester"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template title is required");
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
                tenantId, "Task 1", "Desc 1", OnboardingTaskType.DOCUMENT_UPLOAD, "ID", "EMPLOYEE", true, 7, 1, "sys");
        OnboardingTaskTemplate t2 = new OnboardingTaskTemplate(
                tenantId, "Task 2", "Desc 2", OnboardingTaskType.MANUAL_TASK, null, "EMPLOYEE", false, 14, 2, "sys");

        when(templateRepository.findByTenantIdAndIsActiveTrueOrderBySortOrderAsc(tenantId))
                .thenReturn(List.of(t1, t2));
        when(taskRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<EmployeeOnboardingTaskResponse> assigned = service.assignDefaultTasks(employee, "admin");

        assertThat(assigned).hasSize(2);
        assertThat(assigned.get(0).title()).isEqualTo("Task 1");
        assertThat(assigned.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(assigned.get(1).title()).isEqualTo("Task 2");
        assertThat(assigned.get(1).dueDate()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    @DisplayName("getEmployeeProgress computes correct percentages and counts")
    void getEmployeeProgressCalculation() {
        EmployeeOnboardingTask task1 = new EmployeeOnboardingTask(
                tenantId,
                employeeId,
                UUID.randomUUID(),
                "Task 1",
                null,
                OnboardingTaskType.MANUAL_TASK,
                null,
                "EMPLOYEE",
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
                "EMPLOYEE",
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
    @DisplayName("updateTaskStatus transitions task and sets completion metadata")
    void updateTaskStatusSuccess() {
        UUID taskId = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        EmployeeOnboardingTask task = new EmployeeOnboardingTask(
                tenantId,
                employeeId,
                UUID.randomUUID(),
                "Upload Doc",
                null,
                OnboardingTaskType.DOCUMENT_UPLOAD,
                "Doc",
                "EMPLOYEE",
                true,
                LocalDate.now(),
                "sys");

        when(taskRepository.findByIdAndTenantId(taskId, tenantId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(EmployeeOnboardingTask.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTaskStatusRequest request =
                new UpdateTaskStatusRequest(OnboardingTaskStatus.COMPLETED, documentId, "Uploaded via self service");

        EmployeeOnboardingTaskResponse updated =
                service.updateTaskStatus(tenantId, employeeId, taskId, request, userUuid, "tester");

        assertThat(updated.status()).isEqualTo(OnboardingTaskStatus.COMPLETED);
        assertThat(updated.documentId()).isEqualTo(documentId);
        assertThat(updated.remarks()).isEqualTo("Uploaded via self service");
        assertThat(task.getCompletedAt()).isNotNull();
        assertThat(task.getCompletedBy()).isEqualTo(userUuid);
    }
}
