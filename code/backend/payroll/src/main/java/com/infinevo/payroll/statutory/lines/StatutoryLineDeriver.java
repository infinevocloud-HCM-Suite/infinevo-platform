package com.infinevo.payroll.statutory.lines;

import com.infinevo.payroll.salary.EmployeeStatutoryProfile;
import com.infinevo.payroll.statutory.settings.EpfSetting;
import com.infinevo.payroll.statutory.settings.EsiSetting;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure function that derives employee and employer EPF and ESI statutory lines for a salary version (W-31.3).
 *
 * <p>Contains no database access or Spring context. Receives {@link Money} in and produces {@link DerivedStatutoryLine} out.
 */
public final class StatutoryLineDeriver {

    public static final BigDecimal HUNDRED = new BigDecimal("100");
    public static final BigDecimal TWELVE = new BigDecimal("12");

    private StatutoryLineDeriver() {}

    /**
     * Derives statutory EPF and ESI lines from version wages, settings, profile, and employee personal details.
     *
     * @param basicMonthly the monthly amount of the version's BASIC earning component
     * @param grossMonthly the sum of monthly amounts of all enabled earnings
     * @param profile the employee's statutory profile
     * @param epf the tenant's EPF statutory settings
     * @param esi the tenant's ESI statutory settings
     * @param dateOfBirth the employee's date of birth (may be null)
     * @param effectiveFrom the salary version effective from date
     * @return the list of derived statutory lines (empty if not eligible or disabled)
     */
    public static List<DerivedStatutoryLine> derive(
            Money basicMonthly,
            Money grossMonthly,
            EmployeeStatutoryProfile profile,
            EpfSetting epf,
            EsiSetting esi,
            LocalDate dateOfBirth,
            LocalDate effectiveFrom) {

        List<DerivedStatutoryLine> lines = new ArrayList<>();

        // 1. Provident Fund (EPF & EPS)
        if (epf != null && epf.isEnabled() && profile != null && profile.isEligibleForPf()) {
            Money basic = (basicMonthly != null && !basicMonthly.isNegative()) ? basicMonthly : Money.ZERO;
            Money wageCeiling = Money.of(epf.getWageCeiling());

            // Employee PF
            Money employeeWageBase = epf.isRestrictEmployeeToCeiling() ? min(basic, wageCeiling) : basic;
            BigDecimal employeeRate = epf.getEmployeeRate();
            Money employeeMonthly = calculateRateAmount(employeeWageBase, employeeRate);
            Money employeeAnnual = employeeMonthly.multiply(TWELVE);
            lines.add(new DerivedStatutoryLine(
                    StatutoryComponentCode.EPF_EMPLOYEE,
                    ContributionShare.EMPLOYEE,
                    employeeWageBase,
                    employeeRate,
                    employeeMonthly,
                    employeeAnnual,
                    false));

            // Senior age check for EPS
            boolean isSenior = false;
            if (dateOfBirth != null && effectiveFrom != null) {
                int age = Period.between(dateOfBirth, effectiveFrom).getYears();
                isSenior = age >= epf.getEpsSeniorAge();
            }

            boolean eligibleForEps = profile.isEligibleForEps() && !isSenior;

            // Employer EPS
            Money epsWageBase = profile.isContributesEpsOnHigherWages() ? basic : min(basic, wageCeiling);
            BigDecimal epsRate = epf.getEpsRate();
            Money epsMonthly = eligibleForEps ? calculateRateAmount(epsWageBase, epsRate) : Money.ZERO;
            Money epsAnnual = epsMonthly.multiply(TWELVE);
            lines.add(new DerivedStatutoryLine(
                    StatutoryComponentCode.EPS_EMPLOYER,
                    ContributionShare.EMPLOYER,
                    epsWageBase,
                    epsRate,
                    epsMonthly,
                    epsAnnual,
                    epf.isIncludeEmployerInCtc()));

            // Employer PF
            Money employerWageBase = epf.isRestrictEmployerToCeiling() ? min(basic, wageCeiling) : basic;
            BigDecimal employerRate = epf.getEmployerRate();
            BigDecimal effectiveEmployerPfRate = eligibleForEps ? employerRate.subtract(epsRate) : employerRate;
            Money employerPfFull = calculateRateAmount(employerWageBase, employerRate);
            Money employerPfMonthly = employerPfFull.subtract(epsMonthly);
            if (employerPfMonthly.isNegative()) {
                employerPfMonthly = Money.ZERO;
            }
            Money employerPfAnnual = employerPfMonthly.multiply(TWELVE);
            lines.add(new DerivedStatutoryLine(
                    StatutoryComponentCode.EPF_EMPLOYER,
                    ContributionShare.EMPLOYER,
                    employerWageBase,
                    effectiveEmployerPfRate,
                    employerPfMonthly,
                    employerPfAnnual,
                    epf.isIncludeEmployerInCtc()));

            // EDLI
            Money edliWageBase = min(basic, wageCeiling);
            BigDecimal edliRate = epf.getEdliRate();
            Money edliMonthly = calculateRateAmount(edliWageBase, edliRate);
            Money edliAnnual = edliMonthly.multiply(TWELVE);
            lines.add(new DerivedStatutoryLine(
                    StatutoryComponentCode.EDLI,
                    ContributionShare.EMPLOYER,
                    edliWageBase,
                    edliRate,
                    edliMonthly,
                    edliAnnual,
                    epf.isIncludeEdliAdminInCtc()));

            // Admin Charge
            Money adminWageBase = epf.isRestrictEmployerToCeiling() ? min(basic, wageCeiling) : basic;
            BigDecimal adminRate = epf.getAdminChargeRate();
            Money adminMonthly = calculateRateAmount(adminWageBase, adminRate);
            Money adminAnnual = adminMonthly.multiply(TWELVE);
            lines.add(new DerivedStatutoryLine(
                    StatutoryComponentCode.EPF_ADMIN,
                    ContributionShare.EMPLOYER,
                    adminWageBase,
                    adminRate,
                    adminMonthly,
                    adminAnnual,
                    epf.isIncludeEdliAdminInCtc()));
        }

        // 2. State Insurance (ESI)
        if (esi != null && esi.isEnabled() && profile != null && profile.isEligibleForEsi()) {
            Money gross = (grossMonthly != null && !grossMonthly.isNegative()) ? grossMonthly : Money.ZERO;
            Money esiCeiling = Money.of(esi.getWageCeiling());

            if (gross.raw().compareTo(esiCeiling.raw()) <= 0) {
                // Employee ESI
                BigDecimal employeeRate = esi.getEmployeeRate();
                Money employeeMonthly = calculateRateAmount(gross, employeeRate);
                Money employeeAnnual = employeeMonthly.multiply(TWELVE);
                lines.add(new DerivedStatutoryLine(
                        StatutoryComponentCode.ESI_EMPLOYEE,
                        ContributionShare.EMPLOYEE,
                        gross,
                        employeeRate,
                        employeeMonthly,
                        employeeAnnual,
                        false));

                // Employer ESI
                BigDecimal employerRate = esi.getEmployerRate();
                Money employerMonthly = calculateRateAmount(gross, employerRate);
                Money employerAnnual = employerMonthly.multiply(TWELVE);
                lines.add(new DerivedStatutoryLine(
                        StatutoryComponentCode.ESI_EMPLOYER,
                        ContributionShare.EMPLOYER,
                        gross,
                        employerRate,
                        employerMonthly,
                        employerAnnual,
                        esi.isIncludeEmployerInCtc()));
            }
        }

        return Collections.unmodifiableList(lines);
    }

    private static Money calculateRateAmount(Money base, BigDecimal rate) {
        if (base == null || rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return Money.ZERO;
        }
        BigDecimal amount = base.raw().multiply(rate).divide(HUNDRED, 4, RoundingMode.HALF_UP);
        return Money.of(amount);
    }

    private static Money min(Money a, Money b) {
        return a.raw().compareTo(b.raw()) <= 0 ? a : b;
    }
}
