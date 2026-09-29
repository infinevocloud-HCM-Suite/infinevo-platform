package com.infinevo.payroll.taxdeclaration.deductions;

import com.infinevo.payroll.taxdeclaration.deductions.Section6AItemReader.Section6AItem;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PreTaxDeductionRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PrevEmploymentRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.Section6ALineRequest;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pure domain rules and validations for Section 6A, pre-tax deductions, and previous employment (W-32.3).
 */
public final class DeductionRules {

    public static final Pattern TAN_PATTERN = Pattern.compile("^[A-Z]{4}[0-9]{5}[A-Z]$");

    private DeductionRules() {}

    /**
     * Validates a TAN string format.
     */
    /** Trims a TAN for storage; blank becomes {@code null}. The column is {@code varchar(10)}. */
    public static String normaliseTan(String tan) {
        if (tan == null || tan.isBlank()) {
            return null;
        }
        return tan.trim().toUpperCase();
    }

    public static boolean isValidTan(String tan) {
        if (tan == null || tan.isBlank()) {
            return false;
        }
        return TAN_PATTERN.matcher(tan.trim()).matches();
    }

    /**
     * Validates Chapter VI-A investment declaration rows against catalogue availability,
     * regime restrictions, individual item ceilings, and category group umbrella caps.
     */
    public static void validateSection6A(
            List<Section6ALineRequest> rows, String regime, Section6AItemReader itemReader) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        Map<UUID, Money> itemTotals = new HashMap<>();
        Map<UUID, Section6AItem> itemMap = new HashMap<>();
        Map<String, Money> groupTotals = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            Section6ALineRequest row = rows.get(i);
            int rowIndex = i + 1;

            if (row == null) {
                throw new WindowValidationException("Row " + rowIndex + " must not be null");
            }
            if (row.section6aItemId() == null) {
                throw new WindowValidationException("Row " + rowIndex + ": section6a_item_id is required");
            }
            if (row.description() == null || row.description().isBlank()) {
                throw new WindowValidationException("Row " + rowIndex + ": description is required");
            }
            if (row.description().length() > 150) {
                throw new WindowValidationException("Row " + rowIndex + ": description must not exceed 150 characters");
            }
            if (row.amount() == null || row.amount().compareTo(BigDecimal.ZERO) < 0) {
                throw new WindowValidationException("Row " + rowIndex + ": amount must be non-negative");
            }

            Section6AItem item = itemMap.computeIfAbsent(row.section6aItemId(), itemReader::require);
            if (!item.isActive()) {
                throw new WindowValidationException(
                        "Row " + rowIndex + ": Section " + item.sectionCode() + " is inactive");
            }

            if ("NEW".equalsIgnoreCase(regime) && !item.isAllowedInNewRegime()) {
                throw new WindowValidationException("Row " + rowIndex + ": Section " + item.sectionCode()
                        + " is not allowed under the NEW tax regime");
            }

            Money currentItemTotal = itemTotals.getOrDefault(item.id(), Money.ZERO);
            itemTotals.put(item.id(), currentItemTotal.add(Money.of(row.amount())));

            if (item.categoryGroupCode() != null && !item.categoryGroupCode().isBlank()) {
                Money currentGroupTotal = groupTotals.getOrDefault(item.categoryGroupCode(), Money.ZERO);
                groupTotals.put(item.categoryGroupCode(), currentGroupTotal.add(Money.of(row.amount())));
            }
        }

        // Validate per-item ceilings
        for (Map.Entry<UUID, Money> entry : itemTotals.entrySet()) {
            Section6AItem item = itemMap.get(entry.getKey());
            if (item.maxLimit() != null && entry.getValue().raw().compareTo(item.maxLimit()) > 0) {
                throw new WindowValidationException("Total declared amount for section " + item.sectionCode() + " ("
                        + entry.getValue().raw() + ") exceeds statutory limit (" + item.maxLimit() + ")");
            }
        }

        // Validate category group umbrella caps
        for (Map.Entry<String, Money> entry : groupTotals.entrySet()) {
            String groupCode = entry.getKey();
            BigDecimal cap = itemReader.groupCap(groupCode);
            if (cap != null && entry.getValue().raw().compareTo(cap) > 0) {
                throw new WindowValidationException("Total declared amount for group " + groupCode + " ("
                        + entry.getValue().raw() + ") exceeds group cap (" + cap + ")");
            }
        }
    }

    /**
     * Validates pre-tax deduction rows: unique kind per declaration and non-negative amount.
     */
    public static void validatePreTaxDeductions(List<PreTaxDeductionRequest> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        Set<PreTaxDeductionKind> seenKinds = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            PreTaxDeductionRequest row = rows.get(i);
            int rowIndex = i + 1;

            if (row == null) {
                throw new WindowValidationException("Row " + rowIndex + " must not be null");
            }
            if (row.kind() == null) {
                throw new WindowValidationException("Row " + rowIndex + ": kind is required");
            }
            if (!seenKinds.add(row.kind())) {
                throw new WindowValidationException("Duplicate pre-tax deduction kind: " + row.kind());
            }
            if (row.amount() == null || row.amount().compareTo(BigDecimal.ZERO) < 0) {
                throw new WindowValidationException("Row " + rowIndex + ": amount must be non-negative");
            }
        }
    }

    /**
     * Validates previous employment rows: unique kind, non-negative amount, and valid TAN.
     */
    public static void validatePrevEmployment(List<PrevEmploymentRequest> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        Set<PrevEmploymentKind> seenKinds = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            PrevEmploymentRequest row = rows.get(i);
            int rowIndex = i + 1;

            if (row == null) {
                throw new WindowValidationException("Row " + rowIndex + " must not be null");
            }
            if (row.kind() == null) {
                throw new WindowValidationException("Row " + rowIndex + ": kind is required");
            }
            if (!seenKinds.add(row.kind())) {
                throw new WindowValidationException("Duplicate previous employment kind: " + row.kind());
            }
            if (row.amount() == null || row.amount().compareTo(BigDecimal.ZERO) < 0) {
                throw new WindowValidationException("Row " + rowIndex + ": amount must be non-negative");
            }
            if (row.employerName() != null && row.employerName().length() > 150) {
                throw new WindowValidationException(
                        "Row " + rowIndex + ": employer_name must not exceed 150 characters");
            }
            if (row.employerTan() != null && !row.employerTan().isBlank()) {
                if (!isValidTan(row.employerTan())) {
                    throw new WindowValidationException(
                            "Row " + rowIndex + ": employer_tan is invalid: " + row.employerTan());
                }
            }
        }
    }
}
