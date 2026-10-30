package com.infinevo.payroll.salary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for previewing a salary version without saving it (W-75.1).
 *
 * <p>The {@link SalaryVersionRequest} fields plus an optional unsaved {@code statutoryProfile}; when present it is
 * used instead of the stored profile, so ticks can be previewed before they are saved.
 */
public record SalaryPreviewRequest(
        BigDecimal annualCtc,
        LocalDate effectiveFrom,
        String notes,
        List<SalaryComponentItemRequest> earnings,
        List<SalaryComponentItemRequest> benefits,
        List<SalaryComponentItemRequest> reimbursements,
        StatutoryProfileRequest statutoryProfile) {

    /** The same request as a create would receive. */
    public SalaryVersionRequest toVersionRequest() {
        return new SalaryVersionRequest(annualCtc, effectiveFrom, notes, earnings, benefits, reimbursements);
    }
}
