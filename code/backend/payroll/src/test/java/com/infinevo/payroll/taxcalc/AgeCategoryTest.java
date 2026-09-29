package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AgeCategory} statutory determination (W-33.1 spec § 7 & § 13 decision 3).
 */
class AgeCategoryTest {

    private static final LocalDate FY_END_2026 = LocalDate.of(2026, 3, 31);

    @Test
    @DisplayName("born 1966-03-31 reaches 60 on 2026-03-31 -> SENIOR")
    void born19660331IsSenior() {
        LocalDate dob = LocalDate.of(1966, 3, 31);
        AgeCategory category = AgeCategory.at(dob, FY_END_2026);
        assertThat(category).isEqualTo(AgeCategory.SENIOR);
    }

    @Test
    @DisplayName("born 1966-04-01 reaches 59 on 2026-03-31 -> GENERAL")
    void born19660401IsGeneral() {
        LocalDate dob = LocalDate.of(1966, 4, 1);
        AgeCategory category = AgeCategory.at(dob, FY_END_2026);
        assertThat(category).isEqualTo(AgeCategory.GENERAL);
    }

    @Test
    @DisplayName("reaches 80 on 2026-03-31 (born 1946-03-31) -> SUPER_SENIOR")
    void reaches80IsSuperSenior() {
        LocalDate dob = LocalDate.of(1946, 3, 31);
        AgeCategory category = AgeCategory.at(dob, FY_END_2026);
        assertThat(category).isEqualTo(AgeCategory.SUPER_SENIOR);
    }

    @Test
    @DisplayName("over 80 (born 1940-01-01) -> SUPER_SENIOR")
    void over80IsSuperSenior() {
        LocalDate dob = LocalDate.of(1940, 1, 1);
        AgeCategory category = AgeCategory.at(dob, FY_END_2026);
        assertThat(category).isEqualTo(AgeCategory.SUPER_SENIOR);
    }

    @Test
    @DisplayName("no date of birth (null) defaults to GENERAL")
    void nullDateOfBirthDefaultsToGeneral() {
        AgeCategory category = AgeCategory.at(null, FY_END_2026);
        assertThat(category).isEqualTo(AgeCategory.GENERAL);
    }

    @Test
    @DisplayName("null asOf date throws NullPointerException")
    void nullAsOfDateThrows() {
        assertThatThrownBy(() -> AgeCategory.at(LocalDate.of(1990, 1, 1), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("asOfDate must not be null");
    }
}
