package com.infinevo.payroll.payslip;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Summary row for {@code GET /api/v1/me/payslips} (W-36.2 §4).
 */
public record PayslipSummaryResponse(
        @JsonProperty("payrun_id") UUID payrunId,
        @JsonProperty("period") String period,
        @JsonProperty("paid_on") LocalDate paidOn,
        @JsonProperty("net_pay") BigDecimal netPay) {}
