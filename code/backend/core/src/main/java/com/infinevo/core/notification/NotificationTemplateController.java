package com.infinevo.core.notification;

import com.infinevo.shared.authz.RequiresAction;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/notification-templates} — the tenant's wording, per event and channel (W-20.1).
 *
 * <p>Both endpoints need {@code core.notification_template.manage}, added to the catalogue by
 * {@code W-11.3} ({@code V025}).
 */
@RestController
@RequestMapping("/api/v1/notification-templates")
@RequiresAction("core.notification_template.manage")
public class NotificationTemplateController extends NotificationErrors {

    private final NotificationTemplateService templateService;

    public NotificationTemplateController(NotificationTemplateService templateService) {
        this.templateService = Objects.requireNonNull(templateService, "templateService must not be null");
    }

    @GetMapping
    public List<NotificationTemplateResponse> list(
            @RequestParam(value = "event", required = false) NotificationEvent event) {
        return templateService.list(event);
    }

    /** {@code 200} with the version now in force from today. */
    @PutMapping("/{event}")
    public NotificationTemplateResponse put(
            @PathVariable("event") NotificationEvent event, @RequestBody NotificationTemplateRequest request) {
        return templateService.put(event, request);
    }
}
