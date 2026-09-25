package com.infinevo.core.notification;

/**
 * {@code PUT /api/v1/notification-templates/{event}} (W-20.1). One channel per call: an email body is
 * HTML and an in-app body is plain text, so the two are never the same wording.
 *
 * @param subject required for {@code EMAIL}; ignored for {@code IN_APP}
 * @param body uses only the event's placeholders, as {@code ${name}}
 * @param active null means true; false stops the event notifying on this channel, which then makes
 *     {@code compose} fail loudly rather than skip — switching a channel off is a decision, not a default
 */
public record NotificationTemplateRequest(Channel channel, String subject, String body, Boolean active) {}
