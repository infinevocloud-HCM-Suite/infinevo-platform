package com.infinevo.core.notification;

import java.util.List;

/**
 * A tenant's notification wording (W-20.1, spec section 13 decision 1: a tenant edits its own, starting
 * from the platform defaults {@code V038} seeds).
 */
public interface NotificationTemplateService {

    /** Every version in the bound tenant, or only one event's when {@code event} is given. */
    List<NotificationTemplateResponse> list(NotificationEvent event);

    /**
     * Sets an event's wording on one channel from today: a new version, or today's version replaced if
     * one exists. Earlier versions stay, so what earlier notifications were rendered from stays on record.
     */
    NotificationTemplateResponse put(NotificationEvent event, NotificationTemplateRequest request);
}
