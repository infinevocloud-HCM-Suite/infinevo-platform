package com.infinevo.payroll.taxdeclaration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TaxDeclarationRequest(
        @JsonProperty("tax_regime") String taxRegime,
        @JsonProperty("is_staying_in_rented_house") Boolean isStayingInRentedHouse,
        @JsonProperty("is_repaying_self_occupied_loan") Boolean isRepayingSelfOccupiedLoan,
        @JsonProperty("has_let_out_property") Boolean hasLetOutProperty) {}
