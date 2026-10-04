package com.infinevo.payroll.priorpayroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.payroll.taxdeclaration.FinancialYear;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PriorPayrollTaxQuery} (W-38.2 §4). */
class PriorPayrollTaxQueryTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final FinancialYear FY = FinancialYear.parse("2026-2027");

    private PriorPayrollMonthRepository repository;
    private PriorPayrollTaxQuery query;

    @BeforeEach
    void setUp() {
        repository = mock(PriorPayrollMonthRepository.class);
        query = new PriorPayrollTaxQuery(repository);
    }

    @Test
    @DisplayName("total reads April..March of the year in one aggregate")
    void totalReadsTheWholeYear() {
        when(repository.sumTds(TENANT, EMPLOYEE, "2026-04", "2027-03")).thenReturn(new BigDecimal("60000.0000"));

        assertThat(query.total(TENANT, EMPLOYEE, FY)).isEqualByComparingTo("60000.0000");
    }

    @Test
    @DisplayName("nothing imported: total is 0 and byPeriod and importedPeriods are empty")
    void nothingImported() {
        when(repository.sumTds(TENANT, EMPLOYEE, "2026-04", "2027-03")).thenReturn(null);
        when(repository.sumTdsByPeriod(TENANT, EMPLOYEE, "2026-04", "2027-03")).thenReturn(List.of());
        when(repository.findDistinctPeriods(TENANT, "2026-04", "2027-03")).thenReturn(List.of());

        assertThat(query.total(TENANT, EMPLOYEE, FY)).isEqualByComparingTo("0");
        assertThat(query.byPeriod(TENANT, EMPLOYEE, FY)).isEmpty();
        assertThat(query.importedPeriods(TENANT, FY)).isEmpty();
    }

    @Test
    @DisplayName("byPeriod maps each period to its imported TDS")
    void byPeriodMapsEachPeriod() {
        when(repository.sumTdsByPeriod(TENANT, EMPLOYEE, "2026-04", "2027-03"))
                .thenReturn(List.of(row("2026-04", "10000.0000"), row("2026-05", "12500.5000")));
        when(repository.findDistinctPeriods(TENANT, "2026-04", "2027-03")).thenReturn(List.of("2026-04", "2026-05"));

        assertThat(query.byPeriod(TENANT, EMPLOYEE, FY))
                .containsOnlyKeys("2026-04", "2026-05")
                .hasEntrySatisfying("2026-05", v -> assertThat(v).isEqualByComparingTo("12500.5000"));
        assertThat(query.importedPeriods(TENANT, FY)).containsExactly("2026-04", "2026-05");
    }

    private static PriorPayrollMonthRepository.PeriodTds row(String period, String tds) {
        return new PriorPayrollMonthRepository.PeriodTds() {
            @Override
            public String getPeriod() {
                return period;
            }

            @Override
            public BigDecimal getTds() {
                return new BigDecimal(tds);
            }
        };
    }
}
