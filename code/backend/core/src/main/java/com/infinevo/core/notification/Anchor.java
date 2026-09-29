package com.infinevo.core.notification;

/**
 * What a reminder rule's {@code offset_days} counts from (W-20.2, contracts §5 row 15).
 *
 * <ul>
 *   <li>{@link #WEEKLY}: weekly rule firing on {@code day_of_week} at local time.
 *   <li>{@link #DECLARATION_LOCK_DATE}: offset counted from the IT declaration auto-lock date.
 *   <li>{@link #POI_DUE_DATE}: offset counted from the proof-of-investment submission due date.
 *   <li>{@link #LEAVE_START}: offset counted from leave request start date.
 * </ul>
 */
public enum Anchor {
    WEEKLY,
    DECLARATION_LOCK_DATE,
    POI_DUE_DATE,
    LEAVE_START
}
