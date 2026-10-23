package com.infinevo.core.wfh;

import java.time.LocalDate;
import java.util.UUID;

public record ApplyWfhRequest(
        UUID employeeId, LocalDate fromDate, LocalDate toDate, boolean halfDay, String halfDayPeriod, String reason) {}
