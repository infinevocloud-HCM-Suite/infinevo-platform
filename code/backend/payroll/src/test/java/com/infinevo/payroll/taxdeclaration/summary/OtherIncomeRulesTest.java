package com.infinevo.payroll.taxdeclaration.summary;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.payroll.taxdeclaration.summary.dto.OtherIncomeRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OtherIncomeRulesTest {

    @Test
    @DisplayName("validate accepts valid other income items")
    void testValidOtherIncome() {
        List<OtherIncomeRequest> valid = List.of(
                new OtherIncomeRequest(OtherIncomeKind.SAVINGS_INTEREST, null, new BigDecimal("8500.0000")),
                new OtherIncomeRequest(OtherIncomeKind.FD_INTEREST, null, new BigDecimal("25000.0000")),
                new OtherIncomeRequest(OtherIncomeKind.OTHER, "Consulting fee", new BigDecimal("50000.0000")));

        OtherIncomeRules.validate(valid);
    }

    @Test
    @DisplayName("validate refuses duplicate kind")
    void testDuplicateKindRefused() {
        List<OtherIncomeRequest> duplicate = List.of(
                new OtherIncomeRequest(OtherIncomeKind.SAVINGS_INTEREST, null, new BigDecimal("5000.0000")),
                new OtherIncomeRequest(OtherIncomeKind.SAVINGS_INTEREST, null, new BigDecimal("3000.0000")));

        assertThatThrownBy(() -> OtherIncomeRules.validate(duplicate))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("Duplicate other income kind: SAVINGS_INTEREST");
    }

    @Test
    @DisplayName("validate refuses OTHER income without description")
    void testOtherWithoutDescriptionRefused() {
        List<OtherIncomeRequest> missingDesc =
                List.of(new OtherIncomeRequest(OtherIncomeKind.OTHER, null, new BigDecimal("10000.0000")));

        assertThatThrownBy(() -> OtherIncomeRules.validate(missingDesc))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("description is required for OTHER income");

        List<OtherIncomeRequest> blankDesc =
                List.of(new OtherIncomeRequest(OtherIncomeKind.OTHER, "   ", new BigDecimal("10000.0000")));

        assertThatThrownBy(() -> OtherIncomeRules.validate(blankDesc))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("description is required for OTHER income");
    }

    @Test
    @DisplayName("validate refuses negative amount")
    void testNegativeAmountRefused() {
        List<OtherIncomeRequest> negative =
                List.of(new OtherIncomeRequest(OtherIncomeKind.FD_INTEREST, null, new BigDecimal("-100.0000")));

        assertThatThrownBy(() -> OtherIncomeRules.validate(negative))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("amount must be non-negative");
    }

    @Test
    @DisplayName("validate accepts description up to 150 characters")
    void testDescriptionValidLength() {
        String exactly150 = "a".repeat(150);
        List<OtherIncomeRequest> valid =
                List.of(new OtherIncomeRequest(OtherIncomeKind.OTHER, exactly150, new BigDecimal("10000.0000")));
        OtherIncomeRules.validate(valid);
    }

    @Test
    @DisplayName("validate refuses description exceeding 150 characters")
    void testDescriptionOver150Refused() {
        String over150 = "a".repeat(151);
        List<OtherIncomeRequest> invalid =
                List.of(new OtherIncomeRequest(OtherIncomeKind.OTHER, over150, new BigDecimal("10000.0000")));

        assertThatThrownBy(() -> OtherIncomeRules.validate(invalid))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("Description must not exceed 150 characters");
    }
}
