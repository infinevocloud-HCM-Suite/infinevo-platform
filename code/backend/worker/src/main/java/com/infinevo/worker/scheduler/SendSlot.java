package com.infinevo.worker.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Which day's send slot a sweep is looking at, for anything that fires "at HH:mm tenant-local time
 * on its day" (W-20.2 reminder rules, W-23.2 report schedules).
 *
 * <p>The sweeps run on a cron, every 15 minutes by default, and a naive "is the local time past the
 * send time on the same local day" check can never reach a send time after the last tick of the
 * day: 23:50 is not yet due at 23:45, and at 00:00 it is the next day. So a sweep shortly after
 * midnight still owns the previous day's slot, for {@link #LATE_GRACE} after that slot's moment.
 * The grace is bounded on purpose: without it a schedule saved at 08:00 for 09:00 would fire at once
 * as "yesterday's 09:00, never run".
 */
public final class SendSlot {

    /** How long after its moment a missed slot is still picked up. Covers the cron and its lock. */
    public static final Duration LATE_GRACE = Duration.ofHours(1);

    private SendSlot() {}

    /**
     * The local date whose {@code sendTime} slot this sweep should evaluate: today once the send time
     * has passed, yesterday for {@link #LATE_GRACE} after yesterday's send moment, otherwise nothing
     * is due yet.
     */
    public static Optional<LocalDate> dateDue(ZonedDateTime localNow, LocalTime sendTime) {
        LocalDate today = localNow.toLocalDate();
        if (!localNow.toLocalTime().isBefore(sendTime)) {
            return Optional.of(today);
        }
        ZonedDateTime yesterdaySlot = ZonedDateTime.of(today.minusDays(1), sendTime, localNow.getZone());
        if (Duration.between(yesterdaySlot, localNow).compareTo(LATE_GRACE) <= 0) {
            return Optional.of(today.minusDays(1));
        }
        return Optional.empty();
    }

    /**
     * The slot date a past run belongs to. A run recorded before the send time on its local day can
     * only have been a late run of the previous day's slot, so it counts against that day — otherwise
     * a 23:50 slot run at 00:01 would look like today's run and today's real slot would be skipped.
     */
    public static LocalDate slotDateOf(Instant ranAt, ZoneId zone, LocalTime sendTime) {
        ZonedDateTime local = ranAt.atZone(zone);
        return local.toLocalTime().isBefore(sendTime) ? local.toLocalDate().minusDays(1) : local.toLocalDate();
    }
}
