package com.infinevo.payroll.taxdeclaration.deductions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record Section6ALineRequest(
        @JsonProperty("section6a_item_id") UUID section6aItemId,
        @JsonProperty("description") String description,
        @JsonProperty("amount") BigDecimal amount) {}
