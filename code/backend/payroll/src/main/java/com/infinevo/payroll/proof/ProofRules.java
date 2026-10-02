package com.infinevo.payroll.proof;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure rules for editing and submitting a proof of investment (W-34.1 spec sections 3 and 4). */
public final class ProofRules {

    /** At most this many files per item; a cap the spec does not set, so a client cannot fill storage. */
    public static final int MAX_DOCUMENTS_PER_ITEM = 10;

    /** {@code claimed_amount} is {@code NUMERIC(19,4)}: at most 15 integer digits. */
    static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999999");

    public static final int NOTE_MAX = 1000;

    private ProofRules() {}

    /** The employee may change items and files while the proof window is open and the proof is DRAFT or REJECTED. */
    public static boolean isEditable(boolean proofOpen, ProofStatus status) {
        return proofOpen && status.isEditableState();
    }

    /** {@code 409} unless the proof may be changed now. */
    public static void requireEditable(boolean proofOpen, ProofStatus status) {
        if (!status.isEditableState()) {
            throw new ProofConflictException(
                    ProofConflictException.NOT_EDITABLE, "A " + status + " proof cannot be changed");
        }
        if (!proofOpen) {
            throw new ProofConflictException(
                    ProofConflictException.PROOF_WINDOW_CLOSED, "The proof of investment window is closed");
        }
    }

    /** {@code 400} unless the claimed amount is a non-negative money value with at most two decimals. */
    public static void validateClaimedAmount(BigDecimal claimed) {
        if (claimed == null) {
            throw new ProofValidationException("claimed_amount is required");
        }
        if (claimed.signum() < 0) {
            throw new ProofValidationException("claimed_amount must not be negative");
        }
        if (claimed.stripTrailingZeros().scale() > 2) {
            throw new ProofValidationException("claimed_amount must have at most two decimal places");
        }
        if (claimed.compareTo(MAX_AMOUNT) > 0) {
            throw new ProofValidationException("claimed_amount is too large");
        }
    }

    /** {@code 400} unless the note fits its column. */
    public static void validateNote(String note) {
        if (note != null && note.length() > NOTE_MAX) {
            throw new ProofValidationException("employee_note must be at most " + NOTE_MAX + " characters");
        }
    }

    /**
     * The rules of a submit, in the order the caller sees them: already submitted, window closed,
     * nothing claimed, attachment missing.
     *
     * @param documentsPerItem how many files each item carries, by item id
     */
    public static void validateSubmit(
            ProofStatus status,
            boolean proofOpen,
            boolean attachmentMandatory,
            List<EmployeeProofItem> items,
            Map<UUID, Long> documentsPerItem) {
        if (!status.isEditableState()) {
            throw new ProofConflictException(
                    ProofConflictException.ALREADY_SUBMITTED, "The proof of investment is already " + status);
        }
        if (!proofOpen) {
            throw new ProofConflictException(
                    ProofConflictException.PROOF_WINDOW_CLOSED, "The proof of investment window is closed");
        }
        List<EmployeeProofItem> claimed =
                items.stream().filter(ProofRules::isClaimed).toList();
        if (claimed.isEmpty()) {
            throw new ProofConflictException(
                    ProofConflictException.NOTHING_CLAIMED, "Claim an amount on at least one item before submitting");
        }
        if (attachmentMandatory) {
            List<UUID> missing = new ArrayList<>();
            for (EmployeeProofItem item : claimed) {
                if (documentsPerItem.getOrDefault(item.getId(), 0L) < 1) {
                    missing.add(item.getId());
                }
            }
            if (!missing.isEmpty()) {
                throw new ProofConflictException(
                        ProofConflictException.ATTACHMENT_REQUIRED,
                        "Attach at least one file to every claimed item; missing on " + missing.size() + " item(s)",
                        missing);
            }
        }
    }

    /** An item counts as claimed when the employee entered an amount above zero. */
    public static boolean isClaimed(EmployeeProofItem item) {
        return item.getClaimedAmount() != null && item.getClaimedAmount().signum() > 0;
    }
}
