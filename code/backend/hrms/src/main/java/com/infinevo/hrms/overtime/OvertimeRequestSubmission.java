package com.infinevo.hrms.overtime;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code POST /api/v1/hrms/overtime-requests} body (W-40.6 §4). There is no employee field: the employee is always the
 * caller, so an {@code employeeId} sent in the body is ignored, not honoured.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OvertimeRequestSubmission(
        @JsonAlias("overtime_date") LocalDate overtimeDate, BigDecimal hours, String remarks) {}
