package com.infinevo.payroll.form16;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.taxcalc.recalc.TaxComputationRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Form16AssemblerTest {

    private static final String FY = "2026-2027";
    private static final DeductorDetails DEDUCTOR = new DeductorDetails(
            "Acme Corp", "ABCD12345E", "ABCDE1234F", "CIT/TDS/001/01", "Jane Doe", "John Doe", "Director");
    private static final EmployeeDetails EMPLOYEE = new EmployeeDetails("Alice Smith", "PQRST1234K", "Engineer");

    @Test
    @DisplayName("Assessment year computation: 2026-2027 -> 2027-2028")
    void testAssessmentYear() {
        String ay = Form16Assembler.calculateAssessmentYear("2026-2027");
        assertThat(ay).isEqualTo("2027-2028");
    }

    @Test
    @DisplayName(
            "Hand calculation: April-December 10k/mo -> Q1=30k, Q2=30k, Q3=30k, Q4=0, deducted=90k, balance=30k, final=false")
    void testHandCalculationNineMonthsPaid() {
        BigDecimal annualTax = new BigDecimal("120000.00");
        Map<String, BigDecimal> periodMap = new HashMap<>();
        // April-December
        for (int m = 4; m <= 12; m++) {
            String period = String.format("2026-%02d", m);
            periodMap.put(period, new BigDecimal("10000.00"));
        }

        Form16Statement statement =
                Form16Assembler.assemble(FY, DEDUCTOR, EMPLOYEE, "NEW", annualTax, periodMap, null, 9L, Instant.now());

        assertThat(statement.quarters()).hasSize(4);
        assertThat(statement.quarters().get(0).amountDeducted()).isEqualByComparingTo("30000.00");
        assertThat(statement.quarters().get(1).amountDeducted()).isEqualByComparingTo("30000.00");
        assertThat(statement.quarters().get(2).amountDeducted()).isEqualByComparingTo("30000.00");
        assertThat(statement.quarters().get(3).amountDeducted()).isEqualByComparingTo("0.00");

        assertThat(statement.totalDeducted()).isEqualByComparingTo("90000.00");
        assertThat(statement.annualTax()).isEqualByComparingTo("120000.00");
        assertThat(statement.balance()).isEqualByComparingTo("30000.00");
        assertThat(statement.isFinal()).isFalse();
    }

    @Test
    @DisplayName("Full year 12 months paid: add Jan-Mar 10k/mo -> Q4=30k, balance=0, final=true")
    void testFullYearTwelveMonthsPaid() {
        BigDecimal annualTax = new BigDecimal("120000.00");
        Map<String, BigDecimal> periodMap = new HashMap<>();
        for (int m = 4; m <= 12; m++) {
            periodMap.put(String.format("2026-%02d", m), new BigDecimal("10000.00"));
        }
        for (int m = 1; m <= 3; m++) {
            periodMap.put(String.format("2027-%02d", m), new BigDecimal("10000.00"));
        }

        Form16Statement statement =
                Form16Assembler.assemble(FY, DEDUCTOR, EMPLOYEE, "NEW", annualTax, periodMap, null, 12L, Instant.now());

        assertThat(statement.quarters().get(3).amountDeducted()).isEqualByComparingTo("30000.00");
        assertThat(statement.totalDeducted()).isEqualByComparingTo("120000.00");
        assertThat(statement.balance()).isEqualByComparingTo("0.00");
        assertThat(statement.isFinal()).isTrue();
    }

    @Test
    @DisplayName(
            "Discrepancy override: computation annual tax 118,000 vs record 120,000 -> breakdown null and OFFICER_OVERRIDE")
    void testDiscrepancyProducesOfficerOverrideNote() {
        BigDecimal annualTax = new BigDecimal("120000.00");

        // mock computation record with 118,000
        TaxComputationRecord comp = org.mockito.Mockito.mock(TaxComputationRecord.class);
        org.mockito.Mockito.when(comp.getAnnualTax()).thenReturn(new BigDecimal("118000.00"));

        Form16Statement statement =
                Form16Assembler.assemble(FY, DEDUCTOR, EMPLOYEE, "NEW", annualTax, Map.of(), comp, 0L, Instant.now());

        assertThat(statement.breakdown()).isNull();
        assertThat(statement.breakdownNote()).isEqualTo("OFFICER_OVERRIDE");
    }

    @Test
    @DisplayName("Half up scale 2 rounding: 1,234.565 prints 1,234.57")
    void testHalfUpRounding() {
        QuarterTax qt = new QuarterTax("Q1", new BigDecimal("1234.565"));
        assertThat(qt.amountDeducted()).isEqualTo(new BigDecimal("1234.57"));
    }
}
