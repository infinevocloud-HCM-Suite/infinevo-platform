package com.infinevo.core.setup;

import com.infinevo.shared.entitlement.EntitlementSource;
import com.infinevo.shared.entitlement.PlatformModule;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing tenant setup checklist assembly, step evaluation, and progress (W-24.1).
 *
 * <p><strong>Steps the catalogue gains later (spec §13 decision 1).</strong> Each row stores
 * {@code first_seen_at}, the moment it was assembled for the tenant. Rows assembled together share
 * one instant, so within a module group — core ({@code null}) or one {@link PlatformModule} — the
 * earliest {@code first_seen_at} is when the tenant took that group on, at provisioning or at a
 * module upgrade. A row whose {@code first_seen_at} is later than its group's earliest was added
 * by a catalogue change, not by anything the tenant chose. Such a row is <em>new</em> — listed,
 * incomplete, and outside the progress fraction — until the tenant next touches setup: resolves a
 * step (a {@code completed_at} or a skip recorded after the new row appeared) or resolves the new
 * step itself. A module upgrade starts a new group, so its steps count at once.
 */
@Service
public class SetupChecklistService {

    private final TenantSetupStepRepository repository;
    private final EntitlementSource entitlementSource;
    private final Map<String, SetupStepChecker> checkersByCode;
    private final List<SetupStepCatalogue.StepDefinition> catalogue;
    private final Clock clock;

    @Autowired
    public SetupChecklistService(
            TenantSetupStepRepository repository,
            EntitlementSource entitlementSource,
            List<SetupStepChecker> checkers) {
        this(repository, entitlementSource, checkers, SetupStepCatalogue.DEFAULT_STEPS, Clock.systemUTC());
    }

