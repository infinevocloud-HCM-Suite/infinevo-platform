package com.infinevo.core.attendance;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An input item in a bulk attendance upsert request (W-39.1).
 */
public record AttendanceEntry(UUID employeeId, LocalDate date, AttendanceStatus status, String remarks) {}
