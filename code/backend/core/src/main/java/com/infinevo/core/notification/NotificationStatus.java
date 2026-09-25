package com.infinevo.core.notification;

/**
 * Where a notification is in its delivery (W-20.1). Whether it has been read is {@code read_at}, not a
 * status — reading is the recipient's, delivery is the platform's.
 */
public enum NotificationStatus {
    /** An email waiting for {@code W-20.2} to send it. The row is the outbox: the queue message is a nudge. */
    QUEUED,
    /** Delivered: an email sent, or an in-app notification stored — which is its delivery. */
    SENT,
    /** {@code W-20.2} gave up on it. */
    FAILED
}
