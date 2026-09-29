package com.infinevo.core.payinput;

import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * What any module hands {@link PayInputService#record} (W-19 §4, {@code 12-core-contracts.md} §3
 * row 100) — the one seam every write to the ledger goes through.
 *
 * @param employeeId the employee the value belongs to, in the bound tenant
 * @param period the period the value is for; {@code record} may post it later if this one is locked
 * @param kind what the value means; the kind decides the sign, the value itself is always positive
 * @param quantity a day or hour count, or {@code null} when the input is amount-only
 * @param amount a monetary value, or {@code null} when the input is quantity-only, priced later
 * @param sourceModule the caller — always {@code "core"} through the HTTP controller; a module
 *     calling the Java seam directly names itself
 * @param sourceRef the caller's own reference for this event, unique with {@code sourceModule} for
 *     idempotency ({@code 12-core-contracts.md} §6 decision 2); {@code null} only for a call with no
 *     natural retry key
 * @param runRef the run this input is tagged to, or {@code null} for the regular run (W-30.1). A
 *     tagged input obeys its run's lock, not the period's, and is invisible to {@code forPeriod}
 *     and {@code forEmployee} — only {@code forRun(runRef)} reads it
 */
public record PayInputCommand(
        UUID employeeId,
        YearMonth period,
        PayInputKind kind,
        BigDecimal quantity,
        Money amount,
        String sourceModule,
        String sourceRef,
        UUID runRef) {

    /** Untagged — the regular run reads it. */
    public PayInputCommand(
            UUID employeeId,
            YearMonth period,
            PayInputKind kind,
            BigDecimal quantity,
            Money amount,
            String sourceModule,
            String sourceRef) {
        this(employeeId, period, kind, quantity, amount, sourceModule, sourceRef, null);
    }
}
