package com.infinevo.payroll.taxcalc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * House-property working under sections 24(b) and 71(3A) (W-33.2 spec § 3 step 5, § 4).
 */
public record HousePropertyResponse(
        @JsonProperty("house_property_income") BigDecimal housePropertyIncome,
        @JsonProperty("self_occupied_interest") BigDecimal selfOccupiedInterest,
        @JsonProperty("let_out_net") BigDecimal letOutNet,
        @JsonProperty("loss_cap_applied") boolean lossCapApplied) {}
