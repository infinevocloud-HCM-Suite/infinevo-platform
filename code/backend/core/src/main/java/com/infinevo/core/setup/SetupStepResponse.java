package com.infinevo.core.setup;

import com.infinevo.shared.entitlement.PlatformModule;
import java.time.Instant;

/**
 * Setup step representation in API responses (W-24.1).
 *
 * <p>{@code newStep} is true for a step the catalogue gained after this tenant's checklist was
 * assembled and which the tenant has not acted on since. It is listed, incomplete, and left out of
 * the progress fraction until the tenant next touches setup (spec §13 decision 1).
 */
public record SetupStepResponse(
        String code,
        String label,
        PlatformModule module,
        int displayOrder,
        boolean completed,
        boolean skipped,
        String skipReason,
        Instant completedAt,
        Instant firstSeenAt,
        boolean newStep) {}
