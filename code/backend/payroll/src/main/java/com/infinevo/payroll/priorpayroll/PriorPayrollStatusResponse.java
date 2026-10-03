package com.infinevo.payroll.priorpayroll;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Mid-year prior payroll status summary (W-38.1 §4).
 */
public record PriorPayrollStatusResponse(
        @JsonProperty("financial_year") String financialYear,
        @JsonProperty("first_regular_run_period") String firstRegularRunPeriod,
        @JsonProperty("imported_periods") List<String> importedPeriods,
        @JsonProperty("missing_periods") List<String> missingPeriods,
        @JsonProperty("setup_step_skipped") boolean setupStepSkipped) {}
