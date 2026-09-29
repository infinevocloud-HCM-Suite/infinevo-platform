package com.infinevo.core.notification;

/**
 * How a notification reaches its recipient (W-20.1). SMS and push are out of scope (spec section 2).
 *
 * <p>The HRMS {@code notifications} table was in-app only, with no email field; Payroll had email only,
 * through its email provider. One row with a channel column replaces both.
 */
public enum Channel {
    /** Shown in the app. Delivered by being stored: nothing sends it. */
    IN_APP,
    /** Sent by {@code W-20.2} on {@code worker}. Nothing in this ticket sends anything. */
    EMAIL
}
