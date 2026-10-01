package com.infinevo.payroll.taxcalc.model;

import com.infinevo.payroll.taxcalc.AgeCategory;
import com.infinevo.payroll.taxcalc.engine.ChapterViaDeductions.DeclaredItem;
import com.infinevo.payroll.taxcalc.engine.HraExemption.HraMonthSalary;
import com.infinevo.payroll.taxdeclaration.deductions.PreTaxDeductionKind;
import com.infinevo.payroll.taxdeclaration.deductions.PrevEmploymentKind;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoan;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRent;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutProperty;
import com.infinevo.payroll.taxdeclaration.summary.EmployeeInvOtherIncome;
import com.infinevo.shared.money.Money;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Input aggregated across employee and payroll records for tax calculation (W-33.1 spec ? 3, ? 4, W-33.2 spec ? 3).
 *
 * @param employeeId the employee identifier
 * @param declarationId the declaration header identifier
 * @param salary projected salary result from active CTC versions
 * @param prevEmployment declared income and deductions from previous employers by kind
 * @param ageCategory statutory age category as of March 31
 * @param isStayingInRentedHouse flag indicating whether employee declared staying in rented house
 * @param isRepayingSelfOccupiedLoan flag indicating whether employee declared repaying self-occupied loan
 * @param hasLetOutProperty flag indicating whether employee declared let-out property
 * @param houseRent declared house rent lines
 * @param homeLoans declared home loan lines
 * @param letOutProperties declared let-out property lines
 * @param section6A declared Section 6A deduction items with master caps
 * @param preTaxDeductions declared pre-tax deductions by kind
 * @param otherIncome declared other income rows
 * @param epfFromCtc optional employee EPF calculated from CTC structure versions
 * @param professionalTaxFromService optional professional tax resolved from service
 * @param hraSalaries monthly basic and HRA breakdown for Section 10(13A) exemption
 */
public record TaxInput(
        UUID employeeId,
        UUID declarationId,
        SalaryProjectionResult salary,
        Map<PrevEmploymentKind, Money> prevEmployment,
        AgeCategory ageCategory,
        boolean isStayingInRentedHouse,
        boolean isRepayingSelfOccupiedLoan,
        boolean hasLetOutProperty,
        List<EmployeeInvHouseRent> houseRent,
        List<EmployeeInvHomeLoan> homeLoans,
        List<EmployeeInvLetOutProperty> letOutProperties,
        List<DeclaredItem> section6A,
        Map<PreTaxDeductionKind, Money> preTaxDeductions,
        List<EmployeeInvOtherIncome> otherIncome,
        Optional<Money> epfFromCtc,
        Optional<Money> professionalTaxFromService,
        List<HraMonthSalary> hraSalaries) {

    public TaxInput {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(declarationId, "declarationId must not be null");
        Objects.requireNonNull(salary, "salary must not be null");
        prevEmployment = prevEmployment == null ? Map.of() : Map.copyOf(prevEmployment);
        ageCategory = ageCategory == null ? AgeCategory.GENERAL : ageCategory;
        houseRent = houseRent == null ? List.of() : List.copyOf(houseRent);
        homeLoans = homeLoans == null ? List.of() : List.copyOf(homeLoans);
        letOutProperties = letOutProperties == null ? List.of() : List.copyOf(letOutProperties);
        section6A = section6A == null ? List.of() : List.copyOf(section6A);
        preTaxDeductions = preTaxDeductions == null ? Map.of() : Map.copyOf(preTaxDeductions);
        otherIncome = otherIncome == null ? List.of() : List.copyOf(otherIncome);
        epfFromCtc = epfFromCtc == null ? Optional.empty() : epfFromCtc;
        professionalTaxFromService = professionalTaxFromService == null ? Optional.empty() : professionalTaxFromService;
        hraSalaries = hraSalaries == null ? List.of() : List.copyOf(hraSalaries);
    }

    public TaxInput(
            UUID employeeId,
            UUID declarationId,
            SalaryProjectionResult salary,
            Map<PrevEmploymentKind, Money> prevEmployment,
            AgeCategory ageCategory,
            boolean isStayingInRentedHouse,
            boolean isRepayingSelfOccupiedLoan,
            boolean hasLetOutProperty,
            List<EmployeeInvHouseRent> houseRent,
            List<EmployeeInvHomeLoan> homeLoans,
            List<EmployeeInvLetOutProperty> letOutProperties,
            List<DeclaredItem> section6A,
            Map<PreTaxDeductionKind, Money> preTaxDeductions,
            List<EmployeeInvOtherIncome> otherIncome,
            Optional<Money> epfFromCtc,
            Optional<Money> professionalTaxFromService) {
        this(
                employeeId,
                declarationId,
                salary,
                prevEmployment,
                ageCategory,
                isStayingInRentedHouse,
                isRepayingSelfOccupiedLoan,
                hasLetOutProperty,
                houseRent,
                homeLoans,
                letOutProperties,
                section6A,
                preTaxDeductions,
                otherIncome,
                epfFromCtc,
                professionalTaxFromService,
                List.of());
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
                false,
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of());
    }

    public Money prevEmploymentIncome() {
        return prevEmployment.getOrDefault(PrevEmploymentKind.INCOME, Money.ZERO);
    }

    public Money prevEmploymentTds() {
        return prevEmployment.getOrDefault(PrevEmploymentKind.INCOME_TAX_DEDUCTED, Money.ZERO);
    }

    public Money resolvedEmployeePf() {
        return epfFromCtc.orElseGet(() -> preTaxDeductions.getOrDefault(PreTaxDeductionKind.EMPLOYEE_PF, Money.ZERO));
    }

    public Money resolvedProfessionalTax() {
        return professionalTaxFromService.orElseGet(
                () -> preTaxDeductions.getOrDefault(PreTaxDeductionKind.PROFESSIONAL_TAX, Money.ZERO));
    }

    public Money vpf() {
        return preTaxDeductions.getOrDefault(PreTaxDeductionKind.VPF, Money.ZERO);
    }

    public Money employeeNps() {
        return preTaxDeductions.getOrDefault(PreTaxDeductionKind.NPS_EMPLOYEE, Money.ZERO);
    }
}
