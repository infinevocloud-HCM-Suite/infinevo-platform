package com.infinevo.payroll.taxdeclaration.deductions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.infinevo.payroll.taxdeclaration.deductions.Section6AItemReader.Section6AItem;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PreTaxDeductionRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PrevEmploymentRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.Section6ALineRequest;
import com.infinevo.payroll.taxdeclaration.exception.ReferenceDataMissingException;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class DeductionRulesTest {

    private Section6AItemReader itemReader;

    private final UUID item80cId = UUID.randomUUID();
    private final UUID item80cccId = UUID.randomUUID();
    private final UUID item80dId = UUID.randomUUID();
    private final UUID item80ccd2Id = UUID.randomUUID();
    private final UUID inactiveItemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        itemReader = mock(Section6AItemReader.class);

        Section6AItem item80c = new Section6AItem(
                item80cId,
                "80C",
                "INVESTMENT",
                "Investments under Section 80C",
                "PPF, LIC, ELSS",
                new BigDecimal("150000.0000"),
                "80C_GROUP",
                true,
                false,
                false,
                false,
                1,
                true);
        Section6AItem item80ccc = new Section6AItem(
                item80cccId,
                "80CCC",
                "INVESTMENT",
                "Pension fund contribution",
                "Annuity plan",
                new BigDecimal("150000.0000"),
                "80C_GROUP",
                true,
                false,
                false,
                false,
                2,
                true);
        Section6AItem item80d = new Section6AItem(
                item80dId,
                "80D",
                "HEALTH_INSURANCE",
                "Health insurance premium",
                "Mediclaim",
                new BigDecimal("100000.0000"),
                null,
                false,
                true,
                false,
                false,
                6,
                true);
        Section6AItem item80ccd2 = new Section6AItem(
                item80ccd2Id,
                "80CCD(2)",
                "INVESTMENT",
                "NPS employer contribution",
                "Employer share",
                null,
                null,
                false,
                false,
                true,
                true,
                5,
                true);
        Section6AItem inactiveItem = new Section6AItem(
                inactiveItemId,
                "INACTIVE_SEC",
                "OTHER",
                "Inactive Section",
                "Legacy",
                new BigDecimal("50000.0000"),
                null,
                false,
                false,
                true,
                false,
                99,
                false);

        given(itemReader.require(item80cId)).willReturn(item80c);
        given(itemReader.require(item80cccId)).willReturn(item80ccc);
        given(itemReader.require(item80dId)).willReturn(item80d);
        given(itemReader.require(item80ccd2Id)).willReturn(item80ccd2);
        given(itemReader.require(inactiveItemId)).willReturn(inactiveItem);

        given(itemReader.groupCap("80C_GROUP")).willReturn(new BigDecimal("150000.0000"));
    }

    @Test
    @DisplayName("isValidTan validates TAN format")
    void testTanValidation() {
        assertThat(DeductionRules.isValidTan("MUMB12345F")).isTrue();
        assertThat(DeductionRules.isValidTan("DELH98765A")).isTrue();

        assertThat(DeductionRules.isValidTan("mumb12345f")).isFalse();
        assertThat(DeductionRules.isValidTan("MUM12345F")).isFalse();
        assertThat(DeductionRules.isValidTan("12345MUMBF")).isFalse();
        assertThat(DeductionRules.isValidTan("")).isFalse();
        assertThat(DeductionRules.isValidTan(null)).isFalse();
    }

    @Test
    @DisplayName("validateSection6A refuses inactive catalogue item")
    void testInactiveItemRefused() {
        List<Section6ALineRequest> rows =
                List.of(new Section6ALineRequest(inactiveItemId, "Some policy", new BigDecimal("10000.0000")));

        assertThatThrownBy(() -> DeductionRules.validateSection6A(rows, "OLD", itemReader))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("is inactive");
    }

    @Test
    @DisplayName("validateSection6A refuses item not allowed in NEW regime when header is NEW")
    void testRegimeRestriction() {
        List<Section6ALineRequest> rows =
                List.of(new Section6ALineRequest(item80cId, "PPF Deposit", new BigDecimal("50000.0000")));

        // Refused in NEW regime
        assertThatThrownBy(() -> DeductionRules.validateSection6A(rows, "NEW", itemReader))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("not allowed under the NEW tax regime");

        // Allowed in OLD regime
        DeductionRules.validateSection6A(rows, "OLD", itemReader);

        // 80CCD(2) allowed in NEW regime
        List<Section6ALineRequest> npsRows =
                List.of(new Section6ALineRequest(item80ccd2Id, "NPS Employer", new BigDecimal("75000.0000")));
        DeductionRules.validateSection6A(npsRows, "NEW", itemReader);
    }

    @Test
    @DisplayName("validateSection6A refuses two 80C rows summing over 1,50,000 with limit in message")
    void testItemLimitEnforcedAcrossMultipleRows() {
        List<Section6ALineRequest> rows = List.of(
                new Section6ALineRequest(item80cId, "LIC Policy 1", new BigDecimal("100000.0000")),
                new Section6ALineRequest(item80cId, "PPF Deposit", new BigDecimal("70000.0000")));

        assertThatThrownBy(() -> DeductionRules.validateSection6A(rows, "OLD", itemReader))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("exceeds statutory limit (150000.0000)");
    }

    @Test
    @DisplayName("validateSection6A refuses 80C 1,00,000 plus 80CCC 1,00,000 on the group umbrella cap")
    void testGroupUmbrellaCapEnforced() {
        List<Section6ALineRequest> rows = List.of(
                new Section6ALineRequest(item80cId, "LIC Policy", new BigDecimal("100000.0000")),
                new Section6ALineRequest(item80cccId, "Annuity Plan", new BigDecimal("100000.0000")));

        assertThatThrownBy(() -> DeductionRules.validateSection6A(rows, "OLD", itemReader))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("exceeds group cap (150000.0000)");
    }

    @Test
    @DisplayName("validateSection6A requires non-blank description and non-negative amount")
    void testRowValidation() {
        List<Section6ALineRequest> blankDesc =
                List.of(new Section6ALineRequest(item80cId, "   ", new BigDecimal("10000.0000")));
        assertThatThrownBy(() -> DeductionRules.validateSection6A(blankDesc, "OLD", itemReader))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("description is required");

        List<Section6ALineRequest> negativeAmt =
                List.of(new Section6ALineRequest(item80cId, "LIC", new BigDecimal("-500.0000")));
        assertThatThrownBy(() -> DeductionRules.validateSection6A(negativeAmt, "OLD", itemReader))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("amount must be non-negative");
    }

    @Test
    @DisplayName("validatePreTaxDeductions refuses duplicate kinds and negative amounts")
    void testPreTaxDeductionValidation() {
        List<PreTaxDeductionRequest> valid = List.of(
                new PreTaxDeductionRequest(PreTaxDeductionKind.VPF, new BigDecimal("20000.0000")),
                new PreTaxDeductionRequest(PreTaxDeductionKind.NPS_EMPLOYEE, new BigDecimal("50000.0000")));
        DeductionRules.validatePreTaxDeductions(valid);

        List<PreTaxDeductionRequest> duplicate = List.of(
                new PreTaxDeductionRequest(PreTaxDeductionKind.VPF, new BigDecimal("20000.0000")),
                new PreTaxDeductionRequest(PreTaxDeductionKind.VPF, new BigDecimal("10000.0000")));
        assertThatThrownBy(() -> DeductionRules.validatePreTaxDeductions(duplicate))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("Duplicate pre-tax deduction kind: VPF");
    }

    @Test
    @DisplayName("validatePrevEmployment refuses duplicate kinds and invalid TAN")
    void testPrevEmploymentValidation() {
        List<PrevEmploymentRequest> valid = List.of(
                new PrevEmploymentRequest(
                        PrevEmploymentKind.INCOME, new BigDecimal("500000.0000"), "Old Corp", "MUMB12345F"),
                new PrevEmploymentRequest(
                        PrevEmploymentKind.INCOME_TAX_DEDUCTED,
                        new BigDecimal("45000.0000"),
                        "Old Corp",
                        "MUMB12345F"));
        DeductionRules.validatePrevEmployment(valid);

        List<PrevEmploymentRequest> invalidTan = List.of(new PrevEmploymentRequest(
                PrevEmploymentKind.INCOME, new BigDecimal("500000.0000"), "Old Corp", "INVALID_TAN"));
        assertThatThrownBy(() -> DeductionRules.validatePrevEmployment(invalidTan))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("employer_tan is invalid");

        List<PrevEmploymentRequest> duplicate = List.of(
                new PrevEmploymentRequest(PrevEmploymentKind.INCOME, new BigDecimal("100000.0000"), null, null),
                new PrevEmploymentRequest(PrevEmploymentKind.INCOME, new BigDecimal("200000.0000"), null, null));
        assertThatThrownBy(() -> DeductionRules.validatePrevEmployment(duplicate))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("Duplicate previous employment kind: INCOME");
    }

    @Test
    @DisplayName("Section6AItemReader.groupCap throws ReferenceDataMissingException when group cap is missing")
    void testSection6AItemReaderGroupCapThrowsWhenMissing() {
        JdbcTemplate mockJdbc = mock(JdbcTemplate.class);
        org.mockito.Mockito.when(mockJdbc.query(
                        org.mockito.Mockito.anyString(),
                        org.mockito.Mockito.<RowMapper<BigDecimal>>any(),
                        org.mockito.Mockito.any()))
                .thenReturn(List.of());

        Section6AItemReader reader = new Section6AItemReader(mockJdbc);
        assertThatThrownBy(() -> reader.groupCap("NON_EXISTENT_GROUP"))
                .isInstanceOf(ReferenceDataMissingException.class)
                .hasMessageContaining("reference.section6a_item_master");
    }

    @Test
    @DisplayName("validateSection6A refuses a description over 150 characters with a 400, naming the row")
    void testSection6ADescriptionTooLong() {
        Section6AItemReader reader = mock(Section6AItemReader.class);
        List<Section6ALineRequest> rows =
                List.of(new Section6ALineRequest(UUID.randomUUID(), "x".repeat(151), new BigDecimal("1000.0000")));
        assertThatThrownBy(() -> DeductionRules.validateSection6A(rows, "OLD", reader))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("Row 1")
                .hasMessageContaining("150 characters");
    }

    @Test
    @DisplayName("validatePrevEmployment refuses an employer name over 150 characters with a 400")
    void testPrevEmploymentEmployerNameTooLong() {
        List<PrevEmploymentRequest> rows = List.of(new PrevEmploymentRequest(
                PrevEmploymentKind.values()[0], new BigDecimal("1000.0000"), "y".repeat(151), null));
        assertThatThrownBy(() -> DeductionRules.validatePrevEmployment(rows))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("employer_name");
    }

    @Test
    @DisplayName("normaliseTan trims and upper-cases for storage; blank becomes null")
    void testNormaliseTan() {
        assertThat(DeductionRules.normaliseTan(" mumb12345f ")).isEqualTo("MUMB12345F");
        assertThat(DeductionRules.normaliseTan("MUMB12345F ")).hasSize(10);
        assertThat(DeductionRules.normaliseTan("   ")).isNull();
        assertThat(DeductionRules.normaliseTan(null)).isNull();
    }

    @Test
    @DisplayName("Section6AItemReader.groupCap throws IllegalStateException when members have conflicting caps")
    void testSection6AItemReaderGroupCapThrowsWhenConflictingCaps() {
        JdbcTemplate mockJdbc = mock(JdbcTemplate.class);
        org.mockito.Mockito.when(mockJdbc.query(
                        org.mockito.Mockito.anyString(),
                        org.mockito.Mockito.<RowMapper<BigDecimal>>any(),
                        org.mockito.Mockito.any()))
                .thenReturn(List.of(new BigDecimal("150000.00"), new BigDecimal("100000.00")));

        Section6AItemReader reader = new Section6AItemReader(mockJdbc);
        assertThatThrownBy(() -> reader.groupCap("80C_GROUP"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicting caps");
    }

    @Test
    @DisplayName("Section6AItemReader.groupCap succeeds when all members have the same cap")
    void testSection6AItemReaderGroupCapSucceedsWhenAllCapsMatch() {
        JdbcTemplate mockJdbc = mock(JdbcTemplate.class);
        org.mockito.Mockito.when(mockJdbc.query(
                        org.mockito.Mockito.anyString(),
                        org.mockito.Mockito.<RowMapper<BigDecimal>>any(),
                        org.mockito.Mockito.any()))
                .thenReturn(List.of(new BigDecimal("150000.00"), new BigDecimal("150000.00")));

        Section6AItemReader reader = new Section6AItemReader(mockJdbc);
        assertThat(reader.groupCap("80C_GROUP")).isEqualByComparingTo(new BigDecimal("150000.00"));
    }
}
