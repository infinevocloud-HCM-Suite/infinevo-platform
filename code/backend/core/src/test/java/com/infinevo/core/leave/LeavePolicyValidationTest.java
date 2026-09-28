package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for leave policy validation rules (W-16.1 spec section 4, 6 &amp; 7).
 * Covers:
 * - yearEndLimit without a limit is refused
 * - noLimit and markAsLOP with a limit are refused
 * - accrual or reset frequency outside its vocabulary is refused
 * - employment_type dimension is refused
 */
class LeavePolicyValidationTest {

    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2026, 1, 1);

    @Test
    @DisplayName("yearEndLimit without limit is refused")
    void yearEndLimitWithoutLimitIsRefused() {
        LeavePolicyRequest request = new LeavePolicyRequest(
                BigDecimal.valueOf(20),
                false,
                null,
                null,
                false,
                null,
                false,
                null,
                null,
                false,
                null,
                null,
                false,
                false,
                ExceedBalanceMode.YEAR_END_LIMIT,
                null, // missing limit!
                false,
                null,
                null,
                EFFECTIVE_DATE,
                List.of());

        assertThatThrownBy(() -> LeavePolicyValidator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceedBalanceLimitDays is required when exceedBalanceMode is yearEndLimit");
    }

    @Test
    @DisplayName("yearEndLimit with valid limit is accepted")
    void yearEndLimitWithValidLimitIsAccepted() {
        LeavePolicyRequest request = new LeavePolicyRequest(
                BigDecimal.valueOf(20),
                false,
                null,
                null,
                false,
                null,
                false,
                null,
                null,
                false,
                null,
                null,
                false,
                false,
                ExceedBalanceMode.YEAR_END_LIMIT,
                BigDecimal.valueOf(5),
                false,
                null,
                null,
                EFFECTIVE_DATE,
                List.of());

        LeavePolicyValidator.validate(request);
    }

    @Test
    @DisplayName("noLimit with limit is refused")
    void noLimitWithLimitIsRefused() {
        LeavePolicyRequest request = new LeavePolicyRequest(
                BigDecimal.valueOf(20),
                false,
                null,
                null,
                false,
                null,
                false,
                null,
                null,
                false,
                null,
                null,
                false,
                false,
                ExceedBalanceMode.NO_LIMIT,
                BigDecimal.valueOf(5), // limit specified when not allowed!
                false,
                null,
                null,
                EFFECTIVE_DATE,
                List.of());

        assertThatThrownBy(() -> LeavePolicyValidator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceedBalanceLimitDays must be null when exceedBalanceMode is not yearEndLimit");
    }

    @Test
    @DisplayName("markAsLOP with limit is refused")
    void markAsLOPWithLimitIsRefused() {
        LeavePolicyRequest request = new LeavePolicyRequest(
                BigDecimal.valueOf(20),
                false,
                null,
                null,
                false,
                null,
                false,
                null,
                null,
                false,
                null,
                null,
                false,
                false,
                ExceedBalanceMode.MARK_AS_LOP,
                BigDecimal.valueOf(5), // limit specified when not allowed!
                false,
                null,
                null,
                EFFECTIVE_DATE,
                List.of());

        assertThatThrownBy(() -> LeavePolicyValidator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceedBalanceLimitDays must be null when exceedBalanceMode is not yearEndLimit");
    }

    @Test
    @DisplayName("accrual frequency outside its vocabulary is refused")
    void accrualFrequencyOutsideVocabularyIsRefused() {
        assertThatThrownBy(() -> AccrualFrequency.fromString("daily"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown accrual frequency: 'daily'. Supported: monthly, yearly");

        assertThatThrownBy(() -> AccrualFrequency.fromString("biweekly"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown accrual frequency");

        // Allowed values
        assertThat(AccrualFrequency.fromString("monthly")).isEqualTo(AccrualFrequency.MONTHLY);
        assertThat(AccrualFrequency.fromString("yearly")).isEqualTo(AccrualFrequency.YEARLY);
    }

    @Test
    @DisplayName("reset frequency outside its vocabulary is refused")
    void resetFrequencyOutsideVocabularyIsRefused() {
        assertThatThrownBy(() -> ResetFrequency.fromString("weekly"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Unknown reset frequency: 'weekly'. Supported: yearly, monthly, quarterly, halfYearly");

        assertThatThrownBy(() -> ResetFrequency.fromString("daily"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown reset frequency");

        // Allowed values
        assertThat(ResetFrequency.fromString("yearly")).isEqualTo(ResetFrequency.YEARLY);
        assertThat(ResetFrequency.fromString("monthly")).isEqualTo(ResetFrequency.MONTHLY);
        assertThat(ResetFrequency.fromString("quarterly")).isEqualTo(ResetFrequency.QUARTERLY);
        assertThat(ResetFrequency.fromString("halfYearly")).isEqualTo(ResetFrequency.HALF_YEARLY);
    }

    @Test
    @DisplayName("exceed balance mode outside its vocabulary is refused")
    void exceedBalanceModeOutsideVocabularyIsRefused() {
        assertThatThrownBy(() -> ExceedBalanceMode.fromString("unlimited"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown exceed balance mode");

        // Allowed values
        assertThat(ExceedBalanceMode.fromString("noLimit")).isEqualTo(ExceedBalanceMode.NO_LIMIT);
        assertThat(ExceedBalanceMode.fromString("yearEndLimit")).isEqualTo(ExceedBalanceMode.YEAR_END_LIMIT);
        assertThat(ExceedBalanceMode.fromString("markAsLOP")).isEqualTo(ExceedBalanceMode.MARK_AS_LOP);
    }

    @Test
    @DisplayName("employment_type dimension is refused until master table exists")
    void employmentTypeDimensionIsRefused() {
        LeavePolicyRequest request = new LeavePolicyRequest(
                BigDecimal.valueOf(20),
                false,
                null,
                null,
                false,
                null,
                false,
                null,
                null,
                false,
                null,
                null,
                false,
                false,
                ExceedBalanceMode.NO_LIMIT,
                null,
                false,
                null,
                null,
                EFFECTIVE_DATE,
                List.of(new LeavePolicyEligibilityRequest("employment_type", java.util.UUID.randomUUID())));

        assertThatThrownBy(() -> LeavePolicyValidator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("employment_type dimension is not supported until employment type master exists");
    }

    @Test
    @DisplayName("unknown dimension is refused")
    void unknownDimensionIsRefused() {
        LeavePolicyRequest request = new LeavePolicyRequest(
                BigDecimal.valueOf(20),
                false,
                null,
                null,
                false,
                null,
                false,
                null,
                null,
                false,
                null,
                null,
                false,
                false,
                ExceedBalanceMode.NO_LIMIT,
                null,
                false,
                null,
                null,
                EFFECTIVE_DATE,
                List.of(new LeavePolicyEligibilityRequest("religion", java.util.UUID.randomUUID())));

        assertThatThrownBy(() -> LeavePolicyValidator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported eligibility dimension: 'religion'");
    }
}
