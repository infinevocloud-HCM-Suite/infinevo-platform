package com.infinevo.core.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.org.WorkLocationRepository;
import com.infinevo.shared.entitlement.EntitlementSource;
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

class SetupStepDetectionTest {

    private TenantSetupStepRepository repository;
    private EntitlementSource entitlementSource;
    private WorkLocationRepository workLocationRepository;
    private EmployeeRepository employeeRepository;
    private final Map<String, TenantSetupStep> database = new HashMap<>();

    @BeforeEach
    void setUp() {
        database.clear();
        repository = mock(TenantSetupStepRepository.class);
        entitlementSource = mock(EntitlementSource.class);
        workLocationRepository = mock(WorkLocationRepository.class);
        employeeRepository = mock(EmployeeRepository.class);

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
    @DisplayName("step completes when checker detects data and reverts to incomplete when data is removed")
    void stepCompletesAndReverts() {
        UUID tenantId = UUID.randomUUID();
        when(entitlementSource.modulesOf(tenantId)).thenReturn(Set.of());

        WorkLocationSetupStepChecker wlChecker = new WorkLocationSetupStepChecker(workLocationRepository);
        EmployeeSetupStepChecker empChecker = new EmployeeSetupStepChecker(employeeRepository);

        SetupChecklistService service =
                new SetupChecklistService(repository, entitlementSource, List.of(wlChecker, empChecker));

        // 1. Initially no work location exists -> incomplete
        when(workLocationRepository.existsByTenantId(tenantId)).thenReturn(false);
        when(employeeRepository.existsByTenantIdAndDeletedFalse(tenantId)).thenReturn(false);

        SetupChecklistResponse response1 = service.getChecklist(tenantId);
        SetupStepResponse wl1 = response1.steps().stream()
                .filter(s -> s.code().equals("WORK_LOCATION"))
                .findFirst()
                .orElseThrow();
        assertThat(wl1.completed()).isFalse();
        assertThat(wl1.completedAt()).isNull();

        // 2. Data added -> work location exists -> checker detects completion
        when(workLocationRepository.existsByTenantId(tenantId)).thenReturn(true);

        SetupChecklistResponse response2 = service.getChecklist(tenantId);
        SetupStepResponse wl2 = response2.steps().stream()
                .filter(s -> s.code().equals("WORK_LOCATION"))
                .findFirst()
                .orElseThrow();
        assertThat(wl2.completed()).isTrue();
        assertThat(wl2.completedAt()).isNotNull();

        // 3. Data removed -> only work location deleted -> reverts to incomplete
        when(workLocationRepository.existsByTenantId(tenantId)).thenReturn(false);

        SetupChecklistResponse response3 = service.getChecklist(tenantId);
        SetupStepResponse wl3 = response3.steps().stream()
                .filter(s -> s.code().equals("WORK_LOCATION"))
                .findFirst()
                .orElseThrow();
        assertThat(wl3.completed()).isFalse();
        assertThat(wl3.completedAt()).isNull();
    }
}
