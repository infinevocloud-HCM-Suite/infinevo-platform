package com.infinevo.core.onboarding;

import java.time.Instant;
import java.util.UUID;

public record OnboardingTaskTemplateResponse(
        UUID id,
        UUID tenantId,
        String title,
        String description,
        OnboardingTaskType taskType,
        String documentLabel,
        String assignedRole,
        boolean isMandatory,
        int dueDaysAfterJoining,
        int sortOrder,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt) {

    public static OnboardingTaskTemplateResponse from(OnboardingTaskTemplate entity) {
        return new OnboardingTaskTemplateResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getTaskType(),
                entity.getDocumentLabel(),
                entity.getAssignedRole(),
                entity.isMandatory(),
                entity.getDueDaysAfterJoining(),
                entity.getSortOrder(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
