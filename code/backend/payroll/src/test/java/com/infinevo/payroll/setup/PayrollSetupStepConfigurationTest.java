package com.infinevo.payroll.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.setup.SetupStepCatalogue;
import com.infinevo.core.setup.SetupStepChecker;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayrollSetupStepConfigurationTest {

    @Test
    @DisplayName("PayrollSetupStepConfiguration provides checkers for all 7 payroll steps in catalogue")
    void providesCheckersForAllPayrollSteps() {
        PayrollSetupStepConfiguration config = new PayrollSetupStepConfiguration();

        List<SetupStepChecker> checkers = List.of(
                config.payScheduleSetupStepChecker(),
                config.priorPayrollSetupStepChecker(),
                config.organisationTaxSetupStepChecker(),
                config.salaryComponentsSetupStepChecker(),
                config.epfSetupStepChecker(),
                config.esiSetupStepChecker(),
                config.professionalTaxSetupStepChecker());

        Map<String, SetupStepChecker> byCode =
                checkers.stream().collect(Collectors.toMap(c -> c.code().toUpperCase(), c -> c));

        List<SetupStepCatalogue.StepDefinition> payrollSteps = SetupStepCatalogue.DEFAULT_STEPS.stream()
                .filter(s -> s.module() == PlatformModule.PAYROLL)
                .toList();

        assertThat(payrollSteps).hasSize(7);

        UUID tenantId = UUID.randomUUID();
        for (SetupStepCatalogue.StepDefinition step : payrollSteps) {
            SetupStepChecker checker = byCode.get(step.code().toUpperCase());
            assertThat(checker)
                    .as("Checker for step %s must be provided", step.code())
                    .isNotNull();
            assertThat(checker.module()).isEqualTo(PlatformModule.PAYROLL);
            assertThat(checker.isComplete(tenantId)).isFalse();
        }
    }

    @Test
    @DisplayName("Repository-backed checkers reflect repository data")
    @SuppressWarnings("unchecked")
    void repositoryBackedCheckersDetectCompletion() {
        PayrollSetupStepConfiguration config = new PayrollSetupStepConfiguration();
        UUID tenantId = UUID.randomUUID();

        // EPF
        com.infinevo.payroll.statutory.settings.EpfSettingRepository epfRepo =
                org.mockito.Mockito.mock(com.infinevo.payroll.statutory.settings.EpfSettingRepository.class);
        org.springframework.beans.factory.ObjectProvider<com.infinevo.payroll.statutory.settings.EpfSettingRepository>
                epfProvider = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(epfProvider.getIfAvailable()).thenReturn(epfRepo);
        org.mockito.Mockito.when(epfRepo.findByTenantId(tenantId))
                .thenReturn(java.util.Optional.of(new com.infinevo.payroll.statutory.settings.EpfSetting(tenantId)));
        SetupStepChecker epfChecker = config.epfSetupStepChecker(epfProvider);
        assertThat(epfChecker.isComplete(tenantId)).isTrue();

        // ESI
        com.infinevo.payroll.statutory.settings.EsiSettingRepository esiRepo =
                org.mockito.Mockito.mock(com.infinevo.payroll.statutory.settings.EsiSettingRepository.class);
        org.springframework.beans.factory.ObjectProvider<com.infinevo.payroll.statutory.settings.EsiSettingRepository>
                esiProvider = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(esiProvider.getIfAvailable()).thenReturn(esiRepo);
        org.mockito.Mockito.when(esiRepo.findByTenantId(tenantId))
                .thenReturn(java.util.Optional.of(new com.infinevo.payroll.statutory.settings.EsiSetting(tenantId)));
        SetupStepChecker esiChecker = config.esiSetupStepChecker(esiProvider);
        assertThat(esiChecker.isComplete(tenantId)).isTrue();

        // Salary Components
        com.infinevo.payroll.component.EarningRepository earningRepo =
                org.mockito.Mockito.mock(com.infinevo.payroll.component.EarningRepository.class);
        org.springframework.beans.factory.ObjectProvider<com.infinevo.payroll.component.EarningRepository>
                earningProvider = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(earningProvider.getIfAvailable()).thenReturn(earningRepo);
        org.mockito.Mockito.when(earningRepo.existsByTenantIdAndDeletedFalse(tenantId))
                .thenReturn(true);
        SetupStepChecker scChecker = config.salaryComponentsSetupStepChecker(earningProvider);
        assertThat(scChecker.isComplete(tenantId)).isTrue();

        // Professional Tax
        com.infinevo.payroll.statutory.pt.OrgPtOverrideRepository ptRepo =
                org.mockito.Mockito.mock(com.infinevo.payroll.statutory.pt.OrgPtOverrideRepository.class);
        org.springframework.beans.factory.ObjectProvider<com.infinevo.payroll.statutory.pt.OrgPtOverrideRepository>
                ptProvider = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(ptProvider.getIfAvailable()).thenReturn(ptRepo);
        org.mockito.Mockito.when(ptRepo.existsByTenantId(tenantId)).thenReturn(true);
        SetupStepChecker ptChecker = config.professionalTaxSetupStepChecker(ptProvider);
        assertThat(ptChecker.isComplete(tenantId)).isTrue();

        // Pay Schedule (W-28)
        com.infinevo.payroll.schedule.PayScheduleRepository payScheduleRepo =
                org.mockito.Mockito.mock(com.infinevo.payroll.schedule.PayScheduleRepository.class);
        org.springframework.beans.factory.ObjectProvider<com.infinevo.payroll.schedule.PayScheduleRepository>
                payScheduleProvider = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(payScheduleProvider.getIfAvailable()).thenReturn(payScheduleRepo);
        org.mockito.Mockito.when(payScheduleRepo.findByTenantId(tenantId))
                .thenReturn(java.util.Optional.of(
                        org.mockito.Mockito.mock(com.infinevo.payroll.schedule.PaySchedule.class)));
        SetupStepChecker psChecker = config.payScheduleSetupStepChecker(payScheduleProvider);
        assertThat(psChecker.isComplete(tenantId)).isTrue();
    }
}
