package com.infinevo.payroll.form16;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * What one Part A upload did (W-36.5 §3): how many certificates were filed, the PDFs no live employee
 * in the tenant could be matched to, and the entries left alone — not a PDF, macOS metadata, an unsafe
 * path, a PAN named twice, or a file the document store refused.
 */
public record PartAUploadResult(
        @JsonProperty("matched") int matched,
        @JsonProperty("unmatched") List<String> unmatched,
        @JsonProperty("skipped") List<String> skipped) {

    public PartAUploadResult {
        unmatched = List.copyOf(unmatched);
        skipped = List.copyOf(skipped);
    }
}
