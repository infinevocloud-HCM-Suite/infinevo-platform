package com.infinevo.core.notification;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service managing reminder rules per tenant (W-20.2).
 */
public interface ReminderRuleService {

    /**
     * The values the reminder evaluator supplies to a template. A rule may name only an event whose
     * placeholders are all among them — otherwise every run would render nothing and log an error.
     */
    Set<String> SUPPLIED_PLACEHOLDERS = Set.of("employee_name", "due_date", "week_start", "financial_year", "period");

    List<ReminderRuleResponse> list(NotificationEvent event, Boolean isActive);

    ReminderRuleResponse get(UUID id);

    ReminderRuleResponse create(ReminderRuleRequest request);

    ReminderRuleResponse update(UUID id, ReminderRuleRequest request);

    void delete(UUID id);
}
