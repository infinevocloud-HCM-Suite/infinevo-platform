package com.infinevo.payroll.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.template.TenantTemplateContributor;
import com.infinevo.payroll.statutory.settings.EpfSetting;
import com.infinevo.payroll.statutory.settings.EpfSettingRepository;
import com.infinevo.payroll.statutory.settings.EsiSetting;
import com.infinevo.payroll.statutory.settings.EsiSettingRepository;
import com.infinevo.shared.entitlement.PlatformModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * The {@code statutory} section of a country template (W-73.9): the tenant's EPF and ESI settings with the
 * statutory rates and ceilings, seeded <strong>disabled</strong> - the tenant opts in with its registration
 * number. Each is written only when the tenant has no row of its own. Professional tax has no per-tenant
 * settings row: its slabs come from {@code reference.pt_slab} by each work location's state.
 *
 * <p>Rates and ceilings are JSON strings, parsed to {@link BigDecimal}.
 */
@Component
public class StatutoryTemplateContributor implements TenantTemplateContributor {

    public static final String SECTION = "statutory";

    private final EpfSettingRepository epfRepository;
    private final EsiSettingRepository esiRepository;

    public StatutoryTemplateContributor(EpfSettingRepository epfRepository, EsiSettingRepository esiRepository) {
        this.epfRepository = Objects.requireNonNull(epfRepository, "epfRepository must not be null");
        this.esiRepository = Objects.requireNonNull(esiRepository, "esiRepository must not be null");
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
        boolean wrote = false;
        JsonNode epf = payload.path("epf");
        if (epf.isObject() && epfRepository.findByTenantId(tenantId).isEmpty()) {
            EpfSetting setting = new EpfSetting(tenantId, ACTOR);
            setting.setEnabled(epf.path("enabled").asBoolean(false));
            decimal(epf, "employee_rate", setting::setEmployeeRate);
            decimal(epf, "employer_rate", setting::setEmployerRate);
            decimal(epf, "eps_rate", setting::setEpsRate);
            decimal(epf, "edli_rate", setting::setEdliRate);
            decimal(epf, "admin_charge_rate", setting::setAdminChargeRate);
            decimal(epf, "wage_ceiling", setting::setWageCeiling);
            if (epf.hasNonNull("eps_senior_age")) {
                setting.setEpsSeniorAge((short) epf.path("eps_senior_age").asInt());
            }
            epfRepository.save(setting);
            wrote = true;
        }
        JsonNode esi = payload.path("esi");
        if (esi.isObject() && esiRepository.findByTenantId(tenantId).isEmpty()) {
            EsiSetting setting = new EsiSetting(tenantId, ACTOR);
            setting.setEnabled(esi.path("enabled").asBoolean(false));
            decimal(esi, "employee_rate", setting::setEmployeeRate);
            decimal(esi, "employer_rate", setting::setEmployerRate);
            decimal(esi, "wage_ceiling", setting::setWageCeiling);
            esiRepository.save(setting);
            wrote = true;
        }
        return wrote;
    }

    private static void decimal(JsonNode node, String field, Consumer<BigDecimal> setter) {
        if (node.hasNonNull(field)) {
            setter.accept(new BigDecimal(node.path(field).asText()));
        }
    }
}
