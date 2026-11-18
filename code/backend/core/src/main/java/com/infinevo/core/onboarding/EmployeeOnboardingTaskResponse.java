package com.infinevo.core.onboarding;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeOnboardingTaskResponse(
        UUID id,
        UUID employeeId,
        UUID templateId,
        String title,
        String description,
        OnboardingTaskType taskType,
        String documentLabel,
        String assignedRole,
        boolean isMandatory,
        LocalDate dueDate,
        OnboardingTaskStatus status,
        Instant completedAt,
        UUID completedBy,
        UUID documentId,
        String remarks,
        Instant createdAt,
        Instant updatedAt) {

    public static EmployeeOnboardingTaskResponse from(EmployeeOnboardingTask entity) {
        return new EmployeeOnboardingTaskResponse(
                entity.getId(),
                entity.getEmployeeId(),
                entity.getTemplateId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getTaskType(),
                entity.getDocumentLabel(),
                entity.getAssignedRole(),
                entity.isMandatory(),
                entity.getDueDate(),
                entity.getStatus(),
                entity.getCompletedAt(),
                entity.getCompletedBy(),
                entity.getDocumentId(),
                entity.getRemarks(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
