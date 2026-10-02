package com.infinevo.hrms.timesheet;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * One page of a review list (W-42.4): the weeks on it and enough to draw a pager. A record of its own, not Spring's
 * {@code Page}, so the JSON is ours and snake_case like the rest of the timesheet API.
 */
public record TimesheetPage(
        @JsonProperty("content") List<TimesheetResponse> content,
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages) {

    /** The weeks to return, with the paging numbers of the page of ids they were loaded for. */
    static TimesheetPage of(List<TimesheetResponse> content, Page<?> page) {
        return new TimesheetPage(
                content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    static TimesheetPage empty(int page, int size) {
        return new TimesheetPage(List.of(), page, size, 0, 0);
    }
}
