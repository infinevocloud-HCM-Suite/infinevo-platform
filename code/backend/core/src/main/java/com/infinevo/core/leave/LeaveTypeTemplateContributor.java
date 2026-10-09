package com.infinevo.core.leave;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.template.TenantTemplateContributor;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The {@code leave_types} section of a country template (W-73.9): the country's standard leave types, valid
 * from the first day of the current year. Written only for a tenant with no leave type at all; the tenant
 * sets the allowances in its leave policies.
 *
 * <p>Payload: {@code {"types": [{"code": "EL", "name": "Earned Leave", "paid": true, "half_day": true}]}}.
 */
@Component
public class LeaveTypeTemplateContributor implements TenantTemplateContributor {

    public static final String SECTION = "leave_types";

    private final LeaveTypeRepository repository;

    public LeaveTypeTemplateContributor(LeaveTypeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public String section() {
        return SECTION;
    }

    @Override
    public boolean apply(UUID tenantId, JsonNode payload, LocalDate today) {
        if (repository.existsByTenantId(tenantId)) {
            return false;
        }
        LocalDate validFrom = today.withDayOfYear(1);
        boolean wrote = false;
        for (JsonNode type : payload.path("types")) {
            LeaveType leaveType = new LeaveType(
                    tenantId,
                    type.path("code").asText(),
                    type.path("name").asText(),
                    type.path("paid").asBoolean(true),
                    LeaveUnit.DAYS,
                    type.path("half_day").asBoolean(false),
                    validFrom,
                    null,
                    true);
            leaveType.setCreatedBy(ACTOR);
            leaveType.setUpdatedBy(ACTOR);
            repository.save(leaveType);
            wrote = true;
        }
        return wrote;
    }
}
