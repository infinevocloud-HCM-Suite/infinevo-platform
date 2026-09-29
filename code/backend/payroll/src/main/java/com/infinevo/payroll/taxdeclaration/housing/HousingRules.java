package com.infinevo.payroll.taxdeclaration.housing;

import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.payroll.taxdeclaration.housing.dto.HomeLoanRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.HouseRentRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.LetOutPropertyLineRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.LetOutPropertyRequest;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure domain rules and validations for tax declaration housing sections (W-32.2).
 */
public final class HousingRules {

    public static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");

    private HousingRules() {}

    /**
     * Validates a PAN string format.
     */
    public static boolean isValidPan(String pan) {
        if (pan == null || pan.isBlank()) {
            return false;
        }
        return PAN_PATTERN.matcher(pan.trim()).matches();
    }

    /**
     * Parses "YYYY-MM" into a LocalDate pointing at the first day of that month.
     */
    public static LocalDate parseYearMonth(String yearMonthStr, String fieldName, int rowIndex) {
        if (yearMonthStr == null || yearMonthStr.isBlank()) {
            throw new WindowValidationException("Row " + rowIndex + ": " + fieldName + " must not be null or blank");
        }
        try {
            YearMonth ym = YearMonth.parse(yearMonthStr.trim());
            return ym.atDay(1);
        } catch (Exception e) {
            throw new WindowValidationException(
                    "Row " + rowIndex + ": " + fieldName + " must be in YYYY-MM format: " + yearMonthStr);
        }
    }

    /**
     * Calculates inclusive number of months between two year-month dates.
     */
    public static long calculateMonthsInclusive(LocalDate fromMonth, LocalDate toMonth) {
        return ChronoUnit.MONTHS.between(fromMonth, toMonth) + 1;
    }

    /**
     * Validates house rent rows against financial year boundaries, period sequencing, overlaps,
     * positive amounts, and landlord PAN requirements.
     */
    public static void validateHouseRent(
            List<HouseRentRequest> rows,
            FinancialYear fy,
            boolean panRequiredForRentOverThreshold,
            BigDecimal panMandatoryThreshold) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        record RentInterval(int index, LocalDate from, LocalDate to, BigDecimal monthlyAmount, String pan) {}
        List<RentInterval> intervals = new ArrayList<>();
        BigDecimal totalRent = BigDecimal.ZERO;

        for (int i = 0; i < rows.size(); i++) {
            HouseRentRequest row = rows.get(i);
            int rowIndex = i + 1;

            if (row == null) {
                throw new WindowValidationException("Row " + rowIndex + " must not be null");
            }
            if (row.address() == null || row.address().isBlank()) {
                throw new WindowValidationException("Row " + rowIndex + ": address is required");
            }
            if (row.landlordName() == null || row.landlordName().isBlank()) {
                throw new WindowValidationException("Row " + rowIndex + ": landlord_name is required");
            }

            LocalDate from = parseYearMonth(row.fromMonth(), "from_month", rowIndex);
            LocalDate to = parseYearMonth(row.toMonth(), "to_month", rowIndex);

            if (to.isBefore(from)) {
                throw new WindowValidationException("Row " + rowIndex + ": from_month (" + row.fromMonth()
                        + ") must not be after to_month (" + row.toMonth() + ")");
            }

            if (from.isBefore(fy.start()) || from.isAfter(fy.end())) {
                throw new WindowValidationException("Row " + rowIndex + ": from_month (" + row.fromMonth()
                        + ") must fall within financial year " + fy.label());
            }
            if (to.isBefore(fy.start()) || to.isAfter(fy.end())) {
                throw new WindowValidationException("Row " + rowIndex + ": to_month (" + row.toMonth()
                        + ") must fall within financial year " + fy.label());
            }

            BigDecimal amount = row.amountPerMonth();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new WindowValidationException("Row " + rowIndex + ": amount_per_month must be non-negative");
            }

            if (row.landlordPan() != null && !row.landlordPan().isBlank()) {
                if (!isValidPan(row.landlordPan())) {
                    throw new WindowValidationException(
                            "Row " + rowIndex + ": landlord_pan is invalid: " + row.landlordPan());
                }
            }

            long months = calculateMonthsInclusive(from, to);
            BigDecimal rowTotal = amount.multiply(BigDecimal.valueOf(months));
            totalRent = totalRent.add(rowTotal);

