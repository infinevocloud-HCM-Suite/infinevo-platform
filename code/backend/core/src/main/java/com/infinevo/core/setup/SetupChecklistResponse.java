package com.infinevo.core.setup;

import java.util.List;

/**
 * Onboarding setup checklist response with progress metrics (W-24.1).
 *
 * <p>{@code totalCount} is every step listed. {@code newCount} is how many of them are new since
 * the tenant last touched setup; those are outside {@code progressPercentage}'s denominator
 * (spec §13 decision 1), so a release that adds a step does not drop anyone below 100%.
 */
public record SetupChecklistResponse(
        List<SetupStepResponse> steps,
        int completedCount,
        int skippedCount,
        int totalCount,
        int newCount,
        double progressPercentage) {}
