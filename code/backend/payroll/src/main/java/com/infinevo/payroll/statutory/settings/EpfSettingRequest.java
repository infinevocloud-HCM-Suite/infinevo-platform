package com.infinevo.payroll.statutory.settings;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload for upserting EPF settings (W-31.1).
 */
public record EpfSettingRequest(
        @JsonAlias({"is_enabled", "enabled"}) Boolean isEnabled,
        @JsonAlias({"registration_number"}) String registrationNumber,
        @JsonAlias({"registration_date"}) LocalDate registrationDate,
        @JsonAlias({"deduction_cycle"}) DeductionCycle deductionCycle,
        @JsonAlias({"employee_rate"}) BigDecimal employeeRate,
        @JsonAlias({"employer_rate"}) BigDecimal employerRate,
        @JsonAlias({"eps_rate"}) BigDecimal epsRate,
        @JsonAlias({"edli_rate"}) BigDecimal edliRate,
        @JsonAlias({"admin_charge_rate"}) BigDecimal adminChargeRate,
        @JsonAlias({"wage_ceiling"}) BigDecimal wageCeiling,
        @JsonAlias({"restrict_employee_to_ceiling"}) Boolean restrictEmployeeToCeiling,
        @JsonAlias({"restrict_employer_to_ceiling"}) Boolean restrictEmployerToCeiling,
        @JsonAlias({"prorate_restricted_wage"}) Boolean prorateRestrictedWage,
        @JsonAlias({"consider_earned_wage"}) Boolean considerEarnedWage,
        @JsonAlias({"eps_senior_age"}) Integer epsSeniorAge,
        @JsonAlias({"include_employer_in_ctc"}) Boolean includeEmployerInCtc,
        @JsonAlias({"include_edli_in_ctc"}) Boolean includeEdliInCtc,
        @JsonAlias({"include_admin_in_ctc"}) Boolean includeAdminInCtc,
        @JsonAlias({"include_employer_in_structure"}) Boolean includeEmployerInStructure,
        @JsonAlias({"include_edli_in_structure"}) Boolean includeEdliInStructure,
        @JsonAlias({"include_admin_in_structure"}) Boolean includeAdminInStructure,
        @JsonAlias({"abry_scheme"}) Boolean abryScheme) {}