            intervals.add(new RentInterval(rowIndex, from, to, amount, row.landlordPan()));
        }

        // Check overlapping periods
        intervals.sort(Comparator.comparing(RentInterval::from));
        for (int i = 0; i < intervals.size() - 1; i++) {
            RentInterval current = intervals.get(i);
            RentInterval next = intervals.get(i + 1);
            if (!next.from().isAfter(current.to())) {
                throw new WindowValidationException(
                        "Rent periods overlap between row " + current.index() + " (" + current.from() + " to "
                                + current.to() + ") and row " + next.index() + " (" + next.from() + " to "
                                + next.to() + ")");
            }
        }

        // Check landlord PAN requirement across total annual rent
        if (panRequiredForRentOverThreshold
                && panMandatoryThreshold != null
                && totalRent.compareTo(panMandatoryThreshold) > 0) {
            for (RentInterval interval : intervals) {
                if (interval.pan() == null || interval.pan().isBlank() || !isValidPan(interval.pan())) {
                    throw new WindowValidationException("Landlord PAN is mandatory on row " + interval.index()
                            + " because total annual rent (" + totalRent
                            + ") exceeds statutory threshold (" + panMandatoryThreshold + ")");
                }
            }
        }
    }

    /**
     * Validates home loan entries.
     */
    public static void validateHomeLoans(List<HomeLoanRequest> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            HomeLoanRequest row = rows.get(i);
            int rowIndex = i + 1;

            if (row == null) {
                throw new WindowValidationException("Row " + rowIndex + " must not be null");
            }
            if (row.lenderName() == null || row.lenderName().isBlank()) {
                throw new WindowValidationException("Row " + rowIndex + ": lender_name is required");
            }
            if (row.principalPaid() != null && row.principalPaid().compareTo(BigDecimal.ZERO) < 0) {
                throw new WindowValidationException("Row " + rowIndex + ": principal_paid must be non-negative");
            }
            if (row.interestPaid() != null && row.interestPaid().compareTo(BigDecimal.ZERO) < 0) {
                throw new WindowValidationException("Row " + rowIndex + ": interest_paid must be non-negative");
            }
            if (row.lenderPan() != null && !row.lenderPan().isBlank()) {
                if (!isValidPan(row.lenderPan())) {
                    throw new WindowValidationException(
                            "Row " + rowIndex + ": lender_pan is invalid: " + row.lenderPan());
                }
            }
        }
    }

    /**
     * Derives Section 24 net income or loss from let-out property lines and standard deduction rate.
     *
     * <p>Formula: {@code net_income_loss = annual_rent - municipal_tax - (annual_rent - municipal_tax) * (std_ded_pct / 100) - loan_interest}
     */
    public static BigDecimal calculateNetIncomeLoss(
            BigDecimal annualRent,
            BigDecimal municipalTax,
            BigDecimal loanInterest,
            BigDecimal standardDeductionPercent) {
        BigDecimal rent = annualRent != null ? annualRent : BigDecimal.ZERO;
        BigDecimal tax = municipalTax != null ? municipalTax : BigDecimal.ZERO;
        BigDecimal interest = loanInterest != null ? loanInterest : BigDecimal.ZERO;
        BigDecimal stdDedPct = standardDeductionPercent != null ? standardDeductionPercent : new BigDecimal("30.00");

        Money rentMoney = Money.of(rent);
        Money taxMoney = Money.of(tax);
        Money interestMoney = Money.of(interest);

        Money nav = rentMoney.subtract(taxMoney);
        BigDecimal stdDedFactor = stdDedPct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        Money stdDeduction = nav.multiply(stdDedFactor);

        Money netIncomeLoss = nav.subtract(stdDeduction).subtract(interestMoney);
        return netIncomeLoss.raw();
    }

    /**
     * Validates let-out property structure and ensures at most one line per line type.
     */
    public static void validateLetOutProperties(List<LetOutPropertyRequest> properties) {
        if (properties == null || properties.isEmpty()) {
            return;
        }
        for (int i = 0; i < properties.size(); i++) {
            LetOutPropertyRequest prop = properties.get(i);
            int propIndex = i + 1;

            if (prop == null) {
                throw new WindowValidationException("Property " + propIndex + " must not be null");
            }
            if (prop.propertyName() == null || prop.propertyName().isBlank()) {
                throw new WindowValidationException("Property " + propIndex + ": property_name is required");
            }

            if (prop.lines() != null) {
                Set<LetOutPropertyLineType> seenTypes = new HashSet<>();
                for (int j = 0; j < prop.lines().size(); j++) {
                    LetOutPropertyLineRequest line = prop.lines().get(j);
                    int lineIndex = j + 1;

                    if (line == null) {
                        throw new WindowValidationException(
                                "Property " + propIndex + ", Line " + lineIndex + " must not be null");
                    }
                    if (line.lineType() == null) {
                        throw new WindowValidationException(
                                "Property " + propIndex + ", Line " + lineIndex + ": line_type is required");
                    }
                    if (!seenTypes.add(line.lineType())) {
                        throw new WindowValidationException(
                                "Property " + propIndex + " contains multiple lines of type " + line.lineType());
                    }
                    if (line.amount() != null && line.amount().compareTo(BigDecimal.ZERO) < 0) {
                        throw new WindowValidationException(
                                "Property " + propIndex + ", Line " + lineIndex + ": amount must be non-negative");
                    }
                    if (line.lenderPan() != null && !line.lenderPan().isBlank()) {
                        if (!isValidPan(line.lenderPan())) {
                            throw new WindowValidationException("Property " + propIndex + ", Line " + lineIndex
                                    + ": lender_pan is invalid: " + line.lenderPan());
                        }
                    }
                }
            }
        }
    }
}
