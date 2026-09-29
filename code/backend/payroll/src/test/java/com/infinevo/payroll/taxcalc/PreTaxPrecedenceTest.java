package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.taxcalc.model.SalaryProjectionResult;
import com.infinevo.payroll.taxcalc.model.TaxInput;
import com.infinevo.payroll.taxdeclaration.deductions.PreTaxDeductionKind;
import com.infinevo.shared.money.Money;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Pre-Tax deduction precedence rules (W-33.2 spec § 3a, § 7).
 */
class PreTaxPrecedenceTest {

    private final UUID employeeId = UUID.randomUUID();
    private final UUID declarationId = UUID.randomUUID();
    private final SalaryProjectionResult salary = new SalaryProjectionResult(Money.of("1000000"), List.of(), List.of());

    @Test
    @DisplayName("EPF precedence: CTC line present and declared row present -> CTC line wins")
    void epfCtcLineWinsOverDeclaredRow() {
        TaxInput input = new TaxInput(
                employeeId,
                declarationId,
                salary,
                Map.of(),
                AgeCategory.GENERAL,
                false,
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(PreTaxDeductionKind.EMPLOYEE_PF, Money.of("40000")),
                List.of(),
                Optional.of(Money.of("45000")), // CTC line
                Optional.empty(),
                List.of());

        assertThat(input.resolvedEmployeePf()).isEqualTo(Money.of("45000"));
    }

    @Test
    @DisplayName("EPF precedence: CTC line absent -> declared row fallback")
    void epfDeclaredRowFallback() {
        TaxInput input = new TaxInput(
                employeeId,
                declarationId,
                salary,
                Map.of(),
                AgeCategory.GENERAL,
                false,
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(PreTaxDeductionKind.EMPLOYEE_PF, Money.of("40000")),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of());

        assertThat(input.resolvedEmployeePf()).isEqualTo(Money.of("40000"));
    }

    @Test
    @DisplayName("EPF precedence: neither present -> returns ZERO")
    void epfNeitherPresentYieldsZero() {
        TaxInput input = new TaxInput(
                employeeId,
                declarationId,
                salary,
                Map.of(),
                AgeCategory.GENERAL,
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

        assertThat(input.resolvedEmployeePf()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("Professional Tax precedence: service present -> service wins over declared row")
    void ptServiceWinsOverDeclaredRow() {
        TaxInput input = new TaxInput(
                employeeId,
                declarationId,
                salary,
                Map.of(),
                AgeCategory.GENERAL,
                false,
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(PreTaxDeductionKind.PROFESSIONAL_TAX, Money.of("2400")),
                List.of(),
                Optional.empty(),
                Optional.of(Money.of("2500")), // Service
                List.of());

        assertThat(input.resolvedProfessionalTax()).isEqualTo(Money.of("2500"));
    }
}
