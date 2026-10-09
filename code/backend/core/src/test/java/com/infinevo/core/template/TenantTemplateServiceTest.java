package com.infinevo.core.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.infinevo.shared.entitlement.EntitlementSource;
import com.infinevo.shared.entitlement.PlatformModule;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TenantTemplateServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
    private static final JsonNode EMPTY = JsonNodeFactory.instance.objectNode();

    private final UUID tenantId = UUID.randomUUID();
    private final CountryTemplateReader reader = mock(CountryTemplateReader.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EntitlementSource entitlements = mock(EntitlementSource.class);
    private final List<String> calls = new ArrayList<>();

    private TenantTemplateContributor contributor(String section, PlatformModule module, boolean writes) {
        return new TenantTemplateContributor() {
            @Override
            public String section() {
                return section;
            }

            @Override
            public PlatformModule module() {
                return module;
            }

            @Override
            public boolean apply(UUID id, JsonNode payload, LocalDate today) {
                assertThat(today).isEqualTo(TODAY);
                calls.add(section);
                return writes;
            }
        };
    }

    private TenantTemplateService service(TenantTemplateContributor... contributors) {
        return new TenantTemplateService(reader, jdbc, List.of(contributors), entitlements, () -> TODAY);
    }

    private static CountryTemplateSection section(String name) {
        return new CountryTemplateSection("IN", name, 1, EMPTY);
    }

    @Test
    @DisplayName("sections run in the fixed order; written ones are applied, the rest skipped, each recorded")
    void appliesInOrderAndRecords() {
        when(reader.sections("IN"))
                .thenReturn(List.of(
                        section("statutory"),
                        section("holidays"),
                        section("salary_components"),
                        section("leave_types"),
                        section("pay_schedule")));
        when(entitlements.modulesOf(tenantId)).thenReturn(Set.of(PlatformModule.PAYROLL));

        TemplateApplyResponse result = service(
                        contributor("holidays", null, true),
                        contributor("leave_types", null, false),
                        contributor("pay_schedule", PlatformModule.PAYROLL, true),
                        contributor("salary_components", PlatformModule.PAYROLL, true),
                        contributor("statutory", PlatformModule.PAYROLL, true))
                .apply(tenantId, "IN");

        assertThat(calls).containsExactly("holidays", "leave_types", "pay_schedule", "salary_components", "statutory");
        assertThat(result.countryCode()).isEqualTo("IN");
        assertThat(result.applied()).containsExactly("holidays", "pay_schedule", "salary_components", "statutory");
        assertThat(result.skipped()).containsExactly("leave_types");
        verify(jdbc, times(5))
                .update(anyString(), eq(tenantId), eq("IN"), anyString(), eq(1), anyString(), eq("template"));
        verify(jdbc)
                .update(anyString(), eq(tenantId), eq("IN"), eq("leave_types"), eq(1), eq("SKIPPED"), eq("template"));
    }

    @Test
    @DisplayName("a module section is skipped, unwritten, for a tenant without the module; so is a section with no"
            + " contributor")
    void skipsWithoutModuleOrContributor() {
        when(reader.sections("IN")).thenReturn(List.of(section("holidays"), section("statutory")));
        when(entitlements.modulesOf(tenantId)).thenReturn(Set.of(PlatformModule.HRMS));

        TemplateApplyResponse withoutPayroll =
                service(contributor("statutory", PlatformModule.PAYROLL, true)).apply(tenantId, "IN");

        assertThat(calls).isEmpty();
        assertThat(withoutPayroll.applied()).isEmpty();
        assertThat(withoutPayroll.skipped()).containsExactly("holidays", "statutory");
    }

    @Test
    @DisplayName("a country with no template writes and records nothing")
    void noTemplate() {
        when(reader.sections("AE")).thenReturn(List.of());

        TemplateApplyResponse result =
                service(contributor("holidays", null, true)).apply(tenantId, "AE");

        assertThat(result.applied()).isEmpty();
        assertThat(result.skipped()).isEmpty();
        assertThat(calls).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("two contributors for one section stop the context from starting")
    void duplicateSectionRefused() {
        assertThatThrownBy(() -> service(contributor("holidays", null, true), contributor("holidays", null, true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("holidays");
    }
}
