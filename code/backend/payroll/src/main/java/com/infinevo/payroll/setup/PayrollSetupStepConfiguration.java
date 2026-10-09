package com.infinevo.payroll.setup;

import com.infinevo.core.org.WorkLocation;
import com.infinevo.core.org.WorkLocationRepository;
import com.infinevo.core.setup.SetupStepChecker;
import com.infinevo.core.template.TenantTemplateContributor;
import com.infinevo.payroll.component.DeductionRepository;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.priorpayroll.PriorPayrollMonthRepository;
import com.infinevo.payroll.schedule.PayScheduleRepository;
import com.infinevo.payroll.statutory.pt.OrgPtOverrideRepository;
import com.infinevo.payroll.statutory.settings.EpfSettingRepository;
import com.infinevo.payroll.statutory.settings.EsiSettingRepository;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration providing the {@link SetupStepChecker} beans for every Payroll step in
 * {@code SetupStepCatalogue} (W-24.1, spec §4).
 *
 * <p>Every checker is an existence query against the table its feature owns, so completion is
 * detected, never declared. Repositories arrive through {@link ObjectProvider} so a context that
 * does not scan a feature's repository still starts; the checker then reports incomplete.
 *
 * <p>Annotated with {@link ConditionalOnMissingBean} so that a feature ticket introducing a
 * dedicated checker bean of the same name takes precedence automatically.
 *
 * <p>Organisation tax ({@code W-36.3}) has no checker because it has no catalogue step yet — the
 * feature has no table on this platform. See {@code SetupStepCatalogue}.
 */
@Configuration
public class PayrollSetupStepConfiguration {

    /** The last writer of a row a country template wrote and nobody has saved since (W-73.9). */
    private static final String TEMPLATE = TenantTemplateContributor.ACTOR;

    @Bean
    @ConditionalOnMissingBean(name = "payScheduleSetupStepChecker")
    public SetupStepChecker payScheduleSetupStepChecker(ObjectProvider<PayScheduleRepository> repositoryProvider) {
        return checker(
                "PAY_SCHEDULE",
                repositoryProvider,
                (repo, tenantId) -> repo.findByTenantId(tenantId).isPresent(),
                (repo, tenantId) -> repo.findByTenantId(tenantId)
                        .filter(s -> TEMPLATE.equals(s.getUpdatedBy()))
                        .isPresent());
    }

    /**
     * Prior payroll (W-38.3 §3, §13 decision 1) is settled once any month is imported, or when the
     * tenant's first regular run that is not cancelled is for April: a tenant that started payroll at
     * the start of the financial year has no earlier months to load. Not "any run exists" — that was
     * the frozen rule ({@code OrgSetupStepsServiceImpl.java:75}) and it means "has run payroll".
     */
    @Bean
    @ConditionalOnMissingBean(name = "priorPayrollSetupStepChecker")
    public SetupStepChecker priorPayrollSetupStepChecker(
            ObjectProvider<PriorPayrollMonthRepository> monthRepositoryProvider,
            ObjectProvider<PayRunRepository> payRunRepositoryProvider) {
        Objects.requireNonNull(monthRepositoryProvider, "monthRepositoryProvider must not be null");
        Objects.requireNonNull(payRunRepositoryProvider, "payRunRepositoryProvider must not be null");
        return new PayrollChecker("PRIOR_PAYROLL") {
            @Override
            boolean check(UUID tenantId) {
                PriorPayrollMonthRepository months = monthRepositoryProvider.getIfAvailable();
                if (months != null && months.existsByTenantId(tenantId)) {
                    return true;
                }
                PayRunRepository runs = payRunRepositoryProvider.getIfAvailable();
                if (runs == null) {
                    return false;
                }
                return runs.findFirstByTenantIdAndRunTypeAndStatusNotOrderByPeriodAsc(
                                tenantId, PayRunType.REGULAR, PayRunStatus.CANCELLED)
                        .map(PayRun::getPeriod)
                        .filter(period -> period != null && period.getMonthValue() == 4)
                        .isPresent();
            }
        };
    }

    /**
     * Pre-filled (W-73.9) while the country template wrote the tenant's earnings and nobody has since created,
     * changed or deleted an earning or a deduction - every one still has the template as its last writer.
     */
    @Bean
    @ConditionalOnMissingBean(name = "salaryComponentsSetupStepChecker")
    public SetupStepChecker salaryComponentsSetupStepChecker(
            ObjectProvider<EarningRepository> repositoryProvider,
            ObjectProvider<DeductionRepository> deductionRepositoryProvider) {
        Objects.requireNonNull(deductionRepositoryProvider, "deductionRepositoryProvider must not be null");
        return checker(
                "SALARY_COMPONENTS",
                repositoryProvider,
                EarningRepository::existsByTenantIdAndDeletedFalse,
                (repo, tenantId) -> {
                    if (!repo.existsByTenantIdAndCreatedBy(tenantId, TEMPLATE)
                            || repo.existsByTenantIdAndUpdatedByNot(tenantId, TEMPLATE)) {
                        return false;
                    }
                    DeductionRepository deductions = deductionRepositoryProvider.getIfAvailable();
                    return deductions == null || !deductions.existsByTenantIdAndUpdatedByNot(tenantId, TEMPLATE);
                });
    }

