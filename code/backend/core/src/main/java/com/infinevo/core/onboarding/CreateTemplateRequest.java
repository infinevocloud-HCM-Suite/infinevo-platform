package com.infinevo.core.onboarding;

public record CreateTemplateRequest(
        String title,
        String description,
        OnboardingTaskType taskType,
        String documentLabel,
        OnboardingAssignedRole assignedRole,
        Boolean isMandatory,
        Integer dueDaysAfterJoining,
        Integer sortOrder) {}

