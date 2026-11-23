package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** W-114.3 spec, Tests: {@link AiToolRegistry} - startup checks, {@code toolsFor} and {@code execute}. */
class AiToolRegistryTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Set<String> granted = new HashSet<>();
    private final Set<PlatformModule> licensed = EnumSet.allOf(PlatformModule.class);

    private PermissionService permissionService;
    private EntitlementService entitlementService;
    private AiToolContext caller;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(userId.toString(), null, List.of()));
        permissionService = mock(PermissionService.class);
        when(permissionService.holds(anyString())).thenAnswer(inv -> granted.contains(inv.<String>getArgument(0)));
        entitlementService = mock(EntitlementService.class);
        when(entitlementService.holds(any(PlatformModule.class)))
                .thenAnswer(inv -> licensed.contains(inv.<PlatformModule>getArgument(0)));
        caller = new AiToolContext(tenantId, userId, null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("two tools of the same name fail startup, and the message names both")
    void duplicateName_failsStartup() {
        AiTool first = new FakeTool("core.my_leave_balances", "core.leave.read_own");
        AiTool second = new OtherFakeTool("core.my_leave_balances", "core.leave.read_own");

        assertThatThrownBy(() -> registry(() -> List.of(first), () -> List.of(second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("core.my_leave_balances")
                .hasMessageContaining(FakeTool.class.getName())
                .hasMessageContaining(OtherFakeTool.class.getName());
    }

    @Test
    @DisplayName("a name without its module prefix, or not module.verb_noun, fails startup")
    void nameWithoutModule_failsStartup() {
        for (String bad : List.of("my_leave_balances", "Core.my_leave", "core.", "core.my.leave", "core.My_leave")) {
            assertThatThrownBy(() -> registry(() -> List.of(new FakeTool(bad, "core.leave.read_own"))))
                    .as(bad)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("module.verb_noun");
        }
    }

    @Test
    @DisplayName("a tool with no requiredAction fails startup")
    void noRequiredAction_failsStartup() {
        assertThatThrownBy(() -> registry(() -> List.of(new FakeTool("core.my_leave", " "))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requiredAction");
    }

    @Test
    @DisplayName("toolsFor hides a tool the caller lacks the action for; find still finds it")
    void toolsFor_hidesToolWithoutAction() {
        FakeTool balances = new FakeTool("core.my_leave_balances", "core.leave.read_own");
        FakeTool payslips = new FakeTool("payroll.my_payslips", "payroll.payslip.read_own");
        AiToolRegistry registry = registry(() -> List.of(balances), () -> List.of(payslips));
        granted.add("core.leave.read_own");

        assertThat(names(registry.toolsFor(caller))).containsExactly("core.my_leave_balances");
        assertThat(registry.find("payroll.my_payslips").map(AiToolDescriptor::name))
                .contains("payroll.my_payslips");
        assertThat(registry.find("core.nothing")).isEmpty();
    }

    @Test
    @DisplayName("a caller holding only a requiredAnyOf code is offered the tool and may run it")
    void anyOfCode_offeredAndRuns() {
        FakeTool team = new FakeTool(
                "core.team_leave", "core.leave.read", List.of("core.leave.read_team", "core.leave.approve"));
        AiToolRegistry registry = registry(() -> List.of(team));
        granted.add("core.leave.approve");

        assertThat(names(registry.toolsFor(caller))).containsExactly(team.name());
        AiToolResult result = registry.execute("core.team_leave", caller, JsonNodeFactory.instance.objectNode());
        assertThat(result.ok()).isTrue();
        assertThat(team.runs.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a caller holding neither the action nor any requiredAnyOf code is not offered it and cannot run it")
    void neitherCode_notOfferedNotRun() {
        FakeTool team = new FakeTool(
                "core.team_leave", "core.leave.read", List.of("core.leave.read_team", "core.leave.approve"));
        AiToolRegistry registry = registry(() -> List.of(team));
        granted.add("core.leave.read_own");

        assertThat(registry.toolsFor(caller)).isEmpty();
        AiToolResult result = registry.execute("core.team_leave", caller, JsonNodeFactory.instance.objectNode());
        assertThat(result.ok()).isFalse();
        assertThat(team.runs.get()).isZero();
    }

    @Test
    @DisplayName("execute re-checks at call time: an action withdrawn after toolsFor is refused and nothing runs")
    void execute_refusesWhenActionMissing() {
        FakeTool balances = new FakeTool("core.my_leave_balances", "core.leave.read_own");
        AiToolRegistry registry = registry(() -> List.of(balances));
        granted.add("core.leave.read_own");
        assertThat(names(registry.toolsFor(caller))).containsExactly(balances.name());

        granted.clear();
        AiToolResult result = registry.execute("core.my_leave_balances", caller, null);

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isEqualTo("Not permitted");
        assertThat(balances.runs.get()).isZero();
    }

    @Test
    @DisplayName("a context naming another user or another tenant is offered nothing and runs nothing")
    void foreignContext_refused() {
        FakeTool balances = new FakeTool("core.my_leave_balances", "core.leave.read_own");
        AiToolRegistry registry = registry(() -> List.of(balances));
        granted.add("core.leave.read_own");

        AiToolContext otherUser = new AiToolContext(tenantId, UUID.randomUUID(), null);
        AiToolContext otherTenant = new AiToolContext(UUID.randomUUID(), userId, null);

        assertThat(registry.toolsFor(otherUser)).isEmpty();
        assertThat(registry.toolsFor(otherTenant)).isEmpty();
        assertThat(registry.execute("core.my_leave_balances", otherUser, null).ok())
                .isFalse();
        assertThat(registry.execute("core.my_leave_balances", otherTenant, null).ok())
                .isFalse();
        assertThat(registry.execute("core.unknown_tool", caller, null).ok()).isFalse();
        assertThat(balances.runs.get()).isZero();
    }

    @Test
    @DisplayName("no providers: an empty registry starts")
    void noProviders() {
        AiToolRegistry registry = new AiToolRegistry(permissionService, entitlementService, List.of());
        assertThat(registry.toolsFor(caller)).isEmpty();
    }

    @Test
    @DisplayName("a tool name whose module is not core, hrms or payroll fails startup")
    void unknownModulePrefix_failsStartup() {
        AiTool tool = new FakeTool("reports.my_exports", "core.report.read");

        assertThatThrownBy(() -> registry(() -> List.of(tool)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reports.my_exports")
                .hasMessageContaining("core, hrms, payroll");
    }

    @Test
    @DisplayName("a payroll tool is hidden and refused while the customer does not hold Payroll; a core tool is not")
    void moduleNotHeld_hiddenAndRefused() {
        FakeTool payslips = new FakeTool("payroll.my_payslips", "payroll.payslip.read_own");
        FakeTool balances = new FakeTool("core.my_leave_balances", "core.leave.read_own");
        AiToolRegistry registry = registry(() -> List.of(payslips, balances));
        granted.add("payroll.payslip.read_own");
        granted.add("core.leave.read_own");
        licensed.remove(PlatformModule.PAYROLL);

        assertThat(names(registry.toolsFor(caller))).containsExactly("core.my_leave_balances");
        assertThat(registry.execute("payroll.my_payslips", caller, null).ok()).isFalse();
        assertThat(payslips.runs.get()).isZero();
        assertThat(registry.execute("core.my_leave_balances", caller, null).ok())
                .isTrue();
    }

    @Test
    @DisplayName("W-114.4: a name holding __ fails startup, since the model client sends the dot as __")
    void doubleUnderscore_failsStartup() {
        assertThatThrownBy(() -> registry(() -> List.of(new FakeTool("core.my__leave", "core.leave.read_own"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("core.my__leave")
                .hasMessageContaining("__");
    }

    @Test
    @DisplayName("W-114.4: a name longer than 64 once the dot is __ fails startup; exactly 64 starts")
    void tooLongWireName_failsStartup() {
        String ok = "core." + "a".repeat(64 - "core__".length());
        String tooLong = ok + "b";
        assertThat(names(registry(() -> List.of(new FakeTool(ok, "core.x.y"))).toolsFor(caller)))
                .isEmpty(); // starts; not offered only because the caller holds nothing
        assertThatThrownBy(() -> registry(() -> List.of(new FakeTool(tooLong, "core.x.y"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64");
    }

    @Test
    @DisplayName("W-114.4 rule 4: no employee record hides the own-data tools and refuses running them")
    void noEmployee_hidesAndRefusesOwnDataTools() {
        EmployeeFakeTool balances = new EmployeeFakeTool("core.my_leave_balances", "core.leave.read_own");
        FakeTool holidays = new FakeTool("core.holidays", "core.holiday.read");
        AiToolRegistry registry = registry(() -> List.of(balances, holidays));
        granted.add("core.leave.read_own");
        granted.add("core.holiday.read");

        assertThat(names(registry.toolsFor(caller))).containsExactly("core.holidays");
        AiToolResult refused = registry.execute("core.my_leave_balances", caller, null);
        assertThat(refused.ok()).isFalse();
        assertThat(balances.runs.get()).isZero();

        AiToolContext withEmployee = new AiToolContext(tenantId, userId, UUID.randomUUID());
        assertThat(names(registry.toolsFor(withEmployee))).containsExactly("core.my_leave_balances", "core.holidays");
        assertThat(registry.execute("core.my_leave_balances", withEmployee, null).ok())
                .isTrue();
        assertThat(balances.runs.get()).isEqualTo(1);
    }

    private static List<String> names(List<AiToolDescriptor> tools) {
        return tools.stream().map(AiToolDescriptor::name).toList();
    }

    private AiToolRegistry registry(AiToolProvider... providers) {
        return new AiToolRegistry(permissionService, entitlementService, List.of(providers));
    }

    private static class FakeTool implements AiTool {
        private final String name;
        private final String requiredAction;
        private final List<String> anyOf;
        final AtomicInteger runs = new AtomicInteger();

        FakeTool(String name, String requiredAction) {
            this(name, requiredAction, List.of());
        }

        FakeTool(String name, String requiredAction, List<String> anyOf) {
            this.name = name;
            this.requiredAction = requiredAction;
            this.anyOf = anyOf;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "A test tool";
        }

        @Override
        public JsonNode inputSchema() {
            return JsonNodeFactory.instance.objectNode().put("type", "object");
        }

        @Override
        public String requiredAction() {
            return requiredAction;
        }

        @Override
        public List<String> requiredAnyOf() {
            return anyOf;
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            runs.incrementAndGet();
            return AiToolResult.success(JsonNodeFactory.instance.objectNode().put("ran", true));
        }
    }

    private static class EmployeeFakeTool extends FakeTool {
        EmployeeFakeTool(String name, String requiredAction) {
            super(name, requiredAction);
        }

        @Override
        public boolean needsEmployee() {
            return true;
        }
    }

    private static class OtherFakeTool extends FakeTool {
        OtherFakeTool(String name, String requiredAction) {
            super(name, requiredAction);
        }
    }
}
