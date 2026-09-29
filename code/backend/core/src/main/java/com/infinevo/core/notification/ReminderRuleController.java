package com.infinevo.core.notification;

import com.infinevo.shared.authz.RequiresAction;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/reminder-rules} — tenant reminder rule management (W-20.2).
 *
 * <p>Every verb requires {@code core.reminder_rule.manage}, added to the catalogue by {@code W-11.3}
 * ({@code V025}).
 */
@RestController
@RequestMapping("/api/v1/reminder-rules")
@RequiresAction("core.reminder_rule.manage")
public class ReminderRuleController extends NotificationErrors {

    private final ReminderRuleService reminderRuleService;

    public ReminderRuleController(ReminderRuleService reminderRuleService) {
        this.reminderRuleService = Objects.requireNonNull(reminderRuleService, "reminderRuleService must not be null");
    }

    @GetMapping
    public List<ReminderRuleResponse> list(
            @RequestParam(value = "event", required = false) NotificationEvent event,
            @RequestParam(value = "isActive", required = false) Boolean isActive) {
        return reminderRuleService.list(event, isActive);
    }

    @GetMapping("/{id}")
    public ReminderRuleResponse get(@PathVariable("id") UUID id) {
        return reminderRuleService.get(id);
    }

    @PostMapping
    public ResponseEntity<ReminderRuleResponse> create(@RequestBody ReminderRuleRequest request) {
        ReminderRuleResponse created = reminderRuleService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/reminder-rules/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public ReminderRuleResponse update(@PathVariable("id") UUID id, @RequestBody ReminderRuleRequest request) {
        return reminderRuleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        reminderRuleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
