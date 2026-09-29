package com.infinevo.core.document;

import java.util.Set;

/**
 * What a stored file is for (W-21, spec section 4).
 *
 * <p>Stored as the string name in {@code core.document.kind}, which carries a {@code CHECK} over
 * the same six values ({@code V037__document.sql}), so a row written outside the service cannot
 * hold a value this enum cannot read back.
 *
 * <p>The first four replace the seven legacy tables that each held a Cloudinary URL. {@link
 * #PAYSLIP} is for {@code W-36} and {@link #EXPORT} for {@code W-23.1} — contracts section 1 and
 * section 5 row 16.
 */
public enum DocumentKind {
    EMPLOYEE_DOCUMENT,
    LEAVE_ATTACHMENT,
    REIMBURSEMENT_RECEIPT,
    INVESTMENT_PROOF,
    PAYSLIP,
    EXPORT;

    /**
     * Kinds the platform writes itself and a client may never upload.
     *
     * <p>A payslip is rendered from a locked pay run ({@code W-36}) and an export from a report
     * definition ({@code W-23.1}). Accepting either through {@code POST /api/v1/documents} would let
     * a caller put a file of their own making into the store under a kind the rest of the platform
     * treats as system output — a forged payslip is the obvious case.
     */
    static final Set<DocumentKind> SYSTEM_GENERATED = Set.of(PAYSLIP, EXPORT);

    /** True when a client may upload a file of this kind. */
    public boolean isUploadable() {
        return !SYSTEM_GENERATED.contains(this);
    }
}