    /**
     * Validates the checker registry against the catalogue here, in the constructor, so a
     * catalogue step with no checker stops the application context from starting rather than
     * failing the first read (spec §4).
     */
    SetupChecklistService(
            TenantSetupStepRepository repository,
            EntitlementSource entitlementSource,
            List<SetupStepChecker> checkers,
            List<SetupStepCatalogue.StepDefinition> catalogue,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.entitlementSource = Objects.requireNonNull(entitlementSource, "entitlementSource must not be null");
        this.catalogue = List.copyOf(Objects.requireNonNull(catalogue, "catalogue must not be null"));
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        List<SetupStepChecker> registry = checkers != null ? checkers : List.of();
        List<String> problems = SetupStepCatalogue.registryProblems(this.catalogue, registry, false);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Setup step registry does not match the catalogue: " + problems);
        }
        this.checkersByCode = registry.stream()
                .collect(Collectors.toUnmodifiableMap(c -> c.code().toUpperCase(), c -> c));
    }

    /**
     * Assembles the setup steps for a tenant according to its active module subscriptions.
     *
     * <p>Idempotent: preserves existing progress and skips; inserts new steps when modules are added.
     */
    @Transactional
    public List<TenantSetupStep> assemble(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        Set<PlatformModule> activeModules = entitlementSource.modulesOf(tenantId);
        List<TenantSetupStep> existingSteps = repository.findByTenantIdOrderByDisplayOrderAsc(tenantId);
        Map<String, TenantSetupStep> existingByCode = existingSteps.stream()
                .collect(Collectors.toMap(s -> s.getStepCode().toUpperCase(), s -> s, (s1, s2) -> s1));

        List<TenantSetupStep> assembled = new ArrayList<>();
        Instant now = clock.instant();

        for (SetupStepCatalogue.StepDefinition def : catalogue) {
            if (isApplicable(def, activeModules)) {
                String codeKey = def.code().toUpperCase();
                TenantSetupStep step = existingByCode.get(codeKey);
                if (step == null) {
                    step = new TenantSetupStep(
                            tenantId, def.code().toUpperCase(), def.module(), def.displayOrder(), now);
                    step = repository.save(step);
                } else {
                    boolean changed = false;
                    if (step.getDisplayOrder() != def.displayOrder()) {
                        step.setDisplayOrder(def.displayOrder());
                        changed = true;
                    }
                    if (step.getModule() != def.module()) {
                        step.setModule(def.module());
                        changed = true;
                    }
                    if (changed) {
                        step = repository.save(step);
                    }
                }
                assembled.add(step);
            }
        }

        return assembled;
    }

    /**
     * Reads the checklist for the tenant, evaluates current completion of each step,
     * updates cache, and returns the response with progress.
     */
    @Transactional
    public SetupChecklistResponse getChecklist(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        assemble(tenantId);

        Set<PlatformModule> activeModules = entitlementSource.modulesOf(tenantId);
        List<TenantSetupStep> steps = repository.findByTenantIdOrderByDisplayOrderAsc(tenantId);

        // Refresh the completion cache first: a completion observed on this read is a touch.
        List<ListedStep> listed = new ArrayList<>();
        for (TenantSetupStep step : steps) {
            Optional<SetupStepCatalogue.StepDefinition> def =
                    SetupStepCatalogue.findByCode(catalogue, step.getStepCode());
            // A row whose step left the catalogue is kept (its skip survives a return) but not listed.
            if (def.isEmpty() || !isApplicable(def.get(), activeModules)) {
                continue;
            }

            SetupStepChecker checker = checkersByCode.get(step.getStepCode().toUpperCase());
            if (checker == null) {
                // Unreachable once the constructor has validated the registry for this module.
                throw new IllegalStateException("No SetupStepChecker registered for step: " + step.getStepCode());
            }

            boolean complete = checker.isComplete(tenantId);
            if (complete && step.getCompletedAt() == null) {
                step.setCompletedAt(clock.instant());
                repository.save(step);
            } else if (!complete && step.getCompletedAt() != null) {
                step.setCompletedAt(null);
                repository.save(step);
            }
            listed.add(new ListedStep(step, def.get()));
        }

        Map<PlatformModule, Instant> groupBaseline = new HashMap<>();
        Instant lastTouch = null;
        for (TenantSetupStep step : steps) {
            if (step.getFirstSeenAt() != null) {
                groupBaseline.merge(step.getModule(), step.getFirstSeenAt(), (a, b) -> a.isBefore(b) ? a : b);
            }
            lastTouch = later(lastTouch, step.getCompletedAt());
            if (step.isSkipped()) {
                lastTouch = later(lastTouch, step.getUpdatedAt());
            }
        }

        List<SetupStepResponse> stepResponses = new ArrayList<>();
        for (ListedStep ls : listed) {
            boolean isNew = isNewStep(ls.step(), groupBaseline.get(ls.step().getModule()), lastTouch);
            stepResponses.add(toResponse(ls.step(), ls.def(), isNew));
        }

        stepResponses.sort(Comparator.comparingInt(SetupStepResponse::displayOrder));

        int totalCount = stepResponses.size();
        int completedCount = (int)
                stepResponses.stream().filter(SetupStepResponse::completed).count();
        int skippedCount =
                (int) stepResponses.stream().filter(SetupStepResponse::skipped).count();
        int newCount =
                (int) stepResponses.stream().filter(SetupStepResponse::newStep).count();
        int countedCount = totalCount - newCount;
        int resolvedCount = (int) stepResponses.stream()
                .filter(s -> !s.newStep() && (s.completed() || s.skipped()))
                .count();

        double progress =
                countedCount == 0 ? 100.0 : Math.round(((double) resolvedCount / countedCount) * 1000.0) / 10.0;

        return new SetupChecklistResponse(stepResponses, completedCount, skippedCount, totalCount, newCount, progress);
    }

    /**
     * Marks a step as skipped with the given reason.
     */
    @Transactional
    public SetupStepResponse skipStep(UUID tenantId, String stepCode, String reason) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (stepCode == null || stepCode.isBlank()) {
            throw new IllegalArgumentException("stepCode must not be blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }

        assemble(tenantId);

        String normalizedCode = stepCode.trim().toUpperCase();
        TenantSetupStep step = repository
                .findByTenantIdAndStepCode(tenantId, normalizedCode)
                .orElseThrow(() -> new SetupStepNotFoundException(stepCode));

        Set<PlatformModule> activeModules = entitlementSource.modulesOf(tenantId);
        SetupStepCatalogue.StepDefinition def = SetupStepCatalogue.findByCode(catalogue, step.getStepCode())
                .orElseThrow(() -> new SetupStepNotFoundException(stepCode));

        if (!isApplicable(def, activeModules)) {
            throw new SetupStepNotFoundException(stepCode);
        }

        step.setSkipped(true);
        step.setSkipReason(reason.trim());
        step = repository.save(step);

        // A skipped step is resolved, so it is never new.
        return toResponse(step, def, false);
    }

    private static boolean isNewStep(TenantSetupStep step, Instant groupBaseline, Instant lastTouch) {
        if (step.getCompletedAt() != null || step.isSkipped()) {
            return false;
        }
        Instant firstSeen = step.getFirstSeenAt();
        if (firstSeen == null || groupBaseline == null || !firstSeen.isAfter(groupBaseline)) {
            return false;
        }
        return lastTouch == null || !lastTouch.isAfter(firstSeen);
    }

    private static Instant later(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return b.isAfter(a) ? b : a;
    }

    private static SetupStepResponse toResponse(
            TenantSetupStep step, SetupStepCatalogue.StepDefinition def, boolean isNew) {
        return new SetupStepResponse(
                step.getStepCode(),
                def.label(),
                step.getModule(),
                step.getDisplayOrder(),
                step.getCompletedAt() != null,
                step.isSkipped(),
                step.getSkipReason(),
                step.getCompletedAt(),
                step.getFirstSeenAt(),
                isNew);
    }

    private boolean isApplicable(SetupStepCatalogue.StepDefinition def, Set<PlatformModule> activeModules) {
        return def.module() == null || (activeModules != null && activeModules.contains(def.module()));
    }

    private record ListedStep(TenantSetupStep step, SetupStepCatalogue.StepDefinition def) {}

    public static class SetupStepNotFoundException extends RuntimeException {
        public SetupStepNotFoundException(String stepCode) {
            super("Setup step not found: " + stepCode);
        }
    }
}
