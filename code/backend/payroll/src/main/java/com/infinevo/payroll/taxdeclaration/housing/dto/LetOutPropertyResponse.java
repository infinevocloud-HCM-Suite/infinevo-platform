package com.infinevo.payroll.taxdeclaration.housing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LetOutPropertyResponse(
        UUID id,
        @JsonProperty("property_name") String propertyName,
        @JsonProperty("address") String address,
        @JsonProperty("net_income_loss") BigDecimal netIncomeLoss,
        @JsonProperty("lines") List<LetOutPropertyLineResponse> lines) {}
