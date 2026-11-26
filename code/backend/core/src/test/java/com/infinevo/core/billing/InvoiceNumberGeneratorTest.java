package com.infinevo.core.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvoiceNumberGeneratorTest {

    @Test
    void indianFinancialYearBoundaries() {
        assertThat(InvoiceNumberGenerator.financialYear(LocalDate.of(2026, 3, 31)))
                .isEqualTo("2025-26");
        assertThat(InvoiceNumberGenerator.financialYear(LocalDate.of(2026, 4, 1)))
                .isEqualTo("2026-27");
        assertThat(InvoiceNumberGenerator.financialYear(LocalDate.of(2026, 11, 26)))
                .isEqualTo("2026-27");
        assertThat(InvoiceNumberGenerator.financialYear(LocalDate.of(2027, 1, 15)))
                .isEqualTo("2026-27");
    }
}
