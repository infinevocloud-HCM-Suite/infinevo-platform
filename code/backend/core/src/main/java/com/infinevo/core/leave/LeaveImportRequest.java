package com.infinevo.core.leave;

import java.util.UUID;

/**
 * Request payload for initiating a bulk leave allocation import (W-16.4b, spec section 4).
 *
 * @param documentId the UUID of the uploaded CSV document in {@code core.document}
 * @param leaveYear the target leave year (e.g. "2026", "2026-2027")
 * @param dryRun if true, validates rows and reports errors without creating allocations
 */
public record LeaveImportRequest(UUID documentId, String leaveYear, boolean dryRun) {}
