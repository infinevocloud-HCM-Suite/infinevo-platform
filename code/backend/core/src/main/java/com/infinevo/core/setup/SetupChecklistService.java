package com.infinevo.core.setup;

import com.infinevo.shared.entitlement.EntitlementSource;
import com.infinevo.shared.entitlement.PlatformModule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing tenant setup checklist assembly, step evaluation, and progress (W-24.1).
 */
@Service
public class SetupChecklistService {

    private final TenantSetupStepRepository repository;
    private final EntitlementSource entitlementSource;
    private final Map<String, SetupStepChecker> checkersByCode = new ConcurrentHashMap<>();

    public SetupChecklistService(
            TenantSetupStepRepository repository,
            EntitlementSource entitlementSource,
            List<SetupStepChecker> checkers) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.entitlementSource = Objects.requireNonNull(entitlementSource, "entitlementSource must not be null");
        if (checkers != null) {
            for (SetupStepChecker checker : checkers) {
                this.checkersByCode.put(checker.code().toUpperCase(), checker);
            }
        }
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
                .collect(Collectors.toMap(s -> s.getStepCode().toUpperCase(), s -> s));

        List<TenantSetupStep> assembled = new ArrayList<>();
        Instant now = Instant.now();

        for (SetupStepCatalogue.StepDefinition def : SetupStepCatalogue.DEFAULT_STEPS) {
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

        List<SetupStepResponse> stepResponses = new ArrayList<>();

        for (TenantSetupStep step : steps) {
            SetupStepCatalogue.StepDefinition def = SetupStepCatalogue.findByCode(step.getStepCode())
                    .orElse(new SetupStepCatalogue.StepDefinition(
                            step.getStepCode(), step.getStepCode(), step.getModule(), step.getDisplayOrder()));

            if (!isApplicable(def, activeModules)) {
                continue;
            }

            SetupStepChecker checker = checkersByCode.get(step.getStepCode().toUpperCase());
            if (checker == null) {
                throw new IllegalStateException("No SetupStepChecker registered for step: " + step.getStepCode());
            }

            boolean complete = checker.isComplete(tenantId);
            if (complete && step.getCompletedAt() == null) {
                step.setCompletedAt(Instant.now());
                repository.save(step);
            } else if (!complete && step.getCompletedAt() != null) {
                step.setCompletedAt(null);
                repository.save(step);
            }

            stepResponses.add(new SetupStepResponse(
                    step.getStepCode(),
                    def.label(),
                    step.getModule(),
                    step.getDisplayOrder(),
                    step.getCompletedAt() != null,
                    step.isSkipped(),
                    step.getSkipReason(),
                    step.getCompletedAt(),
                    step.getFirstSeenAt()));
        }

        stepResponses.sort(Comparator.comparingInt(SetupStepResponse::displayOrder));

        int totalCount = stepResponses.size();
        int completedCount = (int)
                stepResponses.stream().filter(SetupStepResponse::completed).count();
        int skippedCount =
                (int) stepResponses.stream().filter(SetupStepResponse::skipped).count();
        int resolvedCount = (int)
                stepResponses.stream().filter(s -> s.completed() || s.skipped()).count();

        double progress = totalCount == 0 ? 100.0 : Math.round(((double) resolvedCount / totalCount) * 1000.0) / 10.0;

        return new SetupChecklistResponse(stepResponses, completedCount, skippedCount, totalCount, progress);
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

        step.setSkipped(true);
        step.setSkipReason(reason.trim());
        step = repository.save(step);

        SetupStepCatalogue.StepDefinition def = SetupStepCatalogue.findByCode(step.getStepCode())
                .orElse(new SetupStepCatalogue.StepDefinition(
                        step.getStepCode(), step.getStepCode(), step.getModule(), step.getDisplayOrder()));

        return new SetupStepResponse(
                step.getStepCode(),
                def.label(),
                step.getModule(),
                step.getDisplayOrder(),
                step.getCompletedAt() != null,
                step.isSkipped(),
                step.getSkipReason(),
                step.getCompletedAt(),
                step.getFirstSeenAt());
    }

    private boolean isApplicable(SetupStepCatalogue.StepDefinition def, Set<PlatformModule> activeModules) {
        return def.module() == null || (activeModules != null && activeModules.contains(def.module()));
    }

    public static class SetupStepNotFoundException extends RuntimeException {
        public SetupStepNotFoundException(String stepCode) {
            super("Setup step not found: " + stepCode);
        }
    }
}
