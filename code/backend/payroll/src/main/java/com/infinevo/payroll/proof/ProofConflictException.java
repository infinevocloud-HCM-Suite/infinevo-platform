package com.infinevo.payroll.proof;

import java.io.Serial;
import java.util.List;
import java.util.UUID;

/**
 * A proof request the current state refuses; the controller answers {@code 409} with {@link #reasonCode()}
 * as the error code, as {@code DeclarationNotEditableException} does (W-34.1).
 */
public class ProofConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String NOT_SUBMITTED = "NOT_SUBMITTED";
    public static final String NOT_EDITABLE = "NOT_EDITABLE";
    public static final String PROOF_WINDOW_CLOSED = "PROOF_WINDOW_CLOSED";
    public static final String ALREADY_SUBMITTED = "ALREADY_SUBMITTED";
    public static final String NOTHING_CLAIMED = "NOTHING_CLAIMED";
    public static final String ATTACHMENT_REQUIRED = "ATTACHMENT_REQUIRED";
    public static final String DOCUMENT_LIMIT = "DOCUMENT_LIMIT";
    public static final String NOT_UNDER_REVIEW = "NOT_UNDER_REVIEW";
    public static final String ITEMS_UNDECIDED = "ITEMS_UNDECIDED";

    private final String reasonCode;
    private final List<UUID> itemIds;

    public ProofConflictException(String reasonCode, String message) {
        this(reasonCode, message, List.of());
    }

    /** {@code itemIds} names the items that failed a rule, for {@code ATTACHMENT_REQUIRED}. */
    public ProofConflictException(String reasonCode, String message, List<UUID> itemIds) {
        super(message);
        this.reasonCode = reasonCode;
        this.itemIds = List.copyOf(itemIds);
    }

    public String reasonCode() {
        return reasonCode;
    }

    public List<UUID> itemIds() {
        return itemIds;
    }
}
