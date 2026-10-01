package com.infinevo.payroll.payslip;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Complete payslip response as rendered on-the-fly (W-36.2 §4).
 * Every amount is a string-safe BigDecimal at scale 2 on the wire.
 */
public record PayslipResponse(
        @JsonProperty("run") RunSummary run,
        @JsonProperty("employer") EmployerSummary employer,
        @JsonProperty("employee") EmployeeSummary employee,
        @JsonProperty("days") DaysSummary days,
        @JsonProperty("earnings") List<PayslipLineResponse> earnings,
        @JsonProperty("deductions") List<PayslipLineResponse> deductions,
        @JsonProperty("reimbursements") List<PayslipLineResponse> reimbursements,
        @JsonProperty("benefits") List<PayslipLineResponse> benefits,
        @JsonProperty("totals") TotalsSummary totals,
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("link") LinkSummary link) {

    public record RunSummary(
            @JsonProperty("payrun_id") UUID payrunId,
            @JsonProperty("type") String type,
            @JsonProperty("period") String period,
            @JsonProperty("pay_date") LocalDate payDate,
            @JsonProperty("paid_on") LocalDate paidOn,
            @JsonProperty("status") String status) {}

    public record EmployerSummary(@JsonProperty("name") String name) {}

    public record EmployeeSummary(
            @JsonProperty("id") UUID id,
            @JsonProperty("number") String number,
            @JsonProperty("name") String name,
            @JsonProperty("designation") String designation,
            @JsonProperty("department") String department,
            @JsonProperty("date_of_joining") LocalDate dateOfJoining) {}

    public record DaysSummary(
            @JsonProperty("payable_days") BigDecimal payableDays,
            @JsonProperty("paid_days") BigDecimal paidDays,
            @JsonProperty("lop_days") BigDecimal lopDays,
            @JsonProperty("unpaid_days") BigDecimal unpaidDays) {}

    public record TotalsSummary(
            @JsonProperty("gross_earnings") BigDecimal grossEarnings,
            @JsonProperty("total_deductions") BigDecimal totalDeductions,
            @JsonProperty("total_reimbursements") BigDecimal totalReimbursements,
            @JsonProperty("total_benefits") BigDecimal totalBenefits,
            @JsonProperty("net_pay") BigDecimal netPay) {}

    public record LinkSummary(@JsonProperty("expires_at") Instant expiresAt) {}
}
