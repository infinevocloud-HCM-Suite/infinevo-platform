package com.infinevo.payroll.taxcalc.model;

import com.infinevo.payroll.taxcalc.AgeCategory;
import com.infinevo.payroll.taxdeclaration.deductions.PrevEmploymentKind;
import com.infinevo.shared.money.Money;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Input aggregated across employee and payroll records for tax calculation (W-33.1 spec § 3, § 4).
 *
 * @param employeeId the employee identifier
 * @param declarationId the declaration header identifier
 * @param salary projected salary result from active CTC versions
 * @param prevEmployment declared income and deductions from previous employers by kind
 * @param ageCategory statutory age category as of March 31
 */
public record TaxInput(
        UUID employeeId,
        UUID declarationId,
        SalaryProjectionResult salary,
        Map<PrevEmploymentKind, Money> prevEmployment,
        AgeCategory ageCategory,
        Optional<Object> houseRent,
        Optional<Object> homeLoan,
        Optional<Object> letOutProperty,
        Optional<Object> section6A,
        Optional<Object> preTaxDeductions,
        Optional<Object> otherIncome) {

    public TaxInput {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(declarationId, "declarationId must not be null");
        Objects.requireNonNull(salary, "salary must not be null");
        prevEmployment = prevEmployment == null ? Map.of() : Map.copyOf(prevEmployment);
        ageCategory = ageCategory == null ? AgeCategory.GENERAL : ageCategory;
        houseRent = houseRent == null ? Optional.empty() : houseRent;
        homeLoan = homeLoan == null ? Optional.empty() : homeLoan;
        letOutProperty = letOutProperty == null ? Optional.empty() : letOutProperty;
        section6A = section6A == null ? Optional.empty() : section6A;
        preTaxDeductions = preTaxDeductions == null ? Optional.empty() : preTaxDeductions;
        otherIncome = otherIncome == null ? Optional.empty() : otherIncome;
    }

    public TaxInput(
            UUID employeeId,
            UUID declarationId,
            SalaryProjectionResult salary,
            Map<PrevEmploymentKind, Money> prevEmployment,
            AgeCategory ageCategory) {
        this(
                employeeId,
                declarationId,
                salary,
                prevEmployment,
                ageCategory,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public Money prevEmploymentIncome() {
        return prevEmployment.getOrDefault(PrevEmploymentKind.INCOME, Money.ZERO);
    }

    public Money prevEmploymentTds() {
        return prevEmployment.getOrDefault(PrevEmploymentKind.INCOME_TAX_DEDUCTED, Money.ZERO);
    }
}
