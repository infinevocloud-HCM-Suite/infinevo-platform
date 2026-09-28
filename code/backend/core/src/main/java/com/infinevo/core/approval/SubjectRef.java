package com.infinevo.core.approval;

import java.util.Objects;
import java.util.UUID;

/**
 * Polymorphic reference to the subject awaiting approval (W-15.2, 12-core-contracts.md §5 row 7).
 */
public record SubjectRef(String table, UUID id) {

    public SubjectRef {
        Objects.requireNonNull(table, "table must not be null");
        if (table.isBlank()) {
            throw new IllegalArgumentException("table must not be blank");
        }
        Objects.requireNonNull(id, "id must not be null");
    }
}
