package com.infinevo.payroll.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.org.WorkLocation;
import com.infinevo.core.org.WorkLocationRepository;
import com.infinevo.core.setup.EmployeeSetupStepChecker;
import com.infinevo.core.setup.SetupChecklistService;
import com.infinevo.core.setup.SetupStepCatalogue;
import com.infinevo.core.setup.SetupStepChecker;
import com.infinevo.core.setup.TenantSetupStepRepository;
import com.infinevo.core.setup.WorkLocationSetupStepChecker;
import com.infinevo.core.template.TenantTemplateContributor;
import com.infinevo.payroll.component.DeductionRepository;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.schedule.PaySchedule;
import com.infinevo.payroll.schedule.PayScheduleRepository;
import com.infinevo.payroll.statutory.pt.OrgPtOverrideRepository;
import com.infinevo.payroll.statutory.settings.EpfSetting;
import com.infinevo.payroll.statutory.settings.EpfSettingRepository;
import com.infinevo.payroll.statutory.settings.EsiSetting;
import com.infinevo.payroll.statutory.settings.EsiSettingRepository;
import com.infinevo.shared.entitlement.EntitlementSource;
import com.infinevo.shared.entitlement.PlatformModule;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;

class PayrollSetupStepConfigurationTest {

    private final PayrollSetupStepConfiguration config = new PayrollSetupStepConfiguration();
    private final UUID tenantId = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    /** Every {@code @Bean} checker the configuration declares, each given empty providers. */
    private List<SetupStepChecker> payrollBeans() throws Exception {
        List<SetupStepChecker> checkers = new ArrayList<>();
        for (Method method : PayrollSetupStepConfiguration.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Bean.class) && SetupStepChecker.class.equals(method.getReturnType())) {
                Object[] args = new Object[method.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = provider(null);
                }
                checkers.add((SetupStepChecker) method.invoke(config, args));
            }
        }
        return checkers;
    }

    @Test
    @DisplayName("core and payroll checkers together cover every catalogue step, so the full application starts")
    void fullRegistryCoversCatalogue() throws Exception {
        List<SetupStepChecker> registry = new ArrayList<>(payrollBeans());
        registry.add(new WorkLocationSetupStepChecker(mock(WorkLocationRepository.class)));
        registry.add(new EmployeeSetupStepChecker(mock(EmployeeRepository.class)));

        assertThat(SetupStepCatalogue.registryProblems(SetupStepCatalogue.DEFAULT_STEPS, registry, true))
                .isEmpty();
        // The constructor is what fails application startup on a mismatch; it must accept this registry.
        new SetupChecklistService(mock(TenantSetupStepRepository.class), mock(EntitlementSource.class), registry);
    }

    @Test
    @DisplayName("every payroll checker belongs to PAYROLL and is incomplete when its repository is absent")
    void payrollCheckersAreIncompleteWithoutData() throws Exception {
        List<SetupStepChecker> checkers = payrollBeans();

        assertThat(checkers)
                .extracting(SetupStepChecker::code)
                .containsExactlyInAnyOrderElementsOf(SetupStepCatalogue.DEFAULT_STEPS.stream()
                        .filter(s -> s.module() == PlatformModule.PAYROLL)
                        .map(SetupStepCatalogue.StepDefinition::code)
                        .toList());
        for (SetupStepChecker checker : checkers) {
            assertThat(checker.module()).isEqualTo(PlatformModule.PAYROLL);
            assertThat(checker.isComplete(tenantId)).as(checker.code()).isFalse();
            assertThat(checker.isComplete(null)).as(checker.code()).isFalse();
        }
    }

    @Test
    @DisplayName("repository-backed checkers reflect repository data")
    void repositoryBackedCheckersDetectCompletion() {
        EpfSettingRepository epfRepo = mock(EpfSettingRepository.class);
        when(epfRepo.findByTenantId(tenantId)).thenReturn(Optional.of(new EpfSetting(tenantId)));
        assertThat(config.epfSetupStepChecker(provider(epfRepo)).isComplete(tenantId))
                .isTrue();

        EsiSettingRepository esiRepo = mock(EsiSettingRepository.class);
        when(esiRepo.findByTenantId(tenantId)).thenReturn(Optional.of(new EsiSetting(tenantId)));
        assertThat(config.esiSetupStepChecker(provider(esiRepo)).isComplete(tenantId))
                .isTrue();

        EarningRepository earningRepo = mock(EarningRepository.class);
        when(earningRepo.existsByTenantIdAndDeletedFalse(tenantId)).thenReturn(true);
        assertThat(config.salaryComponentsSetupStepChecker(provider(earningRepo), provider(null))
                        .isComplete(tenantId))
                .isTrue();

        PayScheduleRepository payScheduleRepo = mock(PayScheduleRepository.class);
        when(payScheduleRepo.findByTenantId(tenantId)).thenReturn(Optional.of(mock(PaySchedule.class)));
        assertThat(config.payScheduleSetupStepChecker(provider(payScheduleRepo)).isComplete(tenantId))
                .isTrue();
    }

    @Test
    @DisplayName("W-73.9: a step is pre-filled while the template is its rows' last writer, and not after a save")
    void prefilledWhileTheTemplateIsTheLastWriter() {
        EpfSetting templated = new EpfSetting(tenantId, TenantTemplateContributor.ACTOR);
        EpfSettingRepository epfRepo = mock(EpfSettingRepository.class);
        when(epfRepo.findByTenantId(tenantId)).thenReturn(Optional.of(templated));
        SetupStepChecker epf = config.epfSetupStepChecker(provider(epfRepo));
        assertThat(epf.isPrefilled(tenantId)).isTrue();
        templated.setUpdatedBy("hr.admin");
        assertThat(epf.isPrefilled(tenantId)).as("saved by a user").isFalse();

        EsiSettingRepository esiRepo = mock(EsiSettingRepository.class);
        when(esiRepo.findByTenantId(tenantId)).thenReturn(Optional.of(new EsiSetting(tenantId, "hr.admin")));
        assertThat(config.esiSetupStepChecker(provider(esiRepo)).isPrefilled(tenantId))
                .as("written by a user")
                .isFalse();

        PaySchedule schedule = mock(PaySchedule.class);
        when(schedule.getUpdatedBy()).thenReturn(TenantTemplateContributor.ACTOR);
        PayScheduleRepository payScheduleRepo = mock(PayScheduleRepository.class);
        when(payScheduleRepo.findByTenantId(tenantId)).thenReturn(Optional.of(schedule));
        assertThat(config.payScheduleSetupStepChecker(provider(payScheduleRepo)).isPrefilled(tenantId))
                .isTrue();

        EarningRepository earnings = mock(EarningRepository.class);
        when(earnings.existsByTenantIdAndCreatedBy(tenantId, TenantTemplateContributor.ACTOR))
                .thenReturn(true);
        DeductionRepository deductions = mock(DeductionRepository.class);
        SetupStepChecker components = config.salaryComponentsSetupStepChecker(provider(earnings), provider(deductions));
        assertThat(components.isPrefilled(tenantId)).isTrue();
        when(deductions.existsByTenantIdAndUpdatedByNot(tenantId, TenantTemplateContributor.ACTOR))
                .thenReturn(true);
        assertThat(components.isPrefilled(tenantId)).as("a deduction was saved").isFalse();
        when(deductions.existsByTenantIdAndUpdatedByNot(tenantId, TenantTemplateContributor.ACTOR))
                .thenReturn(false);
        when(earnings.existsByTenantIdAndUpdatedByNot(tenantId, TenantTemplateContributor.ACTOR))
                .thenReturn(true);
        assertThat(components.isPrefilled(tenantId)).as("an earning was saved").isFalse();

        assertThat(ptChecker(true, List.of()).isPrefilled(tenantId))
                .as("professional tax is never templated")
                .isFalse();
    }

    private static WorkLocation location(String stateCode) {
        WorkLocation wl = mock(WorkLocation.class);
        when(wl.getStateCode()).thenReturn(stateCode);
        return wl;
    }

    private SetupStepChecker ptChecker(boolean override, List<WorkLocation> activeLocations) {
        OrgPtOverrideRepository overrides = mock(OrgPtOverrideRepository.class);
        when(overrides.existsByTenantId(tenantId)).thenReturn(override);
        WorkLocationRepository locations = mock(WorkLocationRepository.class);
        when(locations.findByTenantIdAndActiveTrueOrderByCodeAsc(tenantId)).thenReturn(activeLocations);
        return config.professionalTaxSetupStepChecker(provider(overrides), provider(locations));
    }

    @Test
    @DisplayName("professional tax completes from an override alone")
    void professionalTaxCompletesFromOverride() {
        assertThat(ptChecker(true, List.of()).isComplete(tenantId)).isTrue();
    }

    @Test
    @DisplayName("professional tax completes without an override once every active work location names its state")
    void professionalTaxCompletesFromStatutorySlabsViaWorkLocations() {
        assertThat(ptChecker(false, List.of(location("KA"), location("MH"))).isComplete(tenantId))
                .isTrue();
    }

    @Test
    @DisplayName("professional tax stays open with no work location, or one with no state code")
    void professionalTaxOpenWhenStateUnknown() {
        assertThat(ptChecker(false, List.of()).isComplete(tenantId)).isFalse();
        assertThat(ptChecker(false, List.of(location("KA"), location(" "))).isComplete(tenantId))
                .isFalse();
        assertThat(ptChecker(false, List.of(location(null))).isComplete(tenantId))
                .isFalse();
    }
}
