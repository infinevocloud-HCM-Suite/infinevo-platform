package com.infinevo.core.document;

import java.util.Set;

/**
 * What a stored file is for (W-21, spec section 4).
 *
 * <p>Stored as the string name in {@code core.document.kind}, which carries a {@code CHECK} over
 * the same seven values ({@code V037__document.sql}, widened by {@code V108__document_kind_form16.sql}),
 * so a row written outside the service cannot hold a value this enum cannot read back.
 *
 * <p>The first four replace the seven legacy tables that each held a Cloudinary URL. {@link
 * #PAYSLIP} is for {@code W-36} and {@link #EXPORT} for {@code W-23.1} — contracts section 1 and
 * section 5 row 16. {@link #FORM16_PART_A} is the TRACES certificate of tax deposited, filed per
 * employee from the officer's ZIP upload ({@code W-36.5}). {@link #TENANT_LOGO} is the company logo the
 * header shows ({@code W-73.1}, widened by {@code V160__tenant_branding.sql}): tenant-scoped, never filed
 * against an employee, and uploadable by whoever may update the tenant's profile.
 */
public enum DocumentKind {
    EMPLOYEE_DOCUMENT,
    LEAVE_ATTACHMENT,
    REIMBURSEMENT_RECEIPT,
    INVESTMENT_PROOF,
    PAYSLIP,
    EXPORT,
    FORM16_PART_A,
    TENANT_LOGO;

    /**
     * Kinds the platform writes itself and a client may never upload.
     *
     * <p>A payslip is rendered from a locked pay run ({@code W-36}) and an export from a report
     * definition ({@code W-23.1}). Accepting either through {@code POST /api/v1/documents} would let
     * a caller put a file of their own making into the store under a kind the rest of the platform
     * treats as system output — a forged payslip is the obvious case.
     *
     * <p>A Form 16 Part A is not rendered here — it is downloaded from TRACES — but it reaches the store
     * only through the officer's Part A upload in {@code payroll} ({@code W-36.5}, spec section 13
     * question 3). Through {@code /documents} an employee could file a certificate of their own making.
     */
    static final Set<DocumentKind> SYSTEM_GENERATED = Set.of(PAYSLIP, EXPORT, FORM16_PART_A);

    /** True when a client may upload a file of this kind. */
    public boolean isUploadable() {
        return !SYSTEM_GENERATED.contains(this);
    }
}
