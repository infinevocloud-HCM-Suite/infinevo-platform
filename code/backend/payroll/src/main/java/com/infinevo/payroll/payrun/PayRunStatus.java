package com.infinevo.payroll.payrun;

import java.util.Map;
import java.util.Set;

/**
 * The status vocabulary for every part of W-29 (W-29.1 §3, §13 decision 5). All eight values exist
 * from day one and match the {@code CHECK} on {@code payroll.payrun.status}; later tickets add
 * transitions to {@link #ALLOWED}, never new values.
 *
 * <p>Legacy's seven map as: {@code DRAFT}→{@code DRAFT}, {@code SUBMITTED}/{@code APPROVAL_PENDING}→
 * {@code COMPUTED}, {@code APPROVED}→{@code APPROVED}, {@code COMPLETED}→{@code PAID},
 * {@code REJECTED}→{@code LOCKED}, {@code READY} unused.
 */
public enum PayRunStatus {
    DRAFT,
    LOCKED,
    COMPUTING,
    COMPUTED,
    FAILED,
    APPROVED,
    PAID,
    CANCELLED;

    /**
     * The transitions implemented so far — W-29.1: {@code DRAFT → LOCKED}, {@code DRAFT → CANCELLED},
     * {@code LOCKED → CANCELLED}; W-29.2: into {@code COMPUTING} from {@code LOCKED}, {@code COMPUTED}
     * and {@code FAILED}, and out of it to {@code COMPUTED} or {@code FAILED}. Every other pair is
     * refused until its ticket lands.
     */
    private static final Map<PayRunStatus, Set<PayRunStatus>> ALLOWED = Map.of(
            DRAFT, Set.of(LOCKED, CANCELLED),
            LOCKED, Set.of(CANCELLED, COMPUTING),
            COMPUTED, Set.of(COMPUTING, APPROVED),
            FAILED, Set.of(COMPUTING),
            COMPUTING, Set.of(COMPUTED, FAILED),
            APPROVED, Set.of(PAID, CANCELLED),
            PAID, Set.of(CANCELLED));

    public boolean canTransitionTo(PayRunStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * @throws IllegalPayRunTransitionException unless {@code this → target} is an allowed transition
     */
    public void requireTransitionTo(PayRunStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalPayRunTransitionException(this, target);
        }
    }
}
