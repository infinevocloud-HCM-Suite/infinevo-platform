package com.infinevo.core.payinput;

/**
 * What a {@code core.pay_input} row means (W-19 §4, {@code 12-core-contracts.md} §3). The kind
 * decides the sign: {@link #LOP_DAYS} and {@link #AD_HOC_DEDUCTION} reduce pay, the other three add
 * to it. {@code amount} and {@code quantity} on the row are always stored positive — the legacy
 * convention at {@code EmployeePayRunServiceImpl.java:337-363} — so the sign lives here, in code,
 * rather than in the sign of a stored number.
 */
public enum PayInputKind {
    LOP_DAYS(false),
    OVERTIME(true),
    REIMBURSEMENT(true),
    AD_HOC_DEDUCTION(false),
    ONE_TIME_PAYOUT(true);

    private final boolean addsToPay;

    PayInputKind(boolean addsToPay) {
        this.addsToPay = addsToPay;
    }

    /** {@code true} for a kind that increases pay, {@code false} for one that reduces it. */
    public boolean addsToPay() {
        return addsToPay;
    }
}
