package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.ai.help.HelpLibrary;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-114.13: a sub-route is linkable only when its parent is in the caller's own feed. */
class AiSubRoutesTest {

    private final AiSubRoutes subRoutes = new AiSubRoutes(new HelpLibrary());

    @Test
    @DisplayName("the shipped list: /employees/new needs /employees; five pages sit under payroll settings")
    void allowedFor() {
        assertThat(subRoutes.allowedFor(Set.of("/employees"))).containsExactly("/employees/import", "/employees/new");
        assertThat(subRoutes.allowedFor(Set.of("/me")))
                .doesNotContain("/employees/new")
                .contains("/me/leave");
        assertThat(subRoutes.allowedFor(Set.of("/payroll/settings")))
                .containsExactly(
                        "/payroll/settings/epf",
                        "/payroll/settings/esi",
                        "/payroll/settings/pay-schedule",
                        "/payroll/settings/professional-tax",
                        "/payroll/settings/tax-declaration");
        assertThat(subRoutes.allowedFor(Set.of())).isEmpty();
        assertThat(subRoutes.allowedFor(null)).isEmpty();
    }
}
