package com.infinevo.payroll.proof;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Works out how a proof's items must change to match the declared lines (W-34.1 spec section 3, "Item
 * sync"). Pure: it reads nothing and writes nothing, so every rule is a unit test.
 *
 * <ul>
 *   <li>a line with no item gets a new {@code PENDING} item;
 *   <li>an item whose line is gone is removed, with its file links;
 *   <li>an item whose line is still there keeps its identity, notes and files, and only its declared
 *       amount and description are refreshed.
 * </ul>
 */
public final class ProofItemSync {

    private ProofItemSync() {}

    /**
     * @param toCreate lines that need a new item
     * @param toRefresh items whose declared amount or description differs from the line
     * @param toRemove ids of items whose line is gone
     */
    public record Plan(List<ProofSourceLine> toCreate, List<Refresh> toRefresh, List<UUID> toRemove) {

        public boolean isEmpty() {
            return toCreate.isEmpty() && toRefresh.isEmpty() && toRemove.isEmpty();
        }
    }

    /** An existing item and the line it must now mirror. */
    public record Refresh(EmployeeProofItem item, ProofSourceLine line) {}

    public static Plan plan(List<ProofSourceLine> lines, List<EmployeeProofItem> existing) {
        Map<String, EmployeeProofItem> byKey = new HashMap<>();
        for (EmployeeProofItem item : existing) {
            byKey.put(item.getSourceKind() + ":" + item.getSourceLineId(), item);
        }

        List<ProofSourceLine> toCreate = new ArrayList<>();
        List<Refresh> toRefresh = new ArrayList<>();
        for (ProofSourceLine line : lines) {
            EmployeeProofItem item = byKey.remove(line.key());
            if (item == null) {
                toCreate.add(line);
            } else if (item.getDeclaredAmount().compareTo(line.declaredAmount()) != 0
                    || !item.getDescription().equals(line.description())) {
                toRefresh.add(new Refresh(item, line));
            }
        }
        // Whatever no line claimed is stale.
        List<UUID> toRemove = new ArrayList<>();
        for (EmployeeProofItem stale : byKey.values()) {
            toRemove.add(stale.getId());
        }
        return new Plan(toCreate, toRefresh, toRemove);
    }
}
