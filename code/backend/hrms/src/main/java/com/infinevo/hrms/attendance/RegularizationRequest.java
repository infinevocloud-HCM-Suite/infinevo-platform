package com.infinevo.hrms.attendance;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Body of {@code POST /api/v1/hrms/attendance/regularizations} (W-40.4 §4). {@code inAt} and {@code outAt} are ISO
 * instants with an offset. The employee is never taken from the body: it is always the caller.
 */
public record RegularizationRequest(LocalDate date, OffsetDateTime inAt, OffsetDateTime outAt, String reason) {}
