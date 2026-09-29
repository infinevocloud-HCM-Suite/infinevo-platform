package com.infinevo.core.setup;

import java.util.List;

/**
 * Onboarding setup checklist response with progress metrics (W-24.1).
 */
public record SetupChecklistResponse(
        List<SetupStepResponse> steps,
        int completedCount,
        int skippedCount,
        int totalCount,
        double progressPercentage) {}
