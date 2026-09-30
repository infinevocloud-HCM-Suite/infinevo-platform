package com.infinevo.core.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.entitlement.EntitlementSource;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SetupStepCheckerRegistryTest {

    private TenantSetupStepRepository repository;
    private EntitlementSource entitlementSource;
    private final Map<String, TenantSetupStep> database = new HashMap<>();

    @BeforeEach
    void setUp() {
        database.clear();
        repository = mock(TenantSetupStepRepository.class);
        entitlementSource = mock(EntitlementSource.class);

        when(repository.save(any(TenantSetupStep.class))).thenAnswer(invocation -> {
            TenantSetupStep s = invocation.getArgument(0);
            database.put(s.getTenantId() + ":" + s.getStepCode().toUpperCase(), s);
            return s;
        });

        when(repository.findByTenantIdOrderByDisplayOrderAsc(any(UUID.class))).thenAnswer(invocation -> {
            UUID tid = invocation.getArgument(0);
            List<TenantSetupStep> list = new ArrayList<>();
            for (TenantSetupStep s : database.values()) {
                if (s.getTenantId().equals(tid)) {
                    list.add(s);
                }
            }
            list.sort((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()));
            return list;
        });

        when(repository.findByTenantIdAndStepCode(any(UUID.class), any(String.class)))
                .thenAnswer(invocation -> {
                    UUID tid = invocation.getArgument(0);
                    String code = invocation.getArgument(1);
                    return Optional.ofNullable(database.get(tid + ":" + code.toUpperCase()));
                });
    }

    @Test
    @DisplayName("a checker with module() = PAYROLL is not invoked for an HRMS-only tenant")
    void payrollCheckerNotInvokedForHrmsTenant() {
        UUID hrmsTenant = UUID.randomUUID();
        when(entitlementSource.modulesOf(hrmsTenant)).thenReturn(Set.of(PlatformModule.HRMS));

        SetupStepChecker coreChecker1 = mock(SetupStepChecker.class);
        when(coreChecker1.code()).thenReturn("WORK_LOCATION");
        when(coreChecker1.module()).thenReturn(null);
        when(coreChecker1.isComplete(hrmsTenant)).thenReturn(true);

        SetupStepChecker coreChecker2 = mock(SetupStepChecker.class);
        when(coreChecker2.code()).thenReturn("EMPLOYEE");
        when(coreChecker2.module()).thenReturn(null);
        when(coreChecker2.isComplete(hrmsTenant)).thenReturn(false);

        List<SetupStepChecker> payrollCheckers = payrollMocks();
        List<SetupStepChecker> all = new ArrayList<>(List.of(coreChecker1, coreChecker2));
        all.addAll(payrollCheckers);

        SetupChecklistService service = new SetupChecklistService(repository, entitlementSource, all);

        SetupChecklistResponse response = service.getChecklist(hrmsTenant);

        assertThat(response.steps()).hasSize(2);
        for (SetupStepChecker payrollChecker : payrollCheckers) {
            verify(payrollChecker, never()).isComplete(any());
        }
    }

    private static List<SetupStepChecker> payrollMocks() {
        List<SetupStepChecker> checkers = new ArrayList<>();
        for (SetupStepCatalogue.StepDefinition def : SetupStepCatalogue.DEFAULT_STEPS) {
            if (def.module() == PlatformModule.PAYROLL) {
                checkers.add(checker(def.code(), PlatformModule.PAYROLL));
            }
        }
        return checkers;
    }

    private static SetupStepChecker checker(String code, PlatformModule module) {
        SetupStepChecker checker = mock(SetupStepChecker.class);
        when(checker.code()).thenReturn(code);
        when(checker.module()).thenReturn(module);
        return checker;
    }

    @Test
    @DisplayName("a core catalogue step with no registered checker fails at construction, before any read")
    void missingCheckerFailsFast() {
        // Only provide WORK_LOCATION checker, omitting EMPLOYEE
        List<SetupStepChecker> registry = List.of(checker("WORK_LOCATION", null));

        assertThatThrownBy(() -> new SetupChecklistService(repository, entitlementSource, registry))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No SetupStepChecker registered for step: EMPLOYEE");
        verify(repository, never()).findByTenantIdOrderByDisplayOrderAsc(any());
    }

    @Test
    @DisplayName("a module that registers some checkers but not all of its steps fails at construction")
    void partialModuleRegistryFailsFast() {
        List<SetupStepChecker> registry =
                new ArrayList<>(List.of(checker("WORK_LOCATION", null), checker("EMPLOYEE", null)));
        registry.addAll(payrollMocks());
        registry.removeIf(c -> c.code().equals("EPF"));

        assertThatThrownBy(() -> new SetupChecklistService(repository, entitlementSource, registry))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No SetupStepChecker registered for step: EPF");
    }

    @Test
    @DisplayName(
            "a context that deploys no payroll checker at all starts; the full registry is required in strict mode")
    void undeployedModuleIsAllowedOnlyOutsideStrictMode() {
        List<SetupStepChecker> coreOnly = List.of(checker("WORK_LOCATION", null), checker("EMPLOYEE", null));

        assertThat(SetupStepCatalogue.registryProblems(SetupStepCatalogue.DEFAULT_STEPS, coreOnly, false))
                .isEmpty();
        assertThat(SetupStepCatalogue.registryProblems(SetupStepCatalogue.DEFAULT_STEPS, coreOnly, true))
                .contains("No SetupStepChecker registered for step: PAY_SCHEDULE");
    }

    @Test
    @DisplayName("two checkers for one step, or a checker in the wrong module, fail at construction")
    void duplicateOrMismatchedCheckerFailsFast() {
        List<SetupStepChecker> duplicate =
                List.of(checker("WORK_LOCATION", null), checker("EMPLOYEE", null), checker("employee", null));
        assertThatThrownBy(() -> new SetupChecklistService(repository, entitlementSource, duplicate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("More than one SetupStepChecker registered for step: EMPLOYEE");

        List<SetupStepChecker> mismatched =
                List.of(checker("WORK_LOCATION", PlatformModule.HRMS), checker("EMPLOYEE", null));
        assertThatThrownBy(() -> new SetupChecklistService(repository, entitlementSource, mismatched))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WORK_LOCATION declares module HRMS");
    }
}
