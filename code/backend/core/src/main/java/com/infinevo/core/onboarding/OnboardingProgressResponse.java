package com.infinevo.core.onboarding;

import java.util.List;
import java.util.UUID;

public record OnboardingProgressResponse(
        UUID employeeId,
        int totalTasks,
        int completedTasks,
        int pendingTasks,
        int progressPercentage,
        List<EmployeeOnboardingTaskResponse> tasks) {}
