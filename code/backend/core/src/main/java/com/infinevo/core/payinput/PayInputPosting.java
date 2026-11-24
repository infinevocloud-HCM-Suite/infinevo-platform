package com.infinevo.core.payinput;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.YearMonth;
import java.util.Objects;

/**
 * {@code GET /api/v1/pay-inputs/posting/{period}} (D-144): where an untagged entry for
 * {@code requestedPeriod} would land, and why. Built by the one method {@link PayInputService#record}
 * also uses, so what a screen shows before saving and what saving does cannot drift.
 *
 * @param requestedPeriod the month asked for
 * @param postedPeriod the month whose run picks the entry up - {@code requestedPeriod} unless it is
 *     CLOSED, then the first month after it that is not CLOSED
 * @param state OPEN, LOCKED (posts there, the run must be recomputed) or CLOSED
 */
public record PayInputPosting(
        @JsonProperty("requested_period") YearMonth requestedPeriod,
        @JsonProperty("posted_period") YearMonth postedPeriod,
        @JsonProperty("state") PayInputPostingState state) {

    public PayInputPosting {
        Objects.requireNonNull(requestedPeriod, "requestedPeriod must not be null");
        Objects.requireNonNull(postedPeriod, "postedPeriod must not be null");
        Objects.requireNonNull(state, "state must not be null");
    }
}
