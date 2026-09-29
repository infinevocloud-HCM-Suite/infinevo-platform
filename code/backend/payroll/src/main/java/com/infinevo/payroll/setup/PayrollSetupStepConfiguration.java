package com.infinevo.payroll.setup;

import com.infinevo.core.setup.SetupStepChecker;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration providing default {@link SetupStepChecker} beans for all Payroll module
 * setup steps (W-24.1, spec §4).
 *
 * <p>Each payroll step defined in {@code SetupStepCatalogue} must have a registered checker
 * bean so that tenants entitled to Payroll (such as Acme and Globex) can load their setup
 * checklist without crashing. Checkers return {@code false} until their respective feature
 * (e.g. W-28 for pay schedules, W-26.1 for salary components) is implemented.
 *
 * <p>Annotated with {@link ConditionalOnMissingBean} so that when feature tickets introduce
 * dedicated repository-backed checker beans, they take precedence automatically.
 */
@Configuration
public class PayrollSetupStepConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "payScheduleSetupStepChecker")
    public SetupStepChecker payScheduleSetupStepChecker() {
        return createChecker("PAY_SCHEDULE");
    }

    @Bean
    @ConditionalOnMissingBean(name = "priorPayrollSetupStepChecker")
    public SetupStepChecker priorPayrollSetupStepChecker() {
        return createChecker("PRIOR_PAYROLL");
    }

    @Bean
    @ConditionalOnMissingBean(name = "organisationTaxSetupStepChecker")
    public SetupStepChecker organisationTaxSetupStepChecker() {
        return createChecker("ORGANISATION_TAX");
    }

    @Bean
    @ConditionalOnMissingBean(name = "salaryComponentsSetupStepChecker")
    public SetupStepChecker salaryComponentsSetupStepChecker(
            org.springframework.beans.factory.ObjectProvider<com.infinevo.payroll.component.EarningRepository>
                    earningRepositoryProvider) {
        return new SetupStepChecker() {
            @Override
            public String code() {
                return "SALARY_COMPONENTS";
            }

            @Override
            public PlatformModule module() {
                return PlatformModule.PAYROLL;
            }

            @Override
            public boolean isComplete(UUID tenantId) {
                if (tenantId == null) {
                    return false;
                }
                var repo = earningRepositoryProvider != null ? earningRepositoryProvider.getIfAvailable() : null;
                return repo != null && repo.existsByTenantIdAndDeletedFalse(tenantId);
            }
        };
    }

    public SetupStepChecker salaryComponentsSetupStepChecker() {
        return salaryComponentsSetupStepChecker(null);
    }

    @Bean
    @ConditionalOnMissingBean(name = "epfSetupStepChecker")
    public SetupStepChecker epfSetupStepChecker(
            org.springframework.beans.factory.ObjectProvider<
                            com.infinevo.payroll.statutory.settings.EpfSettingRepository>
                    epfSettingRepositoryProvider) {
        return new SetupStepChecker() {
            @Override
            public String code() {
                return "EPF";
            }

            @Override
            public PlatformModule module() {
                return PlatformModule.PAYROLL;
            }

            @Override
            public boolean isComplete(UUID tenantId) {
                if (tenantId == null) {
                    return false;
                }
                var repo = epfSettingRepositoryProvider != null ? epfSettingRepositoryProvider.getIfAvailable() : null;
                return repo != null && repo.findByTenantId(tenantId).isPresent();
            }
        };
    }

    public SetupStepChecker epfSetupStepChecker() {
        return epfSetupStepChecker(null);
    }

    @Bean
    @ConditionalOnMissingBean(name = "esiSetupStepChecker")
    public SetupStepChecker esiSetupStepChecker(
            org.springframework.beans.factory.ObjectProvider<
                            com.infinevo.payroll.statutory.settings.EsiSettingRepository>
                    esiSettingRepositoryProvider) {
        return new SetupStepChecker() {
            @Override
            public String code() {
                return "ESI";
            }

            @Override
            public PlatformModule module() {
                return PlatformModule.PAYROLL;
            }

            @Override
            public boolean isComplete(UUID tenantId) {
                if (tenantId == null) {
                    return false;
                }
                var repo = esiSettingRepositoryProvider != null ? esiSettingRepositoryProvider.getIfAvailable() : null;
                return repo != null && repo.findByTenantId(tenantId).isPresent();
            }
        };
    }

    public SetupStepChecker esiSetupStepChecker() {
        return esiSetupStepChecker(null);
    }

    @Bean
    @ConditionalOnMissingBean(name = "professionalTaxSetupStepChecker")
    public SetupStepChecker professionalTaxSetupStepChecker(
            org.springframework.beans.factory.ObjectProvider<com.infinevo.payroll.statutory.pt.OrgPtOverrideRepository>
                    orgPtOverrideRepositoryProvider) {
        return new SetupStepChecker() {
            @Override
            public String code() {
                return "PROFESSIONAL_TAX";
            }

            @Override
            public PlatformModule module() {
                return PlatformModule.PAYROLL;
            }

            @Override
            public boolean isComplete(UUID tenantId) {
                if (tenantId == null) {
                    return false;
                }
                var repo = orgPtOverrideRepositoryProvider != null
                        ? orgPtOverrideRepositoryProvider.getIfAvailable()
                        : null;
                return repo != null && repo.existsByTenantId(tenantId);
            }
        };
    }

    public SetupStepChecker professionalTaxSetupStepChecker() {
        return professionalTaxSetupStepChecker(null);
    }

    private static SetupStepChecker createChecker(String code) {
        Objects.requireNonNull(code, "code must not be null");
        return new SetupStepChecker() {
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
                return false;
            }
        };
    }
}
