package com.infinevo.core.ai;

/**
 * The caller has sent too many messages to the AI helper (W-114.4, spec "Per-user rate limit"). Answers
 * {@code 429 AI_RATE_LIMITED} with a {@code Retry-After} header of {@link #retryAfterSeconds()}.
 */
public class AiRateLimitedException extends RuntimeException {

    static final String MINUTE_MESSAGE = "You are sending messages too quickly. Try again in a moment.";
    static final String DAY_MESSAGE = "You have reached today's limit for the AI helper. Try again tomorrow.";

    /** Which limit refused the message. */
    public enum Limit {
        MINUTE,
        DAY
    }

    private final Limit limit;
    private final long retryAfterSeconds;

    public AiRateLimitedException(Limit limit, long retryAfterSeconds) {
        super(limit == Limit.DAY ? DAY_MESSAGE : MINUTE_MESSAGE);
        this.limit = limit;
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public Limit limit() {
        return limit;
    }

    /** Whole seconds until a message would pass, at least 1. */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