    @Bean
    @ConditionalOnMissingBean(name = "epfSetupStepChecker")
    public SetupStepChecker epfSetupStepChecker(ObjectProvider<EpfSettingRepository> repositoryProvider) {
        return checker(
                "EPF",
                repositoryProvider,
                (repo, tenantId) -> repo.findByTenantId(tenantId).isPresent(),
                (repo, tenantId) -> repo.findByTenantId(tenantId)
                        .filter(s -> TEMPLATE.equals(s.getUpdatedBy()))
                        .isPresent());
    }

    @Bean
    @ConditionalOnMissingBean(name = "esiSetupStepChecker")
    public SetupStepChecker esiSetupStepChecker(ObjectProvider<EsiSettingRepository> repositoryProvider) {
        return checker(
                "ESI",
                repositoryProvider,
                (repo, tenantId) -> repo.findByTenantId(tenantId).isPresent(),
                (repo, tenantId) -> repo.findByTenantId(tenantId)
                        .filter(s -> TEMPLATE.equals(s.getUpdatedBy()))
                        .isPresent());
    }

    /**
     * Professional tax is settled when the tenant has overridden a state's slabs
     * ({@code payroll.org_pt_override}, W-31.2), or when every active work location names its state
     * code. PT is levied by the state an employee works in, and a location with a state code
     * resolves to that state's statutory slabs from {@code reference.pt_slab} — or to no PT, for a
     * state that levies none ({@code PtSource.REFERENCE} / {@code NONE},
     * {@code ProfessionalTaxServiceImpl.java:233-266}); the PT screen lists states from the same
     * active work locations ({@code ProfessionalTaxServiceImpl.java:268-279}). There is no other
     * per-tenant PT table on this platform. A location with no state code leaves PT
     * undeterminable, so the step stays open.
     */
    @Bean
    @ConditionalOnMissingBean(name = "professionalTaxSetupStepChecker")
    public SetupStepChecker professionalTaxSetupStepChecker(
            ObjectProvider<OrgPtOverrideRepository> overrideRepositoryProvider,
            ObjectProvider<WorkLocationRepository> workLocationRepositoryProvider) {
        Objects.requireNonNull(overrideRepositoryProvider, "overrideRepositoryProvider must not be null");
        Objects.requireNonNull(workLocationRepositoryProvider, "workLocationRepositoryProvider must not be null");
        return new PayrollChecker("PROFESSIONAL_TAX") {
            @Override
            boolean check(UUID tenantId) {
                OrgPtOverrideRepository overrides = overrideRepositoryProvider.getIfAvailable();
                if (overrides != null && overrides.existsByTenantId(tenantId)) {
                    return true;
                }
                WorkLocationRepository locations = workLocationRepositoryProvider.getIfAvailable();
                if (locations == null) {
                    return false;
                }
                List<WorkLocation> active = locations.findByTenantIdAndActiveTrueOrderByCodeAsc(tenantId);
                Predicate<WorkLocation> hasStateCode =
                        wl -> wl.getStateCode() != null && !wl.getStateCode().isBlank();
                return !active.isEmpty() && active.stream().allMatch(hasStateCode);
            }
        };
    }

    private static <R> SetupStepChecker checker(
            String code,
            ObjectProvider<R> repositoryProvider,
            BiPredicate<R, UUID> exists,
            BiPredicate<R, UUID> prefilled) {
        Objects.requireNonNull(repositoryProvider, "repositoryProvider must not be null");
        return new PayrollChecker(code) {
            @Override
            boolean check(UUID tenantId) {
                R repo = repositoryProvider.getIfAvailable();
                return repo != null && exists.test(repo, tenantId);
            }

            @Override
            boolean prefilled(UUID tenantId) {
                R repo = repositoryProvider.getIfAvailable();
                return repo != null && prefilled.test(repo, tenantId);
            }
        };
    }

    /** A Payroll-module checker: fixed code, {@code PAYROLL} module, no answer without a tenant. */
    private abstract static class PayrollChecker implements SetupStepChecker {

        private final String code;

        PayrollChecker(String code) {
            this.code = code;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public PlatformModule module() {
            return PlatformModule.PAYROLL;
        }

        @Override
        public boolean isComplete(UUID tenantId) {
            return tenantId != null && check(tenantId);
        }

        @Override
        public boolean isPrefilled(UUID tenantId) {
            return tenantId != null && prefilled(tenantId);
        }

        abstract boolean check(UUID tenantId);

        /** W-73.9: the step's rows were written by a country template and nobody has saved them since. */
        boolean prefilled(UUID tenantId) {
            return false;
        }
    }
}
