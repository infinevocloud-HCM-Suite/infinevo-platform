package com.infinevo.core.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.entitlement.EntitlementSource;
import com.infinevo.shared.entitlement.PlatformModule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SetupChecklistServiceTest {

    private TenantSetupStepRepository repository;
    private EntitlementSource entitlementSource;
    private final Map<String, TenantSetupStep> database = new HashMap<>();

    private SetupStepChecker workLocationChecker;
    private SetupStepChecker employeeChecker;
    private SetupStepChecker payScheduleChecker;
    private SetupStepChecker salaryComponentsChecker;
    private SetupStepChecker epfChecker;
    private SetupStepChecker esiChecker;
    private SetupStepChecker ptaxChecker;

    private SetupChecklistService service;

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

        workLocationChecker = createChecker("WORK_LOCATION", null, false);
        employeeChecker = createChecker("EMPLOYEE", null, false);
        payScheduleChecker = createChecker("PAY_SCHEDULE", PlatformModule.PAYROLL, false);
        salaryComponentsChecker = createChecker("SALARY_COMPONENTS", PlatformModule.PAYROLL, false);
        epfChecker = createChecker("EPF", PlatformModule.PAYROLL, false);
        esiChecker = createChecker("ESI", PlatformModule.PAYROLL, false);
        ptaxChecker = createChecker("PROFESSIONAL_TAX", PlatformModule.PAYROLL, false);

        List<SetupStepChecker> checkers = List.of(
                workLocationChecker,
                employeeChecker,
                payScheduleChecker,
                salaryComponentsChecker,
                epfChecker,
                esiChecker,
                ptaxChecker);

        service = new SetupChecklistService(repository, entitlementSource, checkers);
    }

    private SetupStepChecker createChecker(String code, PlatformModule module, boolean complete) {
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
            public boolean isComplete(UUID tenantId) {
                return complete;
            }
        };
    }

    @Test
    @DisplayName("an HRMS-only tenant gets no payroll step")
    void hrmsOnlyTenantGetsNoPayrollStep() {
        UUID hrmsTenant = UUID.randomUUID();
        when(entitlementSource.modulesOf(hrmsTenant)).thenReturn(Set.of(PlatformModule.HRMS));

        SetupChecklistResponse response = service.getChecklist(hrmsTenant);

        assertThat(response.steps()).hasSize(2);
        assertThat(response.steps()).extracting(SetupStepResponse::code).containsExactly("WORK_LOCATION", "EMPLOYEE");
        assertThat(response.steps()).noneMatch(s -> PlatformModule.PAYROLL.equals(s.module()));
        assertThat(response.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a tenant holding both modules gets every catalogue step")
    void bothModulesTenantGetsAllSteps() {
        UUID fullTenant = UUID.randomUUID();
        when(entitlementSource.modulesOf(fullTenant)).thenReturn(Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL));

        SetupChecklistResponse response = service.getChecklist(fullTenant);

        assertThat(response.steps()).hasSize(SetupStepCatalogue.DEFAULT_STEPS.size());
        assertThat(response.totalCount()).isEqualTo(SetupStepCatalogue.DEFAULT_STEPS.size());
        assertThat(response.steps()).extracting(SetupStepResponse::code).contains("WORK_LOCATION", "EMPLOYEE", "EPF");
    }

    @Test
    @DisplayName("progress counts only applicable steps and reflects completion and skips")
    void progressCountsApplicableSteps() {
        UUID tenantId = UUID.randomUUID();
        when(entitlementSource.modulesOf(tenantId)).thenReturn(Set.of(PlatformModule.HRMS));

        SetupChecklistResponse response0 = service.getChecklist(tenantId);
        assertThat(response0.progressPercentage()).isEqualTo(0.0);
        assertThat(response0.completedCount()).isEqualTo(0);

        // Complete WORK_LOCATION
        TenantSetupStep wlStep = database.get(tenantId + ":WORK_LOCATION");
        wlStep.setCompletedAt(Instant.now());

        // Re-read with a checker returning complete
        List<SetupStepChecker> updatedCheckers =
                List.of(createChecker("WORK_LOCATION", null, true), createChecker("EMPLOYEE", null, false));
        SetupChecklistService updatedService =
                new SetupChecklistService(repository, entitlementSource, updatedCheckers);

        SetupChecklistResponse response1 = updatedService.getChecklist(tenantId);
        assertThat(response1.progressPercentage()).isEqualTo(50.0);
        assertThat(response1.completedCount()).isEqualTo(1);

        // Skip EMPLOYEE step
        updatedService.skipStep(tenantId, "EMPLOYEE", "Exempt from initial employee setup");
        SetupChecklistResponse response2 = updatedService.getChecklist(tenantId);

        assertThat(response2.progressPercentage()).isEqualTo(100.0);
        assertThat(response2.completedCount()).isEqualTo(1);
        assertThat(response2.skippedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("skipping step requires a non-blank reason and updates state")
    void skipStepRequiresReason() {
        UUID tenantId = UUID.randomUUID();
        when(entitlementSource.modulesOf(tenantId)).thenReturn(Set.of(PlatformModule.HRMS));

        assertThatThrownBy(() -> service.skipStep(tenantId, "WORK_LOCATION", "   "))
                .isInstanceOf(IllegalArgumentException.class);

        SetupStepResponse skipped = service.skipStep(tenantId, "WORK_LOCATION", "Single virtual office");
        assertThat(skipped.skipped()).isTrue();
        assertThat(skipped.skipReason()).isEqualTo("Single virtual office");
    }

    @Test
    @DisplayName("skipping an unknown step throws SetupStepNotFoundException")
    void skipUnknownStepThrows() {
        UUID tenantId = UUID.randomUUID();
        when(entitlementSource.modulesOf(tenantId)).thenReturn(Set.of(PlatformModule.HRMS));

        assertThatThrownBy(() -> service.skipStep(tenantId, "NON_EXISTENT_STEP", "Some reason"))
                .isInstanceOf(SetupChecklistService.SetupStepNotFoundException.class);
    }

    @Test
    @DisplayName("skipping a step not applicable to tenant throws SetupStepNotFoundException")
    void skipInapplicableStepThrows() {
        UUID tenantId = UUID.randomUUID();
        when(entitlementSource.modulesOf(tenantId)).thenReturn(Set.of(PlatformModule.HRMS));

        TenantSetupStep payStep =
                new TenantSetupStep(tenantId, "PAY_SCHEDULE", PlatformModule.PAYROLL, 3, Instant.now());
        database.put(tenantId + ":PAY_SCHEDULE", payStep);

        assertThatThrownBy(() -> service.skipStep(tenantId, "PAY_SCHEDULE", "Not using payroll"))
                .isInstanceOf(SetupChecklistService.SetupStepNotFoundException.class);
    }

    // --- Spec section 13 decision 1: a step the catalogue gains later ---------------------------

    private static final SetupStepCatalogue.StepDefinition LATER_STEP =
            new SetupStepCatalogue.StepDefinition("LATER_CORE_STEP", "Added in a later release", null, 10);

    private static List<SetupStepCatalogue.StepDefinition> coreSteps() {
        return SetupStepCatalogue.DEFAULT_STEPS.stream()
                .filter(d -> d.module() == null)
                .toList();
    }

    private static List<SetupStepCatalogue.StepDefinition> coreStepsPlusLater() {
        return Stream.concat(coreSteps().stream(), Stream.of(LATER_STEP)).toList();
    }

    private SetupStepChecker switchable(String code, PlatformModule module, AtomicBoolean complete) {
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
            public boolean isComplete(UUID tenantId) {
                return complete.get();
            }
        };
    }

    @Test
    @DisplayName("a tenant at 100% stays at 100% when the catalogue gains a step, and the new step is listed")
    void stepAddedToCatalogueLaterDoesNotDropProgress() {
        UUID tenantId = UUID.randomUUID();
        when(entitlementSource.modulesOf(tenantId)).thenReturn(Set.of(PlatformModule.HRMS));
        MutableClock clock = new MutableClock(Instant.parse("2026-09-01T00:00:00Z"));
        AtomicBoolean done = new AtomicBoolean(true);
        List<SetupStepChecker> coreCheckers =
                List.of(switchable("WORK_LOCATION", null, done), switchable("EMPLOYEE", null, done));

        SetupChecklistResponse before = new SetupChecklistService(
                        repository, entitlementSource, coreCheckers, coreSteps(), clock)
                .getChecklist(tenantId);
        assertThat(before.progressPercentage()).isEqualTo(100.0);

        // A release adds a step; the tenant's next read is a day later.
        clock.advance(Duration.ofDays(1));
        List<SetupStepChecker> withLater = Stream.concat(
                        coreCheckers.stream(), Stream.of(createChecker(LATER_STEP.code(), null, false)))
                .toList();
        SetupChecklistResponse after = new SetupChecklistService(
                        repository, entitlementSource, withLater, coreStepsPlusLater(), clock)
                .getChecklist(tenantId);

        assertThat(after.progressPercentage()).isEqualTo(100.0);
        assertThat(after.totalCount()).isEqualTo(3);
        assertThat(after.newCount()).isEqualTo(1);
        SetupStepResponse later = after.steps().stream()
                .filter(s -> s.code().equals(LATER_STEP.code()))
                .findFirst()
                .orElseThrow();
        assertThat(later.completed()).isFalse();
        assertThat(later.newStep()).isTrue();
        assertThat(later.firstSeenAt()).isEqualTo(clock.instant());
        assertThat(after.steps())
                .filteredOn(s -> !s.code().equals(LATER_STEP.code()))
                .noneMatch(SetupStepResponse::newStep);
    }

    @Test
    @DisplayName("a step added later counts toward progress once the tenant next touches setup")
    void stepAddedLaterCountsAfterNextTouch() {
        UUID tenantId = UUID.randomUUID();
        when(entitlementSource.modulesOf(tenantId)).thenReturn(Set.of(PlatformModule.HRMS));
        MutableClock clock = new MutableClock(Instant.parse("2026-09-01T00:00:00Z"));
        AtomicBoolean employeeDone = new AtomicBoolean(false);
        List<SetupStepChecker> checkers = List.of(
                createChecker("WORK_LOCATION", null, true),
                switchable("EMPLOYEE", null, employeeDone),
                createChecker(LATER_STEP.code(), null, false));

        new SetupChecklistService(repository, entitlementSource, checkers, coreSteps(), clock).getChecklist(tenantId);

        clock.advance(Duration.ofDays(1));
        SetupChecklistService released =
                new SetupChecklistService(repository, entitlementSource, checkers, coreStepsPlusLater(), clock);
        SetupChecklistResponse untouched = released.getChecklist(tenantId);
        assertThat(untouched.progressPercentage()).isEqualTo(50.0);
        assertThat(untouched.newCount()).isEqualTo(1);

        // The tenant adds its first employee: a touch, observed as a completion after the new step appeared.
        clock.advance(Duration.ofHours(1));
        employeeDone.set(true);
        SetupChecklistResponse touched = released.getChecklist(tenantId);
        assertThat(touched.newCount()).isZero();
        assertThat(touched.progressPercentage()).isEqualTo(66.7);
    }

    @Test
    @DisplayName("steps added by a module upgrade count toward progress at once; they are not catalogue additions")
    void moduleUpgradeStepsAreNotNew() {
        UUID tenantId = UUID.randomUUID();
        when(entitlementSource.modulesOf(tenantId)).thenReturn(Set.of(PlatformModule.HRMS));
        MutableClock clock = new MutableClock(Instant.parse("2026-09-01T00:00:00Z"));
        SetupChecklistService clocked = new SetupChecklistService(
                repository,
                entitlementSource,
                List.of(
                        createChecker("WORK_LOCATION", null, true),
                        createChecker("EMPLOYEE", null, true),
                        payScheduleChecker,
                        salaryComponentsChecker,
                        epfChecker,
                        esiChecker,
                        ptaxChecker),
                SetupStepCatalogue.DEFAULT_STEPS,
                clock);
        assertThat(clocked.getChecklist(tenantId).progressPercentage()).isEqualTo(100.0);

        clock.advance(Duration.ofDays(1));
        when(entitlementSource.modulesOf(tenantId)).thenReturn(Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL));
        SetupChecklistResponse upgraded = clocked.getChecklist(tenantId);

        assertThat(upgraded.newCount()).isZero();
        assertThat(upgraded.totalCount()).isEqualTo(SetupStepCatalogue.DEFAULT_STEPS.size());
        assertThat(upgraded.progressPercentage()).isEqualTo(Math.round(2.0 / 7.0 * 1000.0) / 10.0);
    }

    @Test
    @DisplayName("a row whose step has left the catalogue is not listed and does not fail the read")
    void rowForRetiredStepIsNotListed() {
        UUID tenantId = UUID.randomUUID();
        when(entitlementSource.modulesOf(tenantId)).thenReturn(Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL));
        database.put(
                tenantId + ":PRIOR_PAYROLL",
                new TenantSetupStep(tenantId, "PRIOR_PAYROLL", PlatformModule.PAYROLL, 4, Instant.now()));

        SetupChecklistResponse response = service.getChecklist(tenantId);

        assertThat(response.steps()).extracting(SetupStepResponse::code).doesNotContain("PRIOR_PAYROLL");
        assertThatThrownBy(() -> service.skipStep(tenantId, "PRIOR_PAYROLL", "Gone"))
                .isInstanceOf(SetupChecklistService.SetupStepNotFoundException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
