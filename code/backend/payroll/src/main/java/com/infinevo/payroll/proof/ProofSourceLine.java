package com.infinevo.payroll.proof;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One declared line that needs proof, as the six readers report it (W-34.1 spec section 3).
 *
 * @param kind which declared table the line came from
 * @param lineId the line's id in that table
 * @param description what the employee sees, at most 150 characters
 * @param declaredAmount the amount declared for the year, always greater than zero
 */
public record ProofSourceLine(ProofSourceKind kind, UUID lineId, String description, BigDecimal declaredAmount) {

    static final int DESCRIPTION_MAX = 150;

    public ProofSourceLine {
        if (kind == null || lineId == null || description == null || declaredAmount == null) {
            throw new IllegalArgumentException("kind, lineId, description and declaredAmount are required");
        }
        if (description.length() > DESCRIPTION_MAX) {
            description = description.substring(0, DESCRIPTION_MAX);
        }
    }

    /** The identity an item keeps across syncs: the kind and the declared line, never the amount. */
    public String key() {
        return kind + ":" + lineId;
    }
}
