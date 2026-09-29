package com.infinevo.core.payinput;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code GET /api/v1/pay-inputs?runRef=} response (W-30.1 §4): every row tagged to one run,
 * across all employees, plus a quantity and an amount total per kind broken down by employee —
 * what the run pays each of them, not a run-wide blend across all of them. {@link PayInputListResponse}
 * is wrong here on purpose: {@code forPeriod}'s and {@code forEmployee}'s totals are a single
 * period-wide or already-one-employee figure, but {@code W-30.2} needs one employee's own totals to
 * price that employee's line, not everyone's summed together.
 *
 * <p>Two totals per employee, not one, the same reason {@link PayInputListResponse} splits them: a
 * kind may be quantity-only, amount-only or both.
 */
public record PayInputRunResponse(
        @JsonProperty("rows") List<PayInputResponse> rows,
        @JsonProperty("quantity_totals_by_employee_and_kind")
                Map<UUID, Map<PayInputKind, BigDecimal>> quantityTotalsByEmployeeAndKind,
        @JsonProperty("amount_totals_by_employee_and_kind")
                Map<UUID, Map<PayInputKind, BigDecimal>> amountTotalsByEmployeeAndKind) {}
