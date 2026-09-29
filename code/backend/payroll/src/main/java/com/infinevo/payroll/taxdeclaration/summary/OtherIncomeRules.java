package com.infinevo.payroll.taxdeclaration.summary;

import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.payroll.taxdeclaration.summary.dto.OtherIncomeRequest;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Domain rules and validations for declared other income (W-32.4).
 */
public final class OtherIncomeRules {

    private OtherIncomeRules() {}

    /**
     * Validates other income rows: unique kind per declaration, non-negative amounts,
     * and required description when kind is OTHER.
     */
    public static void validate(List<OtherIncomeRequest> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        Set<OtherIncomeKind> seenKinds = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            OtherIncomeRequest row = rows.get(i);
            int rowIndex = i + 1;

            if (row == null) {
                throw new WindowValidationException("Row " + rowIndex + " must not be null");
            }
            if (row.kind() == null) {
                throw new WindowValidationException("Row " + rowIndex + ": kind is required");
            }
            if (!seenKinds.add(row.kind())) {
                throw new WindowValidationException("Duplicate other income kind: " + row.kind());
            }
            if (row.amount() == null || row.amount().compareTo(BigDecimal.ZERO) < 0) {
                throw new WindowValidationException("Row " + rowIndex + ": amount must be non-negative");
            }
            if (row.kind() == OtherIncomeKind.OTHER
                    && (row.description() == null || row.description().isBlank())) {
                throw new WindowValidationException("Row " + rowIndex + ": description is required for OTHER income");
            }
            if (row.description() != null && row.description().length() > 150) {
                throw new WindowValidationException("Description must not exceed 150 characters");
            }
        }
    }
}
