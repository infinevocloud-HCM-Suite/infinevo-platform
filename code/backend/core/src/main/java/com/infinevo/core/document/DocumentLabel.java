package com.infinevo.core.document;

/**
 * What an {@link DocumentKind#EMPLOYEE_DOCUMENT} is — chosen by HR on upload and shown on the employee
 * page's Documents tab and the employee's own {@code /me} Documents panel (W-73.5, spec section 2).
 *
 * <p>The six replace the 22 values of the frozen {@code DocumentType}
 * ({@code legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/enumuration/DocumentType.java:3-35}),
 * folded into headings rather than one value per paper:
 *
 * <ul>
 *   <li>{@link #ID_PROOF} — its "Identification", "Government IDs" and "Passport" groups (Aadhaar, photo,
 *       PAN, EPIC, passport, other ID);
 *   <li>{@link #ADDRESS_PROOF} — its "Address Proof" group (rent agreement, light bill, bank statement);
 *   <li>{@link #CERTIFICATE} — its "Qualification" group (tenth, twelfth or diploma, degree, post-graduate)
 *       and the relieving and experience letters of its "Previous Company" group;
 *   <li>{@link #OFFER_LETTER} — the appointment letter from that same group;
 *   <li>{@link #CONTRACT} — new: the frozen system had no place for an employment contract;
 *   <li>{@link #OTHER} — everything else, the salary slips of a previous employer among them.
 * </ul>
 *
 * <p>Stored as its name in {@code core.document.label}, which {@code V166__document_label.sql} checks
 * against the same six values, so a row written outside the service cannot hold a value this enum
 * cannot read back. Null for every other kind, and optional on an employee document.
 */
public enum DocumentLabel {
    ID_PROOF,
    ADDRESS_PROOF,
    OFFER_LETTER,
    CONTRACT,
    CERTIFICATE,
    OTHER
}
