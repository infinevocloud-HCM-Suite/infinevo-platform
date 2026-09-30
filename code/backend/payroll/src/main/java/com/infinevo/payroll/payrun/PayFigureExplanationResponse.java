package com.infinevo.payroll.payrun;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.lop.WorkingDayBasis;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One employee's pay figure with the stamp that explains it (W-18.2 §3): the net, the loss-of-pay
 * amounts and any loss of pay given back for an earlier month ({@code LOP_REVERSAL}), the days they were worked out from, and the policy version, basis, divisor, payable days and
 * rounding that produced them — exactly as stored on the row, never recomputed.
 */
public record PayFigureExplanationResponse(
        @JsonProperty("payrun_id") UUID payrunId,
        @JsonProperty("period") String period,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("gross_earnings") BigDecimal grossEarnings,
        @JsonProperty("total_deductions") BigDecimal totalDeductions,
        @JsonProperty("net_pay") BigDecimal netPay,
        @JsonProperty("lop_amount") BigDecimal lopAmount,
        @JsonProperty("benefit_lop_amount") BigDecimal benefitLopAmount,
        @JsonProperty("lop_reversal_amount") BigDecimal lopReversalAmount,
        @JsonProperty("lop_days") BigDecimal lopDays,
        @JsonProperty("unpaid_days") BigDecimal unpaidDays,
        @JsonProperty("paid_days") BigDecimal paidDays,
        @JsonProperty("lop_policy_id") UUID lopPolicyId,
        @JsonProperty("working_day_basis") WorkingDayBasis workingDayBasis,
        @JsonProperty("pay_divisor") BigDecimal payDivisor,
        @JsonProperty("payable_days") BigDecimal payableDays,
        @JsonProperty("lop_rounding") LopRounding lopRounding,
        @JsonProperty("computed_at") Instant computedAt) {}
