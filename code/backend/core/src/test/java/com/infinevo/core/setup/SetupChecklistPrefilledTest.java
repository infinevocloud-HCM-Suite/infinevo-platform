package com.infinevo.core.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.entitlement.EntitlementSource;
import com.infinevo.shared.entitlement.PlatformModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-73.9: a step whose data a country template wrote reads "pre-filled" until someone saves it, and counts as
 * completed all along.
 */
class SetupChecklistPrefilledTest {

    private final UUID tenantId = UUID.randomUUID();
    private final Map<String, TenantSetupStep> rows = new HashMap<>();
    private final Map<String, AtomicBoolean> complete = new HashMap<>();
    private final Map<String, AtomicBoolean> prefilled = new HashMap<>();
    private SetupChecklistService service;

    @BeforeEach
    void setUp() {
        TenantSetupStepRepository repository = mock(TenantSetupStepRepository.class);
        when(repository.save(any(TenantSetupStep.class))).thenAnswer(inv -> {
            TenantSetupStep s = inv.getArgument(0);
            rows.put(s.getStepCode(), s);
            return s;
        });
        when(repository.findByTenantIdOrderByDisplayOrderAsc(any(UUID.class))).thenAnswer(inv -> {
            List<TenantSetupStep> list = new ArrayList<>(rows.values());
            list.sort((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()));
            return list;
        });
        when(repository.findByTenantIdAndStepCode(any(UUID.class), any(String.class)))
                .thenAnswer(inv -> Optional.ofNullable(rows.get(((String) inv.getArgument(1)).toUpperCase())));
        EntitlementSource entitlements = mock(EntitlementSource.class);
        when(entitlements.modulesOf(tenantId)).thenReturn(Set.of(PlatformModule.PAYROLL));

        List<SetupStepChecker> checkers = new ArrayList<>();
        for (SetupStepCatalogue.StepDefinition def : SetupStepCatalogue.DEFAULT_STEPS) {
            checkers.add(checker(def.code(), def.module()));
        }
        service = new SetupChecklistService(
                repository,
                entitlements,
                checkers,
                SetupStepCatalogue.DEFAULT_STEPS,
                Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC));
    }

    private SetupStepChecker checker(String code, PlatformModule module) {
        AtomicBoolean done = complete.computeIfAbsent(code, k -> new AtomicBoolean());
        AtomicBoolean byTemplate = prefilled.computeIfAbsent(code, k -> new AtomicBoolean());
        return new SetupStepChecker() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public PlatformModule module() {
                return module;
            }

            @Override
            public boolean isComplete(UUID id) {
                return done.get();
            }

            @Override
            public boolean isPrefilled(UUID id) {
                return byTemplate.get();
            }
        };
    }

    private void templateWrote(String code) {
        complete.get(code).set(true);
        prefilled.get(code).set(true);
    }

    private SetupStepResponse step(SetupChecklistResponse checklist, String code) {
        return checklist.steps().stream()
                .filter(s -> s.code().equals(code))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("the four templated payroll steps read pre-filled and completed; the other four stay to do")
    void templatedStepsArePrefilled() {
        for (String code : List.of("PAY_SCHEDULE", "SALARY_COMPONENTS", "EPF", "ESI")) {
            templateWrote(code);
        }

        SetupChecklistResponse checklist = service.getChecklist(tenantId);

        assertThat(checklist.steps())
                .filteredOn(SetupStepResponse::prefilled)
                .extracting(SetupStepResponse::code)
                .containsExactlyInAnyOrder("PAY_SCHEDULE", "SALARY_COMPONENTS", "EPF", "ESI");
        assertThat(checklist.steps()).filteredOn(SetupStepResponse::prefilled).allMatch(SetupStepResponse::completed);
        assertThat(checklist.completedCount()).isEqualTo(4);
        assertThat(checklist.steps())
                .filteredOn(s -> !s.completed())
                .extracting(SetupStepResponse::code)
                .containsExactlyInAnyOrder("WORK_LOCATION", "EMPLOYEE", "PRIOR_PAYROLL", "PROFESSIONAL_TAX");
    }

    @Test
    @DisplayName("a pre-filled step stops being pre-filled once it is saved, and stays completed")
    void savedStepIsNoLongerPrefilled() {
        templateWrote("EPF");
        assertThat(step(service.getChecklist(tenantId), "EPF").prefilled()).isTrue();

        prefilled.get("EPF").set(false);

        SetupStepResponse epf = step(service.getChecklist(tenantId), "EPF");
        assertThat(epf.prefilled()).isFalse();
        assertThat(epf.completed()).isTrue();
    }

    @Test
    @DisplayName("a checker that says pre-filled for an incomplete or skipped step is not believed")
    void onlyCompleteUnskippedStepsArePrefilled() {
        prefilled.get("ESI").set(true);
        assertThat(step(service.getChecklist(tenantId), "ESI").prefilled()).isFalse();

        templateWrote("ESI");
        service.skipStep(tenantId, "ESI", "Not registered");
        assertThat(step(service.getChecklist(tenantId), "ESI").prefilled()).isFalse();
    }
}
