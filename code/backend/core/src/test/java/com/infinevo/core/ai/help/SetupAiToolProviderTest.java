package com.infinevo.core.ai.help;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.setup.SetupChecklistResponse;
import com.infinevo.core.setup.SetupChecklistService;
import com.infinevo.core.setup.SetupStepResponse;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-114.16: core.setup_status - the checklist's own steps and counts, each with a screen only the caller may open. */
class SetupAiToolProviderTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HelpLibrary LIBRARY = new HelpLibrary();
    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-11-20T10:00:00Z");

    private final SetupChecklistService service = mock(SetupChecklistService.class);
    private final AiToolContext ctx = new AiToolContext(TENANT, UUID.randomUUID(), null);

    @BeforeEach
    void bind() {
        TenantContext.set(TENANT);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private static SetupStepResponse step(
            String code, String label, PlatformModule module, boolean completed, boolean skipped, boolean prefilled) {
        return new SetupStepResponse(
                code,
                label,
                module,
                1,
                completed,
                skipped,
                skipped ? "later" : null,
                completed ? AT : null,
                AT,
                false,
                prefilled);
    }

    private static SetupChecklistResponse checklist() {
        return new SetupChecklistResponse(
                List.of(
                        step("WORK_LOCATION", "Work location", null, true, false, false),
                        step("EMPLOYEE", "Employee", null, false, false, false),
                        step("PAY_SCHEDULE", "Pay schedule", PlatformModule.PAYROLL, false, true, false),
                        step("EPF", "EPF", PlatformModule.PAYROLL, true, false, true)),
                2,
                1,
                4,
                0,
                75.0);
    }

    /** A tenant admin whose menu holds work locations, employees and payroll settings. */
    private static HelpViewer admin(boolean payrollSettings) {
        Map<String, String> leaves = new LinkedHashMap<>();
        leaves.put("core.org.locations", "/org/work-locations");
        leaves.put("core.employee", "/employees");
        if (payrollSettings) {
            leaves.put("payroll.settings", "/payroll/settings");
        }
        return new HelpViewer(
                Set.copyOf(leaves.values()),
                Set.of("core.tenant.read"),
                Set.of("tenant-admin", "hr", "payroll-officer"),
                Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL),
                leaves);
    }

    private AiTool tool(HelpViewer viewer) {
        return new SetupAiToolProvider(service, LIBRARY, HelpAccess.fixed(viewer))
                .tools()
                .get(0);
    }

    @Test
    @DisplayName("name, core.tenant.read, no employee needed; refuses unknown input")
    void shape() throws Exception {
        AiTool tool = tool(admin(true));
        assertThat(tool.name()).isEqualTo("core.setup_status");
        assertThat(tool.requiredAction()).isEqualTo("core.tenant.read");
        assertThat(tool.needsEmployee()).isFalse();
        assertThat(tool.execute(ctx, JSON.readTree("{\"skip\":\"EPF\"}")).ok()).isFalse();
    }

    @Test
    @DisplayName("the four states, the service's counts, each screen and article; no id or timestamp")
    void statesAndCounts() throws Exception {
        when(service.getChecklist(TENANT)).thenReturn(checklist());

        AiToolResult result = tool(admin(true)).execute(ctx, JSON.readTree("{}"));

        assertThat(result.ok()).isTrue();
        JsonNode out = result.data();
        verify(service).getChecklist(TENANT);
        assertThat(out.path("total").asInt()).isEqualTo(4);
        assertThat(out.path("done").asInt()).isEqualTo(2);
        assertThat(out.path("skipped").asInt()).isEqualTo(1);
        JsonNode steps = out.path("steps");
        assertThat(steps.findValuesAsText("state")).containsExactly("DONE", "NOT_DONE", "SKIPPED", "REVIEW");
        assertThat(steps.get(0).path("screen").path("path").asText()).isEqualTo("/org/work-locations");
        assertThat(steps.get(0).path("screen").path("name").asText()).isEqualTo("Work locations");
        assertThat(steps.get(2).path("screen").path("path").asText()).isEqualTo("/payroll/settings/pay-schedule");
        assertThat(steps.get(2).path("screen").path("name").asText()).isEqualTo("Payroll settings - Pay schedule");
        assertThat(steps.get(0).path("article").asText()).isEqualTo("work-locations");
        assertThat(steps.get(3).path("article").asText()).isEqualTo("statutory-settings");
        assertThat(out.toString())
                .doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-")
                .doesNotContain("2026-11-20")
                .doesNotContain("later");
    }

    @Test
    @DisplayName("a step skipped and finished later is DONE, as the Setup screen shows it")
    void skippedThenDone() {
        assertThat(SetupAiToolProvider.state(step("ESI", "ESI", PlatformModule.PAYROLL, true, true, false)))
                .isEqualTo("DONE");
        assertThat(SetupAiToolProvider.state(step("ESI", "ESI", PlatformModule.PAYROLL, false, true, false)))
                .isEqualTo("SKIPPED");
    }

    @Test
    @DisplayName(
            "a step whose menu item is not in the caller's menu has no screen; an article they cannot see is left out")
    void screenOnlyWhenTheMenuHoldsIt() throws Exception {
        when(service.getChecklist(TENANT)).thenReturn(checklist());
        HelpViewer hr = new HelpViewer(
                Set.of("/org/work-locations", "/employees"),
                Set.of("core.tenant.read"),
                Set.of("hr"),
                Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL),
                Map.of("core.org.locations", "/org/work-locations", "core.employee", "/employees"));

        JsonNode steps = tool(hr).execute(ctx, JSON.readTree("{}")).data().path("steps");

        assertThat(steps.get(2).has("screen")).isFalse();
        assertThat(steps.get(3).has("screen")).isFalse();
        assertThat(steps.get(3).has("article")).isFalse();
        assertThat(steps.get(1).path("screen").path("path").asText()).isEqualTo("/employees");
    }
}
