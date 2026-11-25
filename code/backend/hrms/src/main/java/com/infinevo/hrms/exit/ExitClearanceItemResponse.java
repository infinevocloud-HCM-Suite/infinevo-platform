package com.infinevo.hrms.exit;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for one clearance checklist item (W-103).
 */
public record ExitClearanceItemResponse(
        UUID id,
        ClearanceDepartment department,
        String itemName,
        ClearanceItemStatus status,
        String clearedBy,
        Instant clearedAt,
        String remarks) {}
