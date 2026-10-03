package com.infinevo.core.employee.detail;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The result of one PAN lookup in the bound tenant (W-36.5, spec sections 4 and 9).
 *
 * <p>{@code unique} maps each upper-cased PAN exactly one live employee holds to that employee.
 * {@code ambiguous} holds the upper-cased PANs two or more live employees hold — never resolved to
 * either, so the caller can report them as skipped rather than unmatched. A PAN no live employee holds
 * is in neither. The two never overlap.
 *
 * @param unique upper-cased PAN to the one live employee holding it
 * @param ambiguous upper-cased PANs held by more than one live employee
 */
public record PanLookup(Map<String, UUID> unique, Set<String> ambiguous) {

    /** Nothing found. */
    public static final PanLookup EMPTY = new PanLookup(Map.of(), Set.of());

    public PanLookup {
        unique = Map.copyOf(unique);
        ambiguous = Set.copyOf(ambiguous);
    }
}
