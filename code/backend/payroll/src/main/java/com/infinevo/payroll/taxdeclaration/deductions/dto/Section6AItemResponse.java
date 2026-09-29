package com.infinevo.payroll.taxdeclaration.deductions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record Section6AItemResponse(
        UUID id,
        @JsonProperty("section_code") String sectionCode,
        String category,
        String name,
        String description,
        @JsonProperty("max_limit") BigDecimal maxLimit,
        @JsonProperty("category_group_code") String categoryGroupCode,
        @JsonProperty("is_80c") boolean is80c,
        @JsonProperty("is_80d") boolean is80d) {}
