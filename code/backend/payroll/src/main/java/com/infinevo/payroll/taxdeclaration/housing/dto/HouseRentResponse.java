package com.infinevo.payroll.taxdeclaration.housing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record HouseRentResponse(
        UUID id,
        @JsonProperty("from_month") String fromMonth,
        @JsonProperty("to_month") String toMonth,
        @JsonProperty("address") String address,
        @JsonProperty("landlord_name") String landlordName,
        @JsonProperty("landlord_pan") String landlordPan,
        @JsonProperty("is_metro") boolean isMetro,
        @JsonProperty("amount_per_month") BigDecimal amountPerMonth) {}
