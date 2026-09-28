package com.infinevo.payroll.statutory.settings;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload for upserting ESI settings (W-31.1).
 */
public record EsiSettingRequest(
        @JsonAlias({"is_enabled", "enabled"}) Boolean isEnabled,
        @JsonAlias({"registration_number"}) String registrationNumber,
        @JsonAlias({"registration_date"}) LocalDate registrationDate,
        @JsonAlias({"deduction_cycle"}) DeductionCycle deductionCycle,
        @JsonAlias({"employee_rate"}) BigDecimal employeeRate,
        @JsonAlias({"employer_rate"}) BigDecimal employerRate,
        @JsonAlias({"wage_ceiling"}) BigDecimal wageCeiling,
        @JsonAlias({"include_employer_in_ctc"}) Boolean includeEmployerInCtc,
        @JsonAlias({"include_in_structure"}) Boolean includeInStructure) {}
