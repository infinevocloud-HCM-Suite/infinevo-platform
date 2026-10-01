package com.infinevo.payroll.taxcalc;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Statutory age category under Indian Income Tax Act (W-33.1).
 *
 * <p>Determined by the age reached by 31 March of the financial year (W-33.1 spec ? 13 decision 3):
 * <ul>
 *   <li>{@code GENERAL}: under 60 years of age</li>
 *   <li>{@code SENIOR}: 60 to 79 years of age</li>
 *   <li>{@code SUPER_SENIOR}: 80 years of age and over</li>
 * </ul>
 * A person who turns 60 during the year is a senior citizen for the whole year.
 * When date of birth is absent, {@code GENERAL} is the default.
 */
public enum AgeCategory {
    GENERAL,
    SENIOR,
    SUPER_SENIOR;

    /**
     * Determines the age category for an employee born on {@code dateOfBirth} as of {@code asOfDate}
     * (normally March 31 of the financial year).
     *
     * @param dateOfBirth the date of birth, nullable
     * @param asOfDate the evaluation date, must not be null
     * @return the resolved {@link AgeCategory}
     */
    public static AgeCategory at(LocalDate dateOfBirth, LocalDate asOfDate) {
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");
        if (dateOfBirth == null) {
            return GENERAL;
        }

        if (dateOfBirth.isAfter(asOfDate)) {
            return GENERAL;
        }

        int ageInYears = Period.between(dateOfBirth, asOfDate).getYears();
        if (ageInYears >= 80) {
            return SUPER_SENIOR;
        } else if (ageInYears >= 60) {
            return SENIOR;
        } else {
            return GENERAL;
        }
    }
}
