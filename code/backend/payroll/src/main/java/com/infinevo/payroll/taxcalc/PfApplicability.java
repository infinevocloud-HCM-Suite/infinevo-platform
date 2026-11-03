package com.infinevo.payroll.taxcalc;

import com.infinevo.payroll.salary.EmployeeStatutoryProfileService;
import com.infinevo.payroll.salary.StatutoryProfileResponse;
import com.infinevo.payroll.statutory.settings.EpfSettingResponse;
import com.infinevo.payroll.statutory.settings.StatutorySettingsService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Whether employee PF is deducted for an employee now: the company EPF setting is on and the
 * employee is ticked for PF — the pay run's own test ({@code StatutoryLineContributor}, W-76.3).
 * The tax projection uses it so TDS does not assume PF the pay run no longer deducts (W-78.1).
 */
@Component
public class PfApplicability {

    private final StatutorySettingsService statutorySettingsService;
    private final EmployeeStatutoryProfileService employeeStatutoryProfileService;

    public PfApplicability(
            StatutorySettingsService statutorySettingsService,
            EmployeeStatutoryProfileService employeeStatutoryProfileService) {
        this.statutorySettingsService =
                Objects.requireNonNull(statutorySettingsService, "statutorySettingsService must not be null");
        this.employeeStatutoryProfileService = Objects.requireNonNull(
                employeeStatutoryProfileService, "employeeStatutoryProfileService must not be null");
    }

    public boolean applies(UUID tenantId, UUID employeeId) {
        EpfSettingResponse epf = statutorySettingsService.epf(tenantId);
        if (epf == null || !epf.isEnabled()) {
            return false;
        }
        StatutoryProfileResponse profile = employeeStatutoryProfileService.get(employeeId);
        return profile != null && profile.eligibleForPf();
    }
}
