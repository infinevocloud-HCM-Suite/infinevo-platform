package com.infinevo.core.payinput;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/v1/pay-inputs} response (W-19 §4): the rows, plus a total per kind.
 *
 * <p>Two totals, not one — a kind is quantity-only, amount-only or both (an hours-only overtime
 * entry has no amount until the pay run prices it, {@code W-39.2} §4), and adding a day count to a
 * money figure would mean nothing. Each map sums only the rows that carry that figure.
 *
 * <p>This is the one arithmetic the ledger does — a plain signed sum, not a calculation (spec §9:
 * "no arithmetic beyond summing by kind"). A reversal row is stored positive like any other, so an
 * original row's value is added to its kind's total and a row whose {@code reversesId} is set is
 * subtracted instead — exactly what "the two net to zero" means in
 * {@code PayInputServiceImplTest} and {@code OvertimeLedgerIT}.
 */
public record PayInputListResponse(
        @JsonProperty("rows") List<PayInputResponse> rows,
        @JsonProperty("quantity_totals_by_kind") Map<PayInputKind, BigDecimal> quantityTotalsByKind,
        @JsonProperty("amount_totals_by_kind") Map<PayInputKind, BigDecimal> amountTotalsByKind) {}
