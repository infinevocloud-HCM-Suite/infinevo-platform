package com.infinevo.core.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.onboarding.EmployeeOnboardingTaskRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OnboardingPanelProviderTest {

    private EmployeeOnboardingTaskRepository taskRepository;
    private OnboardingPanelProvider provider;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        taskRepository = mock(EmployeeOnboardingTaskRepository.class);
        provider = new OnboardingPanelProvider(taskRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Provider declares code and null module (core)")
    void providerDeclaration() {
        assertThat(provider.code()).isEqualTo("onboarding");
        assertThat(provider.module()).isNull();
    }

    @Test
    @DisplayName("panel() returns null when employee has no tasks")
    void returnsNullWhenNoTasks() {
        when(taskRepository.countByTenantIdAndEmployeeId(tenantId, employeeId)).thenReturn(0L);

        PanelDescriptor descriptor = provider.panel(employeeId);

        assertThat(descriptor).isNull();
    }

    @Test
    @DisplayName(
            "panel() returns descriptor with order 9 (after every other panel) when employee has one or more tasks")
    void returnsDescriptorWhenHasTasks() {
        when(taskRepository.countByTenantIdAndEmployeeId(tenantId, employeeId)).thenReturn(3L);

        PanelDescriptor descriptor = provider.panel(employeeId);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.code()).isEqualTo("onboarding");
        assertThat(descriptor.title()).isEqualTo("Onboarding");
        assertThat(descriptor.displayOrder()).isEqualTo(9);
        assertThat(descriptor.endpoint()).isEqualTo("/api/v1/me/onboarding");
        assertThat(descriptor.requiredAction()).isEqualTo("core.onboarding.read_own");
    }
}
