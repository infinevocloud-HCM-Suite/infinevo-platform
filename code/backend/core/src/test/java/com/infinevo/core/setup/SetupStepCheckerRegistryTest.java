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

        SetupStepChecker payrollChecker = mock(SetupStepChecker.class);
        when(payrollChecker.code()).thenReturn("PAY_SCHEDULE");
        when(payrollChecker.module()).thenReturn(PlatformModule.PAYROLL);

        SetupChecklistService service = new SetupChecklistService(
                repository, entitlementSource, List.of(coreChecker1, coreChecker2, payrollChecker));

        SetupChecklistResponse response = service.getChecklist(hrmsTenant);

        assertThat(response.steps()).hasSize(2);
        verify(payrollChecker, never()).isComplete(any());
    }

    @Test
    @DisplayName("a catalogue step applicable to tenant with no registered checker fails fast")
    void missingCheckerFailsFast() {
        UUID hrmsTenant = UUID.randomUUID();
        when(entitlementSource.modulesOf(hrmsTenant)).thenReturn(Set.of(PlatformModule.HRMS));

        // Only provide WORK_LOCATION checker, omitting EMPLOYEE
        SetupStepChecker coreChecker1 = mock(SetupStepChecker.class);
        when(coreChecker1.code()).thenReturn("WORK_LOCATION");
        when(coreChecker1.module()).thenReturn(null);

        SetupChecklistService service = new SetupChecklistService(repository, entitlementSource, List.of(coreChecker1));

        assertThatThrownBy(() -> service.getChecklist(hrmsTenant))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No SetupStepChecker registered for step: EMPLOYEE");
    }
}
