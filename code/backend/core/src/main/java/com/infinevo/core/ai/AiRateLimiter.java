package com.infinevo.core.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The per-user rate limit of the AI helper (W-114.4, spec "Per-user rate limit", rule 10): at most {@code per-minute}
 * messages in any 60 seconds and {@code per-day} in one UTC day, per user. Counted from the user's own {@code USER}
 * rows in {@code core.ai_message}, found through their sessions; no table of its own.
 *
 * <p>Called by {@link AiChatServiceImpl} after the session check and before {@link AiAccessGate#consume()}, so a
 * refused message is not counted against the tenant cap, stores no row and calls no model. The count is a read, not a
 * lock: two messages sent at the same instant can both pass (accepted in the spec).
 */
@Component
public class AiRateLimiter {

    static final Duration MINUTE = Duration.ofSeconds(60);

    private final AiMessageRepository messages;
    private final int perMinute;
    private final int perDay;
    private final Clock clock;

    @Autowired
    public AiRateLimiter(
            AiMessageRepository messages,
            @Value("${infinevo.ai.rate.per-minute:10}") int perMinute,
            @Value("${infinevo.ai.rate.per-day:200}") int perDay) {
        this(messages, perMinute, perDay, Clock.systemUTC());
    }

    AiRateLimiter(AiMessageRepository messages, int perMinute, int perDay, Clock clock) {
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        if (perMinute < 1 || perDay < 1) {
            throw new IllegalArgumentException("AI rate limits must be at least 1");
        }
        this.perMinute = perMinute;
        this.perDay = perDay;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Passes when the user may send one more message; otherwise throws. Call inside a transaction bound to the tenant.
     *
     * @throws AiRateLimitedException with {@code Retry-After}: for the day limit until the next 00:00 UTC, for the
     *     minute limit until the oldest counted message is 60 seconds old
     */
    public void check(UUID tenantId, UUID userId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Instant now = clock.instant();

        Instant dayStart =
                LocalDate.ofInstant(now, ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        if (messages.countUserMessagesSince(tenantId, userId, dayStart) >= perDay) {
            Instant nextDay = dayStart.plus(Duration.ofDays(1));
            throw new AiRateLimitedException(AiRateLimitedException.Limit.DAY, ceilSeconds(now, nextDay));
        }

        Instant minuteStart = now.minus(MINUTE);
        if (messages.countUserMessagesSince(tenantId, userId, minuteStart) >= perMinute) {
            // The window includes its start, so a message exactly 60 seconds old still counts: free just after that.
            Instant free = messages.findOldestUserMessageSince(tenantId, userId, minuteStart)
                    .map(oldest -> oldest.plus(MINUTE).plusNanos(1))
                    .orElse(now.plus(MINUTE));
            throw new AiRateLimitedException(AiRateLimitedException.Limit.MINUTE, ceilSeconds(now, free));
        }
    }

    /** Whole seconds from {@code now} to {@code until}, rounded up, at least 1. */
    static long ceilSeconds(Instant now, Instant until) {
        Duration d = Duration.between(now, until);
        long seconds = d.getSeconds() + (d.getNano() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }
}
