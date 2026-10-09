package com.infinevo.payroll.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.template.TenantTemplateContributor;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Deduction;
import com.infinevo.payroll.component.DeductionRepository;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.component.PercentageOf;
import com.infinevo.payroll.component.SalaryComponent;
import com.infinevo.shared.entitlement.PlatformModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The {@code salary_components} section of a country template (W-73.9): the common earnings, with their
 * named earning types (D-37), and the common non-statutory deductions. Written only for a tenant with no
 * earning and no deduction of its own; a code the tenant once used and deleted is left alone.
 *
 * <p>Amounts are JSON strings, parsed to {@link BigDecimal}. Every row is active, in CTC and in the salary
 * structure, and shown on the payslip - the component drawer's defaults ({@code componentFields.js}).
 */
@Component
public class SalaryComponentTemplateContributor implements TenantTemplateContributor {

    public static final String SECTION = "salary_components";

    private static final String NEVER = "NEVER";
    private static final String MONTHLY = "MONTHLY";
    private static final String PRE_TAX = "PRE_TAX";

    private final EarningRepository earningRepository;
    private final DeductionRepository deductionRepository;

    public SalaryComponentTemplateContributor(
            EarningRepository earningRepository, DeductionRepository deductionRepository) {
        this.earningRepository = Objects.requireNonNull(earningRepository, "earningRepository must not be null");
        this.deductionRepository = Objects.requireNonNull(deductionRepository, "deductionRepository must not be null");
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
        if (earningRepository.existsByTenantIdAndDeletedFalse(tenantId)
                || deductionRepository.existsByTenantIdAndDeletedFalse(tenantId)) {
            return false;
        }
        boolean wrote = false;
        for (JsonNode row : payload.path("earnings")) {
            if (earningRepository.existsByTenantIdAndCode(
                    tenantId, row.path("code").asText())) {
                continue;
            }
            Earning earning = new Earning(tenantId, ACTOR);
            common(earning, row);
            earning.setEarningType(row.path("earning_type").asText());
            earning.setVariable(row.path("variable").asBoolean(false));
            earning.setEarningFrequency(MONTHLY);
            earning.setProRata(row.path("pro_rata").asBoolean(true));
            earning.setTaxable(row.path("taxable").asBoolean(true));
            earning.setIncludedInCtc(true);
            earning.setIncludedInSalaryStructure(true);
            String epf = row.path("epf_inclusion_type").asText(NEVER);
            earning.setEpfInclusionType(epf);
            earning.setIncludedInEpf(!NEVER.equals(epf));
            earning.setIncludedInEsi(row.path("included_in_esi").asBoolean(false));
            earning.setShowInPayslip(true);
            earningRepository.save(earning);
            wrote = true;
        }
        for (JsonNode row : payload.path("deductions")) {
            if (deductionRepository.existsByTenantIdAndCode(
                    tenantId, row.path("code").asText())) {
                continue;
            }
            Deduction deduction = new Deduction(tenantId, ACTOR);
            common(deduction, row);
            String type = row.path("deduction_type").asText("POST_TAX");
            deduction.setDeductionType(type);
            deduction.setPreTax(PRE_TAX.equals(type));
            deduction.setRecurring(row.path("recurring").asBoolean(false));
            deductionRepository.save(deduction);
            wrote = true;
        }
        return wrote;
    }

    private static void common(SalaryComponent component, JsonNode row) {
        component.setCode(row.path("code").asText());
        component.setName(row.path("name").asText());
        CalculationType type =
                CalculationType.valueOf(row.path("calculation_type").asText("FLAT"));
        component.setCalculationType(type);
        JsonNode value = row.path("default_value");
        component.setDefaultValue(value.isTextual() ? new BigDecimal(value.asText()) : null);
        if (type == CalculationType.PERCENTAGE) {
            component.setPercentageOf(
                    PercentageOf.valueOf(row.path("percentage_of").asText()));
        }
        component.setActive(true);
    }
}
