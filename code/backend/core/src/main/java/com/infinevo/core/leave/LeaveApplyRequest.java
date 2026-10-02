package com.infinevo.core.leave;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request payload for an employee applying for leave (W-16.3, spec section 4).
 */
public record LeaveApplyRequest(
        @JsonAlias({"typeId", "leave_type_id"}) UUID leaveTypeId,
        @JsonAlias({"from", "from_date"}) LocalDate fromDate,
        @JsonAlias({"to", "to_date"}) LocalDate toDate,
        @JsonAlias({"halfDay", "is_half_day"}) Boolean isHalfDay,
        @JsonAlias({"halfDayPeriod", "half_day_period"}) HalfDayPeriod halfDayPeriod,
        String reason,
        @JsonAlias({"documentIds", "document_ids"}) List<UUID> documentIds,
        @JsonProperty(defaultValue = "true") Boolean submit) {

    public boolean shouldSubmit() {
        return submit == null || submit;
    }
}
