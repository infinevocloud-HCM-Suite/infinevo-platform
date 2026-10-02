package com.infinevo.payroll.payslip;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Request body for {@code POST /api/v1/payroll/payruns/{id}/pay} (W-36.2 §4).
 */
public record PayRunPaymentRequest(@JsonProperty("paid_on") LocalDate paidOn) {}
