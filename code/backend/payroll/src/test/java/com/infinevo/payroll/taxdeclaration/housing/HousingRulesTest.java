package com.infinevo.payroll.taxdeclaration.housing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.exception.ReferenceDataMissingException;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.payroll.taxdeclaration.housing.dto.HomeLoanRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.HouseRentRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.LetOutPropertyLineRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.LetOutPropertyRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class HousingRulesTest {

    private final FinancialYear fy2024 = FinancialYear.parse("2024-2025");

    @Test
    @DisplayName("isValidPan correctly validates Indian PAN format")
    void testPanValidation() {
        assertThat(HousingRules.isValidPan("ABCDE1234F")).isTrue();
        assertThat(HousingRules.isValidPan("AAAPA1234A")).isTrue();

        assertThat(HousingRules.isValidPan("abcde1234f")).isFalse();
        assertThat(HousingRules.isValidPan("ABCDE12345")).isFalse();
        assertThat(HousingRules.isValidPan("12345ABCDE")).isFalse();
        assertThat(HousingRules.isValidPan("ABCDE1234")).isFalse();
        assertThat(HousingRules.isValidPan("")).isFalse();
        assertThat(HousingRules.isValidPan(null)).isFalse();
    }

    @Test
    @DisplayName("parseYearMonth parses YYYY-MM correctly or throws WindowValidationException")
    void testParseYearMonth() {
        LocalDate parsed = HousingRules.parseYearMonth("2024-04", "from_month", 1);
        assertThat(parsed).isEqualTo(LocalDate.of(2024, 4, 1));

        assertThatThrownBy(() -> HousingRules.parseYearMonth("2024-13", "from_month", 1))
                .isInstanceOf(WindowValidationException.class);
        assertThatThrownBy(() -> HousingRules.parseYearMonth("", "from_month", 1))
                .isInstanceOf(WindowValidationException.class);
        assertThatThrownBy(() -> HousingRules.parseYearMonth(null, "from_month", 1))
                .isInstanceOf(WindowValidationException.class);
    }

    @Test
    @DisplayName("calculateMonthsInclusive calculates month span correctly")
    void testCalculateMonthsInclusive() {
        LocalDate apr = LocalDate.of(2024, 4, 1);
        LocalDate jun = LocalDate.of(2024, 6, 1);
        assertThat(HousingRules.calculateMonthsInclusive(apr, jun)).isEqualTo(3);
        assertThat(HousingRules.calculateMonthsInclusive(apr, apr)).isEqualTo(1);
    }

    @Test
    @DisplayName("validateHouseRent accepts non-overlapping periods within FY and valid landlord PAN")
    void testValidateHouseRentSuccess() {
        List<HouseRentRequest> rows = List.of(
                new HouseRentRequest(
                        "2024-04",
                        "2024-09",
                        "Flat 101, Mumbai",
                        "Landlord A",
                        "ABCDE1234F",
                        true,
                        new BigDecimal("20000.0000")),
                new HouseRentRequest(
                        "2024-10",
                        "2025-03",
                        "Flat 202, Pune",
                        "Landlord B",
                        "XYZPK9876Q",
                        false,
                        new BigDecimal("25000.0000")));

        HousingRules.validateHouseRent(rows, fy2024, true, new BigDecimal("100000.0000"));
    }

    @Test
    @DisplayName("validateHouseRent rejects dates outside the financial year")
    void testValidateHouseRentOutsideFy() {
        List<HouseRentRequest> rows = List.of(new HouseRentRequest(
                "2024-03", "2024-05", "Address", "Landlord", null, false, new BigDecimal("10000.0000")));

        assertThatThrownBy(() -> HousingRules.validateHouseRent(rows, fy2024, true, new BigDecimal("100000.0000")))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("must fall within financial year");
    }

    @Test
    @DisplayName("validateHouseRent rejects overlapping rent periods")
    void testValidateHouseRentOverlapping() {
        List<HouseRentRequest> rows = List.of(
                new HouseRentRequest(
                        "2024-04", "2024-08", "Address 1", "Landlord 1", null, false, new BigDecimal("10000.0000")),
                new HouseRentRequest(
                        "2024-07", "2024-12", "Address 2", "Landlord 2", null, false, new BigDecimal("12000.0000")));

        assertThatThrownBy(() -> HousingRules.validateHouseRent(rows, fy2024, true, new BigDecimal("100000.0000")))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("Rent periods overlap");
    }

    @Test
    @DisplayName("validateHouseRent enforces landlord PAN when total rent > 1,00,000 threshold")
    void testValidateHouseRentPanMandatoryOverThreshold() {
        // 6 months @ 20,000 = 1,20,000 (> 1,00,000 threshold)
        List<HouseRentRequest> rowsWithoutPan = List.of(new HouseRentRequest(
                "2024-04", "2024-09", "Address", "Landlord", null, true, new BigDecimal("20000.0000")));

        assertThatThrownBy(() ->
                        HousingRules.validateHouseRent(rowsWithoutPan, fy2024, true, new BigDecimal("100000.0000")))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("Landlord PAN is mandatory");

        // When PAN is provided, it passes
        List<HouseRentRequest> rowsWithPan = List.of(new HouseRentRequest(
                "2024-04", "2024-09", "Address", "Landlord", "ABCDE1234F", true, new BigDecimal("20000.0000")));

        HousingRules.validateHouseRent(rowsWithPan, fy2024, true, new BigDecimal("100000.0000"));
    }

    @Test
    @DisplayName("validateHouseRent honours the threshold passed for the year (spec §9: two years, two thresholds)")
    void testValidateHouseRentThresholdIsPerYear() {
        FinancialYear fy2025 = FinancialYear.parse("2025-2026");
        // 6 months @ 20,000 = 1,20,000 without a PAN
        List<HouseRentRequest> rows2024 = List.of(new HouseRentRequest(
                "2024-04", "2024-09", "Address", "Landlord", null, false, new BigDecimal("20000.0000")));
        List<HouseRentRequest> rows2025 = List.of(new HouseRentRequest(
                "2025-04", "2025-09", "Address", "Landlord", null, false, new BigDecimal("20000.0000")));

        // FY 2024-25 threshold 1,00,000: over it, PAN required
        assertThatThrownBy(() -> HousingRules.validateHouseRent(rows2024, fy2024, true, new BigDecimal("100000.0000")))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("100000.0000");

        // FY 2025-26 threshold 1,50,000: the same rent is under it, no PAN needed
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> HousingRules.validateHouseRent(rows2025, fy2025, true, new BigDecimal("150000.0000")));

        // And the reverse: the 2025-26 rows against the lower threshold are refused
        assertThatThrownBy(() -> HousingRules.validateHouseRent(rows2025, fy2025, true, new BigDecimal("100000.0000")))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("Landlord PAN is mandatory");
    }

    @Test
    @DisplayName("validateHouseRent rejects from_month after to_month (D-8)")
    void testValidateHouseRentFromAfterTo() {
        List<HouseRentRequest> rows = List.of(new HouseRentRequest(
                "2024-09", "2024-04", "Address", "Landlord", null, false, new BigDecimal("10000.0000")));

        assertThatThrownBy(() -> HousingRules.validateHouseRent(rows, fy2024, true, new BigDecimal("100000.0000")))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("must not be after to_month");
    }

    @Test
    @DisplayName("validateHouseRent skips landlord PAN requirement when panRequiredForRentOverThreshold is false (D-8)")
    void testValidateHouseRentPanThresholdSkippedWhenFlagOff() {
        // 6 months @ 20,000 = 1,20,000 (> 100,000 threshold), but panRequired is false
        List<HouseRentRequest> rowsWithoutPan = List.of(new HouseRentRequest(
                "2024-04", "2024-09", "Address", "Landlord", null, true, new BigDecimal("20000.0000")));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> HousingRules.validateHouseRent(rowsWithoutPan, fy2024, false, new BigDecimal("100000.0000")));
    }

    @Test
    @DisplayName(
            "Two house-rent rows individually under PAN threshold whose sum crosses threshold triggers PAN rule (D-8)")
    void testValidateHouseRentTwoRowsSumCrossesThreshold() {
        // Row 1: 5 months @ 15,000 = 75,000 (< 100,000)
        // Row 2: 5 months @ 10,000 = 50,000 (< 100,000)
        // Total = 125,000 (> 100,000 threshold)
        List<HouseRentRequest> rowsWithoutPan = List.of(
                new HouseRentRequest(
                        "2024-04", "2024-08", "Address 1", "Landlord 1", null, false, new BigDecimal("15000.0000")),
                new HouseRentRequest(
                        "2024-09", "2025-01", "Address 2", "Landlord 2", null, false, new BigDecimal("10000.0000")));

        assertThatThrownBy(() ->
                        HousingRules.validateHouseRent(rowsWithoutPan, fy2024, true, new BigDecimal("100000.0000")))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("Landlord PAN is mandatory");
    }

    @Test
    @DisplayName("Let-out property interest is uncapped and full interest is deducted (D-8)")
    void testLetOutPropertyUncappedInterest() {
        // Rent = 300,000, Tax = 0 -> NAV = 300,000
        // Standard deduction (30%) = 90,000
        // Net before interest = 210,000
        // Large interest = 500,000 (well beyond self-occupied 200,000 cap)
        // Expected net income/loss = 210,000 - 500,000 = -290,000
        BigDecimal annualRent = new BigDecimal("300000.0000");
        BigDecimal municipalTax = BigDecimal.ZERO;
        BigDecimal largeInterest = new BigDecimal("500000.0000");
        BigDecimal stdDedPct = new BigDecimal("30.00");

        BigDecimal netIncomeLoss =
                HousingRules.calculateNetIncomeLoss(annualRent, municipalTax, largeInterest, stdDedPct);

        assertThat(netIncomeLoss).isEqualByComparingTo(new BigDecimal("-290000.0000"));
    }

    @Test
    @DisplayName("validateHomeLoans validates non-negative amounts and valid lender PAN")
    void testValidateHomeLoans() {
        List<HomeLoanRequest> valid = List.of(new HomeLoanRequest(
                "HDFC Bank",
                "AAACH1234F",
                new BigDecimal("50000.0000"),
                new BigDecimal("150000.0000"),
                true,
                LocalDate.of(2023, 5, 15)));
        HousingRules.validateHomeLoans(valid);

        List<HomeLoanRequest> negative = List.of(new HomeLoanRequest(
                "HDFC Bank", "AAACH1234F", new BigDecimal("-100.0000"), new BigDecimal("150000.0000"), false, null));
        assertThatThrownBy(() -> HousingRules.validateHomeLoans(negative))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("principal_paid must be non-negative");

        List<HomeLoanRequest> invalidPan = List.of(new HomeLoanRequest(
                "HDFC Bank", "INVALID_PAN", new BigDecimal("10000.0000"), new BigDecimal("20000.0000"), false, null));
        assertThatThrownBy(() -> HousingRules.validateHomeLoans(invalidPan))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("lender_pan is invalid");
    }

    @Test
    @DisplayName("calculateNetIncomeLoss correctly calculates Section 24 property income/loss")
    void testCalculateNetIncomeLoss() {
        // Spec §7 example:
        // Annual rent = 3,00,000
        // Municipal tax = 20,000
        // Loan interest = 3,00,000
        // Std ded = 30%
        // NAV = 2,80,000 -> Std Ded = 84,000 -> Net = 2,80,000 - 84,000 - 3,00,000 = -1,04,000.0000
        BigDecimal netIncomeLoss = HousingRules.calculateNetIncomeLoss(
                new BigDecimal("300000.0000"),
                new BigDecimal("20000.0000"),
                new BigDecimal("300000.0000"),
                new BigDecimal("30.00"));

        assertThat(netIncomeLoss).isEqualByComparingTo(new BigDecimal("-104000.0000"));

        // Positive income example:
        // Rent = 2,40,000, Tax = 0, Interest = 0
        // NAV = 2,40,000 -> Std Ded (30%) = 72,000 -> Net = 1,68,000.0000
        BigDecimal positiveIncome = HousingRules.calculateNetIncomeLoss(
                new BigDecimal("240000.0000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("30.00"));

        assertThat(positiveIncome).isEqualByComparingTo(new BigDecimal("168000.0000"));
    }

    @Test
    @DisplayName("validateLetOutProperties rejects duplicate line types per property")
    void testValidateLetOutPropertiesDuplicateLines() {
        List<LetOutPropertyRequest> props = List.of(new LetOutPropertyRequest(
                "Green Villa",
                "123 Lake View",
                List.of(
                        new LetOutPropertyLineRequest(
                                LetOutPropertyLineType.ANNUAL_RENT, new BigDecimal("300000.0000"), null, null),
                        new LetOutPropertyLineRequest(
                                LetOutPropertyLineType.ANNUAL_RENT, new BigDecimal("50000.0000"), null, null))));

        assertThatThrownBy(() -> HousingRules.validateLetOutProperties(props))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("multiple lines of type ANNUAL_RENT");
    }

    @Test
    @DisplayName("HraRuleReader throws ReferenceDataMissingException when statutory rule is missing")
    void testHraRuleReaderThrowsWhenMissing() {
        JdbcTemplate mockJdbc = Mockito.mock(JdbcTemplate.class);
        Mockito.when(mockJdbc.query(
                        Mockito.anyString(), Mockito.<RowMapper<BigDecimal>>any(), Mockito.any(), Mockito.any()))
                .thenReturn(List.of());

        HraRuleReader reader = new HraRuleReader(mockJdbc);
        assertThatThrownBy(() -> reader.getPanMandatoryThreshold("2035-2036", "NEW"))
                .isInstanceOf(ReferenceDataMissingException.class)
                .hasMessageContaining("reference.hra_rule_master");
        assertThatThrownBy(() -> reader.getPanMandatoryThreshold("2026-2027", null))
                .isInstanceOf(ReferenceDataMissingException.class)
                .hasMessageContaining("reference.hra_rule_master");
        assertThatThrownBy(() -> reader.getPanMandatoryThreshold(null, "OLD"))
                .isInstanceOf(ReferenceDataMissingException.class)
                .hasMessageContaining("reference.hra_rule_master");
    }

    @Test
    @DisplayName("LetOutPropertyRuleReader throws ReferenceDataMissingException when statutory rule is missing")
    void testLetOutPropertyRuleReaderThrowsWhenMissing() {
        JdbcTemplate mockJdbc = Mockito.mock(JdbcTemplate.class);
        Mockito.when(mockJdbc.query(
                        Mockito.anyString(), Mockito.<RowMapper<BigDecimal>>any(), Mockito.any(), Mockito.any()))
                .thenReturn(List.of());

        LetOutPropertyRuleReader reader = new LetOutPropertyRuleReader(mockJdbc);
        assertThatThrownBy(() -> reader.getStandardDeductionPercent("2035-2036", "NEW"))
                .isInstanceOf(ReferenceDataMissingException.class)
                .hasMessageContaining("reference.let_out_property_rule_master");
        assertThatThrownBy(() -> reader.getStandardDeductionPercent("2026-2027", null))
                .isInstanceOf(ReferenceDataMissingException.class)
                .hasMessageContaining("reference.let_out_property_rule_master");
        assertThatThrownBy(() -> reader.getStandardDeductionPercent(null, "OLD"))
                .isInstanceOf(ReferenceDataMissingException.class)
                .hasMessageContaining("reference.let_out_property_rule_master");
    }
}
