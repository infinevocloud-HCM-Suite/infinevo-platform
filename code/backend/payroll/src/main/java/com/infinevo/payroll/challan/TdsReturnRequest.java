package com.infinevo.payroll.challan;

import java.time.LocalDate;

/** Marks a quarter filed: the 15-digit token number from the filing acknowledgement, and the date filed (W-89). */
public record TdsReturnRequest(String tokenNumber, LocalDate filedOn) {}
