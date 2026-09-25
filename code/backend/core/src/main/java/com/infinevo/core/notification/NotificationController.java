package com.infinevo.core.notification;

import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/notifications} — the caller's own in-app notifications (W-20.1, spec section 4).
 *
 * <p><strong>Recipient only, with no action code</strong>, as the spec's API table says: every member
 * may read their own notifications, and nobody may read anyone else's through here, whatever they hold.
 * The scoping is in the query — tenant and recipient — so there is no permission to check; that is why
 * {@code EndpointGuardCoverageTest} lists this controller as exempt, with the reason.
 *
 * <p>Until {@code W-13.4} links a login to an employee, every caller is linked to nobody, so the list is
 * empty and nothing can be marked read ({@link NotificationRecipientResolver}).
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController extends NotificationErrors {

    static final int DEFAULT_PAGE_SIZE = 20;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
    }

    @GetMapping
    public Page<NotificationResponse> mine(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        return notificationService.mine(unreadOnly, PageRequest.of(Math.max(page, 0), Math.max(size, 1)));
    }

    /** {@code 200}; {@code 404} for a notification that is not the caller's. Reading twice is harmless. */
    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable("id") UUID id) {
        return notificationService.markRead(id);
    }
}
