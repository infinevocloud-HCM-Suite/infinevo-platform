package com.infinevo.core.ai.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.ai.help.HelpViewer;
import com.infinevo.core.ai.starter.AiStarter.Kind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-114.22 rules 2 to 4, over the shipped list. */
class AiStarterServiceTest {

    private static final List<AiStarter> SHIPPED = new AiStarterRegistry().all();

    private static HelpViewer viewer(Set<String> roles, Map<String, String> leaves) {
        return new HelpViewer(Set.copyOf(leaves.values()), Set.of(), roles, Set.of(), leaves);
    }

    private static List<String> ids(List<AiStarter> list) {
        return list.stream().map(AiStarter::id).toList();
    }

    @Test
    @DisplayName("an employee with only the W-114.4 tools: leave left and holidays; nothing for a tool not built")
    void onlyOfferedTools() {
        List<AiStarter> out = AiStarterService.pick(
                SHIPPED, Set.of("core.my_leave_balances", "core.holidays"), viewer(Set.of("employee"), Map.of()));
        assertThat(ids(out)).containsExactly("leave-left", "holidays-next");
    }

    @Test
    @DisplayName("once the payslip tools are offered, the two payslip starters appear with no change here")
    void newPartAppears() {
        List<AiStarter> out = AiStarterService.pick(
                SHIPPED,
                Set.of("core.my_leave_balances", "core.holidays", "payroll.explain_payslip", "payroll.explain_tds"),
                viewer(Set.of("employee"), Map.of()));
        assertThat(ids(out)).containsExactly("leave-left", "payslip-explain", "tds-change", "holidays-next");
    }

    @Test
    @DisplayName("a starter whose screen is not in the caller's menu is hidden; in the menu it shows")
    void screenKeyMustBeInTheMenu() {
        Set<String> offered = Set.of("core.search_help", "core.setup_status", "core.my_role_guide", "core.find_screen");
        List<AiStarter> without = AiStarterService.pick(
                SHIPPED, offered, viewer(Set.of("tenant-admin"), Map.of("core.users", "/admin/users")));
        assertThat(ids(without)).contains("setup-left", "invite-hr").doesNotContain("statutory-howto", "go-roles");

        List<AiStarter> with = AiStarterService.pick(
                SHIPPED,
                offered,
                viewer(
                        Set.of("tenant-admin"),
                        Map.of("core.users", "/admin/users", "payroll.settings", "/payroll/settings")));
        assertThat(ids(with)).contains("statutory-howto");
    }

    @Test
    @DisplayName("own-role starters first; two roles alternate; never more than six")
    void ordering() {
        Set<String> everything = SHIPPED.stream().map(AiStarter::toolName).collect(Collectors.toSet());
        Map<String, String> menu = Map.of(
                "core.leave.types", "/leave/types",
                "core.leave.allocations", "/leave/allocations");

        List<AiStarter> out = AiStarterService.pick(SHIPPED, everything, viewer(Set.of("hr", "manager"), menu));

        assertThat(out).hasSize(6);
        assertThat(out)
                .extracting(s -> s.roles().get(0))
                .containsExactly("hr", "manager", "hr", "manager", "hr", "manager");

        List<AiStarter> employeeOnly = AiStarterService.pick(SHIPPED, everything, viewer(Set.of("employee"), Map.of()));
        assertThat(employeeOnly).hasSize(6).allSatisfy(s -> assertThat(s.roles())
                .contains("employee"));
    }

    @Test
    @DisplayName("no role written for: the usable starters in registry order; nothing offered: an empty list")
    void noRolesAndNoTools() {
        List<AiStarter> out = AiStarterService.pick(
                SHIPPED, Set.of("core.find_screen"), viewer(Set.of(), Map.of("payroll.runs", "/payroll/runs")));
        assertThat(ids(out)).containsExactly("go-payruns");
        assertThat(AiStarterService.pick(SHIPPED, Set.of(), viewer(Set.of("hr"), Map.of())))
                .isEmpty();
        assertThat(SHIPPED).extracting(AiStarter::kind).contains(Kind.QUESTION, Kind.TASK);
    }
}
