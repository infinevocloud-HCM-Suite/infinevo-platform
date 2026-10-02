package com.infinevo.payroll.taxcalc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * One Chapter VI-A working line: declared, allowed and the cap applied (W-33.2 spec § 3 step 8, § 4).
 * {@code cap} is null when the item has no limit (80E, 80G, 80GGC).
 */
public record ChapterViaLineResponse(
        @JsonProperty("section_code") String sectionCode,
        @JsonProperty("description") String description,
        @JsonProperty("declared_amount") BigDecimal declaredAmount,
        @JsonProperty("allowed_amount") BigDecimal allowedAmount,
        @JsonProperty("cap") BigDecimal cap) {}
