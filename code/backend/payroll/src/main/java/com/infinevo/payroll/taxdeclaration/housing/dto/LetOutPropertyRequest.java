package com.infinevo.payroll.taxdeclaration.housing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LetOutPropertyRequest(
        @JsonProperty("property_name") String propertyName,
        @JsonProperty("address") String address,
        @JsonProperty("lines") List<LetOutPropertyLineRequest> lines) {}
