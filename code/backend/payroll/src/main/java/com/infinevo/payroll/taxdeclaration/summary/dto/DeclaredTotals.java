package com.infinevo.payroll.taxdeclaration.summary.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Map;

public record DeclaredTotals(
        @JsonProperty("house_rent_annual") BigDecimal houseRentAnnual,
        @JsonProperty("home_loan_principal") BigDecimal homeLoanPrincipal,
        @JsonProperty("home_loan_interest") BigDecimal homeLoanInterest,
        @JsonProperty("let_out_net") BigDecimal letOutNet,
        @JsonProperty("section6a_by_group") Map<String, BigDecimal> section6aByGroup,
        @JsonProperty("section6a_total") BigDecimal section6aTotal,
        @JsonProperty("pre_tax_total") BigDecimal preTaxTotal,
        @JsonProperty("prev_employment_income") BigDecimal prevEmploymentIncome,
        @JsonProperty("prev_employment_tax") BigDecimal prevEmploymentTax,
        @JsonProperty("other_income_total") BigDecimal otherIncomeTotal) {}
