package com.infinevo.hrms.hiring;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload to generate a job offer (W-112).
 */
public record JobOfferRequest(
        @NotNull(message = "designationId is required") UUID designationId,
        @NotNull(message = "departmentId is required") UUID departmentId,
        @NotNull(message = "workLocationId is required") UUID workLocationId,
        @NotNull(message = "offeredCtc is required") BigDecimal offeredCtc,
        @NotNull(message = "targetJoinDate is required") LocalDate targetJoinDate,
        @NotNull(message = "offerValidUntil is required") LocalDate offerValidUntil) {}
