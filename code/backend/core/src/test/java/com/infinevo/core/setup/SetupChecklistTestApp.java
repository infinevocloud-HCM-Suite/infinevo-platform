package com.infinevo.core.setup;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spring Boot application context for setup checklist integration tests (W-24.1).
 */
@SpringBootApplication(
        scanBasePackages = {
            "com.infinevo.core.setup",
            "com.infinevo.core.subscription",
            "com.infinevo.core.tenant",
            "com.infinevo.core.org",
            "com.infinevo.core.employee",
            "com.infinevo.shared.authz"
        })
@EntityScan(
        basePackages = {
            "com.infinevo.core.setup",
            "com.infinevo.core.subscription",
            "com.infinevo.core.org",
            "com.infinevo.core.employee"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.core.setup",
            "com.infinevo.core.subscription",
            "com.infinevo.core.org",
            "com.infinevo.core.employee"
        })
public class SetupChecklistTestApp {

    @Bean
    SetupStepChecker payScheduleChecker() {
        return stubChecker("PAY_SCHEDULE");
    }

    @Bean
    SetupStepChecker priorPayrollChecker() {
        return stubChecker("PRIOR_PAYROLL");
    }

    @Bean
    SetupStepChecker orgTaxChecker() {
        return stubChecker("ORGANISATION_TAX");
    }

    @Bean
    SetupStepChecker salaryComponentsChecker() {
        return stubChecker("SALARY_COMPONENTS");
    }

    @Bean
    SetupStepChecker epfChecker() {
        return stubChecker("EPF");
    }

    @Bean
    SetupStepChecker esiChecker() {
        return stubChecker("ESI");
    }

    @Bean
    SetupStepChecker ptaxChecker() {
        return stubChecker("PROFESSIONAL_TAX");
    }

    private SetupStepChecker stubChecker(String code) {
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
