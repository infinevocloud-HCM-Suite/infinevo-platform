package com.infinevo.hrms.hiring;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Request payload to schedule an interview round (W-112).
 */
public record InterviewRequest(
        @NotBlank(message = "roundName is required") String roundName,
        @NotNull(message = "scheduledAt is required") Instant scheduledAt,
        Integer durationMinutes,
        @NotNull(message = "interviewerId is required") UUID interviewerId,
        String meetingLink) {}
