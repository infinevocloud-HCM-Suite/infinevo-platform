package com.infinevo.payroll.form16;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.taxcalc.recalc.TaxComputationRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    @DisplayName("No computation at all: breakdown null and NO_COMPUTATION, not an officer override (D-149)")
    void testNoComputationHasItsOwnNote() {
        Form16Statement statement = Form16Assembler.assemble(
                FY, DEDUCTOR, EMPLOYEE, "NEW", new BigDecimal("120000.00"), Map.of(), null, 0L, Instant.now());

        assertThat(statement.breakdown()).isNull();
        assertThat(statement.breakdownNote()).isEqualTo("NO_COMPUTATION");
    }

    @Test
    @DisplayName(
            "New regime working adds up: salary 1,400,000 + let-out 60,000 + other 40,000, standard 75,000 (D-149)")
    void testNewRegimeBreakdownAddsUp() {
        // The new-regime calculator stores gross total income before the standard deduction.
        TaxComputationRecord comp = computation("NEW", "1500000", "0", "75000", "0", "60000", "40000", "0", "1425000");

        TaxBreakdown b = Form16Assembler.breakdownOf(comp);

        assertThat(b.grossSalary()).isEqualByComparingTo("1400000");
        assertThat(b.incomeFromSalary()).isEqualByComparingTo("1325000");
        assertThat(b.grossTotalIncome()).isEqualByComparingTo("1425000");
        assertAddsUp(b);
    }

    @Test
    @DisplayName(
            "Old regime working adds up: stored total 1,170,000 is after HRA 120,000, standard 50,000 and PT 2,400 (D-149)")
    void testOldRegimeBreakdownAddsUp() {
        // The old-regime calculator stores gross total income after the section 16 deductions.
        // Salary 1,300,000 - 120,000 - 50,000 - 2,400 = 1,127,600; - 7,600 house property + 50,000 other.
        TaxComputationRecord comp =
                computation("OLD", "1170000", "120000", "50000", "2400", "-7600", "50000", "150000", "1020000");

        TaxBreakdown b = Form16Assembler.breakdownOf(comp);

        assertThat(b.grossSalary()).isEqualByComparingTo("1300000");
        assertThat(b.incomeFromSalary()).isEqualByComparingTo("1127600");
        assertThat(b.grossTotalIncome()).isEqualByComparingTo("1170000");
        assertAddsUp(b);
    }

    @Test
    @DisplayName(
            "Old regime with 80TTA: stored Chapter VI-A is 150,000 but 160,000 was taken off; the line prints 160,000 (D-149)")
    void testInterestDeductionIsInsideTheDeductionsLine() {
        // Gross total income 1,000,000; 80C 150,000 stored; 80TTA 10,000 not stored; taxable 840,000.
        TaxComputationRecord comp = computation("OLD", "1000000", "0", "50000", "0", "0", "10000", "150000", "840000");

        TaxBreakdown b = Form16Assembler.breakdownOf(comp);

        assertThat(b.chapterVia()).isEqualByComparingTo("160000");
        assertAddsUp(b);
    }

    @Test
    @DisplayName("Rounding to the rupee alone leaves the stored Chapter VI-A figure as it is (D-149)")
    void testRupeeRoundingDoesNotChangeTheDeductionsLine() {
        TaxComputationRecord comp = computation("OLD", "1000000.40", "0", "50000", "0", "0", "0", "150000", "850000");

        assertThat(Form16Assembler.breakdownOf(comp).chapterVia()).isEqualByComparingTo("150000");
    }

    /** Each printed line follows from the one above. */
    private static void assertAddsUp(TaxBreakdown b) {
        assertThat(b.grossSalary()
                        .subtract(b.hraExemption())
                        .subtract(b.standardDeduction())
                        .subtract(b.professionalTax()))
                .isEqualByComparingTo(b.incomeFromSalary());
        assertThat(b.incomeFromSalary().add(b.housePropertyIncome()).add(b.otherIncome()))
                .isEqualByComparingTo(b.grossTotalIncome());
        assertThat(b.grossTotalIncome().subtract(b.chapterVia())).isEqualByComparingTo(b.taxableIncome());
    }

    private static TaxComputationRecord computation(
            String regime,
            String storedGrossTotal,
            String hra,
            String standard,
            String professionalTax,
            String houseProperty,
            String other,
            String chapterVia,
            String taxable) {
        TaxComputationRecord comp = org.mockito.Mockito.mock(TaxComputationRecord.class);
        org.mockito.Mockito.when(comp.getRegime()).thenReturn(regime);
        org.mockito.Mockito.when(comp.getGrossTotalIncome()).thenReturn(new BigDecimal(storedGrossTotal));
        org.mockito.Mockito.when(comp.getHraExemption()).thenReturn(new BigDecimal(hra));
        org.mockito.Mockito.when(comp.getStandardDeduction()).thenReturn(new BigDecimal(standard));
        org.mockito.Mockito.when(comp.getProfessionalTax()).thenReturn(new BigDecimal(professionalTax));
        org.mockito.Mockito.when(comp.getHousePropertyIncome()).thenReturn(new BigDecimal(houseProperty));
        org.mockito.Mockito.when(comp.getOtherIncome()).thenReturn(new BigDecimal(other));
        org.mockito.Mockito.when(comp.getChapterVia()).thenReturn(new BigDecimal(chapterVia));
        org.mockito.Mockito.when(comp.getTaxableIncome()).thenReturn(new BigDecimal(taxable));
        return comp;
    }

    @Test
    @DisplayName("Half up scale 2 rounding: 1,234.565 prints 1,234.57")
    void testHalfUpRounding() {
        QuarterTax qt = new QuarterTax("Q1", new BigDecimal("1234.565"));
        assertThat(qt.amountDeducted()).isEqualTo(new BigDecimal("1234.57"));
    }

    @Test
    @DisplayName("W-38.2: imported Apr-Sep 10k/mo + PAID Oct-Mar 10k/mo -> 30k per quarter, balance 0, final=true")
    void importedMonthsJoinTheirQuarterAndCountAsCovered() {
        Map<String, BigDecimal> imported = new HashMap<>();
        Set<String> importedPeriods = new HashSet<>();
        for (int m = 4; m <= 9; m++) {
            String period = String.format("2026-%02d", m);
            imported.put(period, new BigDecimal("10000.0000"));
            importedPeriods.add(period);
        }
        Map<String, BigDecimal> paid = new HashMap<>();
        Set<String> paidPeriods = new HashSet<>();
        for (String period : new String[] {"2026-10", "2026-11", "2026-12", "2027-01", "2027-02", "2027-03"}) {
            paid.put(period, new BigDecimal("10000.0000"));
            paidPeriods.add(period);
        }

        Form16Statement statement = Form16Assembler.assemble(
                FY,
                DEDUCTOR,
                EMPLOYEE,
                "NEW",
                new BigDecimal("120000.00"),
                paid,
                imported,
                null,
                paidPeriods,
                importedPeriods,
                Instant.now());

        assertThat(statement.quarters().get(0).amountDeducted()).isEqualByComparingTo("30000.00");
        assertThat(statement.quarters().get(1).amountDeducted()).isEqualByComparingTo("30000.00");
        assertThat(statement.quarters().get(2).amountDeducted()).isEqualByComparingTo("30000.00");
        assertThat(statement.quarters().get(3).amountDeducted()).isEqualByComparingTo("30000.00");
        assertThat(statement.totalDeducted()).isEqualByComparingTo("120000.00");
        assertThat(statement.balance()).isEqualByComparingTo("0.00");
        assertThat(statement.isFinal()).isTrue();
    }

    @Test
    @DisplayName("W-38.2: a month neither imported nor paid -> final=false")
    void monthNeitherImportedNorPaidIsNotFinal() {
        Map<String, BigDecimal> imported = new HashMap<>();
        Set<String> importedPeriods = new HashSet<>();
        for (int m = 4; m <= 9; m++) {
            String period = String.format("2026-%02d", m);
            imported.put(period, new BigDecimal("10000.0000"));
            importedPeriods.add(period);
        }
        Map<String, BigDecimal> paid = new HashMap<>();
        Set<String> paidPeriods = new HashSet<>();
        // 2027-03 missing
        for (String period : new String[] {"2026-10", "2026-11", "2026-12", "2027-01", "2027-02"}) {
            paid.put(period, new BigDecimal("10000.0000"));
            paidPeriods.add(period);
        }
        // A period outside the year never counts towards coverage
        paidPeriods.add("2027-04");

        Form16Statement statement = Form16Assembler.assemble(
                FY,
                DEDUCTOR,
                EMPLOYEE,
                "NEW",
                new BigDecimal("120000.00"),
                paid,
                imported,
                null,
                paidPeriods,
                importedPeriods,
                Instant.now());

        assertThat(statement.quarters().get(3).amountDeducted()).isEqualByComparingTo("20000.00");
        assertThat(statement.balance()).isEqualByComparingTo("10000.00");
        assertThat(statement.isFinal()).isFalse();
    }
}
