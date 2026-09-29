package com.infinevo.core.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.entitlement.EntitlementSource;
import com.infinevo.shared.entitlement.PlatformModule;
import java.time.Instant;
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

class SetupChecklistServiceTest {

    private TenantSetupStepRepository repository;
    private EntitlementSource entitlementSource;
    private final Map<String, TenantSetupStep> database = new HashMap<>();

    private SetupStepChecker workLocationChecker;
    private SetupStepChecker employeeChecker;
    private SetupStepChecker payScheduleChecker;
    private SetupStepChecker priorPayrollChecker;
    private SetupStepChecker orgTaxChecker;
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
        priorPayrollChecker = createChecker("PRIOR_PAYROLL", PlatformModule.PAYROLL, false);
        orgTaxChecker = createChecker("ORGANISATION_TAX", PlatformModule.PAYROLL, false);
        salaryComponentsChecker = createChecker("SALARY_COMPONENTS", PlatformModule.PAYROLL, false);
        epfChecker = createChecker("EPF", PlatformModule.PAYROLL, false);
        esiChecker = createChecker("ESI", PlatformModule.PAYROLL, false);
        ptaxChecker = createChecker("PROFESSIONAL_TAX", PlatformModule.PAYROLL, false);

        List<SetupStepChecker> checkers = List.of(
                workLocationChecker,
                employeeChecker,
                payScheduleChecker,
                priorPayrollChecker,
                orgTaxChecker,
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
    @DisplayName("a tenant holding both modules gets all 9 steps")
    void bothModulesTenantGetsAllSteps() {
        UUID fullTenant = UUID.randomUUID();
        when(entitlementSource.modulesOf(fullTenant)).thenReturn(Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL));

        SetupChecklistResponse response = service.getChecklist(fullTenant);

        assertThat(response.steps()).hasSize(9);
        assertThat(response.totalCount()).isEqualTo(9);
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
}
