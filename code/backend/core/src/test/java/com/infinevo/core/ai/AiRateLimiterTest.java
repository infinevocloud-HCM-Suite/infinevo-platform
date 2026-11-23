package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-114.4 spec, "Per-user rate limit" and Tests: {@link AiRateLimiter} with a fixed clock. The repository answers its
 * two queries from rows held here, filtered as the JPQL filters them (tenant, the user's sessions, {@code USER} role,
 * {@code created_at >= since}); {@code AiMessageIT} runs the real queries against PostgreSQL.
 */
class AiRateLimiterTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID me = UUID.randomUUID();
    private final UUID colleague = UUID.randomUUID();
    private final List<Row> rows = new ArrayList<>();
    private AiMessageRepository repository;

    private record Row(UUID tenantId, UUID userId, AiMessageRole role, Instant createdAt) {}

    @BeforeEach
    void setUp() {
        repository = mock(AiMessageRepository.class);
        when(repository.countUserMessagesSince(any(), any(), any()))
                .thenAnswer(inv -> counted(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2))
                        .count());
        when(repository.findOldestUserMessageSince(any(), any(), any()))
                .thenAnswer(inv -> counted(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2))
                        .map(Row::createdAt)
                        .min(Comparator.naturalOrder()));
    }

    @Test
    @DisplayName(
            "minute: the 10th message in a minute passes, the 11th is refused with Retry-After to the oldest + 60s")
    void minuteLimit() {
        Instant now = Instant.parse("2026-11-23T10:00:30Z");
        for (int i = 0; i < 9; i++) {
            add(me, AiMessageRole.USER, now.minusSeconds(50 - i));
        }
        AiRateLimiter limiter = limiter(now);
        assertThatCode(() -> limiter.check(tenant, me)).as("10th").doesNotThrowAnyException();

        add(me, AiMessageRole.USER, now.minusSeconds(1));
        assertThatThrownBy(() -> limiter.check(tenant, me))
                .as("11th")
                .isInstanceOfSatisfying(AiRateLimitedException.class, e -> {
                    assertThat(e.limit()).isEqualTo(AiRateLimitedException.Limit.MINUTE);
                    assertThat(e.getMessage()).isEqualTo(AiRateLimitedException.MINUTE_MESSAGE);
                    // The oldest counted is 50 s old: it stops counting just after 10 more seconds.
                    assertThat(e.retryAfterSeconds()).isEqualTo(11);
                });
    }

    @Test
    @DisplayName("minute: a message 61 seconds old no longer counts")
    void sixtyOneSecondsOld() {
        Instant now = Instant.parse("2026-11-23T10:05:00Z");
        add(me, AiMessageRole.USER, now.minusSeconds(61));
        for (int i = 0; i < 9; i++) {
            add(me, AiMessageRole.USER, now.minusSeconds(10));
        }

        assertThatCode(() -> limiter(now).check(tenant, me)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("day: the 201st message of a UTC day is refused with Retry-After to 00:00 UTC; the first after passes")
    void dayLimit() {
        Instant evening = Instant.parse("2026-11-23T23:59:00Z");
        for (int i = 0; i < 200; i++) {
            add(me, AiMessageRole.USER, Instant.parse("2026-11-23T00:00:00Z").plusSeconds(i * 300L));
        }

        assertThatThrownBy(() -> limiter(evening).check(tenant, me))
                .isInstanceOfSatisfying(AiRateLimitedException.class, e -> {
                    assertThat(e.limit()).isEqualTo(AiRateLimitedException.Limit.DAY);
                    assertThat(e.getMessage()).isEqualTo(AiRateLimitedException.DAY_MESSAGE);
                    assertThat(e.retryAfterSeconds()).isEqualTo(60);
                });
        assertThatCode(() -> limiter(Instant.parse("2026-11-24T00:00:00Z")).check(tenant, me))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("day: 199 earlier today passes; the count starts at 00:00 UTC, not 24 hours back")
    void dayStartsAtMidnightUtc() {
        Instant now = Instant.parse("2026-11-23T00:30:00Z");
        for (int i = 0; i < 199; i++) {
            add(me, AiMessageRole.USER, Instant.parse("2026-11-22T20:00:00Z").plusSeconds(i));
        }
        add(me, AiMessageRole.USER, Instant.parse("2026-11-23T00:00:00Z"));

        assertThatCode(() -> limiter(now).check(tenant, me)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ASSISTANT and TOOL rows, another user's rows and another tenant's rows are not counted")
    void onlyOwnUserRows() {
        Instant now = Instant.parse("2026-11-23T10:00:00Z");
        for (int i = 0; i < 20; i++) {
            add(me, AiMessageRole.ASSISTANT, now.minusSeconds(5));
            add(me, AiMessageRole.TOOL, now.minusSeconds(5));
            add(colleague, AiMessageRole.USER, now.minusSeconds(5));
            rows.add(new Row(UUID.randomUUID(), me, AiMessageRole.USER, now.minusSeconds(5)));
        }
        for (int i = 0; i < 9; i++) {
            add(me, AiMessageRole.USER, now.minusSeconds(5));
        }

        assertThatCode(() -> limiter(now).check(tenant, me)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter(now).check(tenant, colleague)).isInstanceOf(AiRateLimitedException.class);
    }

    @Test
    @DisplayName("the limits come from the settings")
    void settings() {
        Instant now = Instant.parse("2026-11-23T10:00:00Z");
        add(me, AiMessageRole.USER, now.minusSeconds(1));
        add(me, AiMessageRole.USER, now.minusSeconds(1));

        assertThatThrownBy(
                        () -> new AiRateLimiter(repository, 2, 200, Clock.fixed(now, ZoneOffset.UTC)).check(tenant, me))
                .isInstanceOf(AiRateLimitedException.class);
        assertThatThrownBy(
                        () -> new AiRateLimiter(repository, 10, 2, Clock.fixed(now, ZoneOffset.UTC)).check(tenant, me))
                .isInstanceOfSatisfying(AiRateLimitedException.class, e -> assertThat(e.limit())
                        .isEqualTo(AiRateLimitedException.Limit.DAY));
        assertThatThrownBy(() -> new AiRateLimiter(repository, 0, 200)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Retry-After is whole seconds rounded up, never below 1")
    void ceilSeconds() {
        Instant now = Instant.parse("2026-11-23T10:00:00Z");
        assertThat(AiRateLimiter.ceilSeconds(now, now)).isEqualTo(1);
        assertThat(AiRateLimiter.ceilSeconds(now, now.plusMillis(1))).isEqualTo(1);
        assertThat(AiRateLimiter.ceilSeconds(now, now.plusMillis(1001))).isEqualTo(2);
        assertThat(AiRateLimiter.ceilSeconds(now, now.plusSeconds(5))).isEqualTo(5);
        assertThat(new AiRateLimitedException(AiRateLimitedException.Limit.MINUTE, 0).retryAfterSeconds())
                .isEqualTo(1);
    }

    private AiRateLimiter limiter(Instant now) {
        return new AiRateLimiter(repository, 10, 200, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void add(UUID user, AiMessageRole role, Instant at) {
        rows.add(new Row(tenant, user, role, at));
    }

    private Stream<Row> counted(UUID tenantId, UUID userId, Instant since) {
        return rows.stream()
                .filter(r -> r.tenantId().equals(tenantId)
                        && r.userId().equals(userId)
                        && r.role() == AiMessageRole.USER
                        && !r.createdAt().isBefore(since));
    }
}
