package com.infinevo.core.onboarding;

import java.util.UUID;

public record UpdateTaskStatusRequest(OnboardingTaskStatus status, UUID documentId, String remarks) {}
