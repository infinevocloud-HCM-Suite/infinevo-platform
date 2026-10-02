package com.infinevo.hrms.attendance;

import java.time.LocalDate;
import java.util.List;

/**
 * Summary of today's attendance state for the calling employee (W-40.3).
 */
public record TodayResponse(
        LocalDate date, ClockSessionResponse openSession, List<ClockSessionResponse> sessions, int workedMinutes) {}
