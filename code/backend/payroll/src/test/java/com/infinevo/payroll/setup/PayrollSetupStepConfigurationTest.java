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
}
