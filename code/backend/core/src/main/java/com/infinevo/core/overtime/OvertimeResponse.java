package com.infinevo.core.overtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/** One {@code core.overtime_request} row (W-39.2 §4). */
public record OvertimeResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("overtime_date") LocalDate overtimeDate,
        @JsonProperty("hours") BigDecimal hours,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("status") OvertimeStatus status,
        @JsonProperty("source") OvertimeSource source,
        @JsonProperty("remarks") String remarks,
        @JsonProperty("pay_input_id") UUID payInputId,
        @JsonProperty("posted_period") YearMonth postedPeriod,
        @JsonProperty("created_at") Instant createdAt) {

    static OvertimeResponse from(OvertimeRequest entity) {
        return new OvertimeResponse(
                entity.getId(),
                entity.getEmployeeId(),
                entity.getOvertimeDate(),
                entity.getHours(),
                entity.getAmount(),
                entity.getStatus(),
                entity.getSource(),
                entity.getRemarks(),
                entity.getPayInputId(),
                entity.getPostedPeriod(),
                entity.getCreatedAt());
    }
}
