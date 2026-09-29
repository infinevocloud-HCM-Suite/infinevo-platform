package com.infinevo.payroll.taxdeclaration.deductions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record Section6ALineResponse(
        UUID id,
        @JsonProperty("section6a_item_id") UUID section6aItemId,
        @JsonProperty("section_code") String sectionCode,
        String name,
        String description,
        BigDecimal amount) {}
