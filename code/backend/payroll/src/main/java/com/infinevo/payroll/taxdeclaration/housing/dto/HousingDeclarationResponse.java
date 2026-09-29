package com.infinevo.payroll.taxdeclaration.housing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record HousingDeclarationResponse(
        @JsonProperty("house_rent") List<HouseRentResponse> houseRent,
        @JsonProperty("home_loans") List<HomeLoanResponse> homeLoans,
        @JsonProperty("let_out_properties") List<LetOutPropertyResponse> letOutProperties) {}
