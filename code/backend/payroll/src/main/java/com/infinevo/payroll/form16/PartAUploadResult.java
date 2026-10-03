package com.infinevo.payroll.form16;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Result of a Form 16 Part A ZIP upload (W-36.5 §4).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartAUploadResult(int matched, List<String> unmatched, List<String> skipped) {

    public PartAUploadResult {
        unmatched = unmatched == null ? List.of() : List.copyOf(unmatched);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }
}
