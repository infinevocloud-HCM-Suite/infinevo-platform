package com.infinevo.hrms.exit;

import java.time.LocalDate;

/**
 * Employee self-service resignation submission payload (W-103).
 */
public record SubmitResignationRequest(
        LocalDate resignationDate,
        LocalDate requestedLwd,
        String exitType,
        String reasonCategory,
        String reasonNotes) {}
