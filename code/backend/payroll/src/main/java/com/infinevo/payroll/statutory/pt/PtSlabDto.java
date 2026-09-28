package com.infinevo.payroll.statutory.pt;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO representing one professional tax bracket (W-31.2).
 */
public record PtSlabDto(
        @JsonProperty("from_amount") @JsonAlias("fromAmount") BigDecimal fromAmount,
        @JsonProperty("to_amount") @JsonAlias("toAmount") BigDecimal toAmount,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("is_female_exempt") @JsonAlias({"isFemaleExempt", "femaleExempted"}) boolean isFemaleExempt,
        @JsonProperty("deduction_months") @JsonAlias({"deductionMonths"}) List<Integer> deductionMonths) {

    public static List<Integer> parseMonths(String deductionMonthsStr) {
        if (deductionMonthsStr == null || deductionMonthsStr.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(deductionMonthsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    public static String formatMonths(List<Integer> months) {
        if (months == null || months.isEmpty()) {
            return null;
        }
        return months.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }
}
