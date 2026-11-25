package com.infinevo.hrms.hiring;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request payload to apply a candidate to a job opening (W-112).
 */
public record JobApplicationRequest(@NotNull(message = "candidateId is required") UUID candidateId) {}
