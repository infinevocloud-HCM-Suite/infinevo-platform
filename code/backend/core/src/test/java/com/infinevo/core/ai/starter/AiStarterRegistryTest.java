package com.infinevo.core.ai.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.ai.starter.AiStarter.Kind;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-114.22 rule 7: the shipped list passes the startup checks; each kind of bad starter is refused. */
class AiStarterRegistryTest {

    private static AiStarter starter(String id, String label, String tool) {
        return new AiStarter(id, label, label, Kind.QUESTION, tool, null, List.of("employee"));
    }

    @Test
    @DisplayName("the shipped list loads: 28 starters, labels at most 40, every role of the spec present")
    void shippedListLoads() {
        AiStarterRegistry registry = new AiStarterRegistry();
        assertThat(registry.all()).hasSize(28);
        assertThat(registry.all())
                .allSatisfy(s -> assertThat(s.label().length()).isLessThanOrEqualTo(40));
        assertThat(registry.all()).flatExtracting(AiStarter::roles).containsAll(AiStarterRegistry.ROLE_ORDER);
        assertThat(registry.all())
                .filteredOn(s -> s.toolName().startsWith("payroll.payrun"))
                .allSatisfy(s -> assertThat(s.kind()).isEqualTo(Kind.QUESTION));
    }

    @Test
    @DisplayName("a duplicate id, a label over 40 characters and a tool name without its module each fail")
    void badStartersFail() {
        AiStarter ok = starter("a", "Fine", "core.holidays");
        assertThatThrownBy(() -> new AiStarterRegistry(List.of(ok, starter("a", "Again", "core.holidays"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("used twice");
        assertThatThrownBy(() -> new AiStarterRegistry(List.of(starter("b", "x".repeat(41), "core.holidays"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("label");
        assertThatThrownBy(() -> new AiStarterRegistry(List.of(starter("c", "Fine", "holidays"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("module.verb_noun");
        assertThatThrownBy(() -> new AiStarterRegistry(List.of(starter("d", "Fine", "billing.invoices"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a tool name no part has built yet is not an error")
    void unbuiltToolIsFine() {
        assertThat(new AiStarterRegistry(List.of(starter("e", "Later", "core.not_built_yet"))).all())
                .hasSize(1);
    }
}
