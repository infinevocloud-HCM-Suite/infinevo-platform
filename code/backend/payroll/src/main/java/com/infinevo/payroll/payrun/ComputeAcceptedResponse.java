package com.infinevo.payroll.payrun;

import com.fasterxml.jackson.annotation.JsonProperty;

/** What {@code POST /compute} returns with {@code 202} (W-29.4 §4): the job to watch and its attempt. */
public record ComputeAcceptedResponse(
        @JsonProperty("job_id") String jobId,
        @JsonProperty("status") PayRunStatus status,
        @JsonProperty("compute_attempt") int computeAttempt) {}
