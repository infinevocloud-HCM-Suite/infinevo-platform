package com.infinevo.core.setup;

import com.infinevo.shared.entitlement.PlatformModule;
import java.time.Instant;

/**
 * Setup step representation in API responses (W-24.1).
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
        Instant firstSeenAt) {}
