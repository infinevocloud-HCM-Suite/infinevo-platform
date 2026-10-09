package com.infinevo.payroll.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.template.TenantTemplateContributor;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PaySchedule;
import com.infinevo.payroll.schedule.PayScheduleRepository;
import com.infinevo.shared.entitlement.PlatformModule;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The {@code pay_schedule} section of a country template (W-73.9): frequency, working week, pay-day rule and
 * input cut-off. The first period is the month the template is applied in. Written only when the tenant has no
 * pay schedule.
 */
@Component
public class PayScheduleTemplateContributor implements TenantTemplateContributor {

    public static final String SECTION = "pay_schedule";

    private final PayScheduleRepository repository;

    public PayScheduleTemplateContributor(PayScheduleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public String section() {
        return SECTION;
    }

    @Override
    public PlatformModule module() {
        return PlatformModule.PAYROLL;
    }

    @Override
    public boolean apply(UUID tenantId, JsonNode payload, LocalDate today) {
        if (repository.findByTenantId(tenantId).isPresent()) {
            return false;
        }
        List<Short> days = new ArrayList<>();
        payload.path("working_days").forEach(d -> days.add((short) d.asInt()));
        PaySchedule schedule = new PaySchedule(
                tenantId,
                days.toArray(new Short[0]),
                PayDayRule.valueOf(payload.path("pay_day_rule").asText(PayDayRule.LAST_WORKING_DAY.name())),
                null,
                (short) payload.path("input_cutoff_day").asInt(PaySchedule.DEFAULT_INPUT_CUTOFF_DAY),
                today.withDayOfMonth(1));
        schedule.setFrequency(payload.path("frequency").asText(PaySchedule.DEFAULT_FREQUENCY));
        schedule.setCreatedBy(ACTOR);
        schedule.setUpdatedBy(ACTOR);
        repository.save(schedule);
        return true;
    }
}
