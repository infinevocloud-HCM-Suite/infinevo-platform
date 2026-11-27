package com.infinevo.core.ai.help;

import static com.infinevo.core.ai.help.TestArticles.article;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HelpCatalogueTest {

    private final HelpLibrary shipped = new HelpLibrary();

    /** Every screen the shipped articles name, and every action they need: what a tenant admin with all modules sees. */
    private HelpViewer everything() {
        Set<String> screens = new HashSet<>();
        Set<String> actions = new HashSet<>();
        for (HelpArticle a : shipped.all()) {
            screens.add(a.screen());
            if (a.action() != null) {
                actions.add(a.action());
            }
        }
        return new HelpViewer(
                screens, actions, Set.of("tenant-admin"), Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL));
    }

    @Test
    @DisplayName("HR's catalogue holds leave-types; an employee's does not")
    void hrAndEmployee() {
        HelpViewer hr = new HelpViewer(Set.of("/leave/types", "/leave/requests", "/employees"), Set.of(), Set.of("hr"));
        HelpViewer employee = new HelpViewer(
                Set.of("/me", "/hrms/attendance", "/holidays"),
                Set.of("payroll.payslip.read_own", "core.leave.read_own"),
                Set.of("employee"),
                Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL));

        assertThat(shipped.catalogueFor(hr).lines())
                .extracting(HelpCatalogue.Line::id)
                .contains("leave-types");
        assertThat(shipped.catalogueFor(employee).lines())
                .extracting(HelpCatalogue.Line::id)
                .contains("apply-leave", "view-payslip", "clock-in-out", "holidays")
                .doesNotContain("leave-types", "tax-declaration");
    }

    @Test
    @DisplayName(
            "a customer without Payroll: no Payroll screen line, and no payslip, tax or salary line on a shared screen")
    void noPayroll() {
        Set<String> screens = new HashSet<>(everything().screens());
        screens.removeIf(s -> s.startsWith("/payroll"));
        // The feed's actions are not filtered by module, so a Payroll action can still be held.
        HelpViewer noPayroll =
                new HelpViewer(screens, everything().actions(), Set.of("tenant-admin"), Set.of(PlatformModule.HRMS));

        assertThat(shipped.catalogueFor(noPayroll).lines())
                .extracting(HelpCatalogue.Line::id)
                .contains("apply-leave", "add-employee")
                .doesNotContain(
                        "run-payroll",
                        "salary-components",
                        "statutory-settings",
                        "view-payslip",
                        "tax-declaration",
                        "assign-salary");
    }

    @Test
    @DisplayName("lines are sorted by id and hold only id, title and summary; all 58 are under 8,000 characters")
    void shapeAndSize() {
        HelpCatalogue all = shipped.catalogueFor(everything());

        assertThat(all.lines()).hasSize(58);
        assertThat(all.more()).isFalse();
        List<String> ids = all.lines().stream().map(HelpCatalogue.Line::id).toList();
        assertThat(ids).isSorted();
        HelpCatalogue.Line line = all.lines().get(0);
        HelpArticle source = shipped.all().stream()
                .filter(a -> a.id().equals(line.id()))
                .findFirst()
                .orElseThrow();
        assertThat(line.text()).isEqualTo(source.id() + " | " + source.title() + " | " + source.summary());
        int size = all.lines().stream().mapToInt(l -> l.text().length() + 1).sum();
        assertThat(size).isLessThan(8_000);
    }

    @Test
    @DisplayName("61 visible articles give 60 lines, those written for the caller's role first, and the more flag")
    void capAtSixty() {
        List<HelpArticle> many = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            many.add(article(String.format("a-%02d", i), "/x", null, null, List.of("employee"), List.of(), "T", "B"));
        }
        many.add(article("z-hr", "/x", null, null, List.of("hr"), List.of(), "T", "B"));
        HelpLibrary library = HelpLibrary.of(many);

        HelpCatalogue hr = library.catalogueFor(new HelpViewer(Set.of("/x"), Set.of(), Set.of("hr")));

        assertThat(hr.lines()).hasSize(60);
        assertThat(hr.more()).isTrue();
        assertThat(hr.lines())
                .extracting(HelpCatalogue.Line::id)
                .contains("z-hr")
                .doesNotContain("a-59");
        assertThat(hr.lines()).extracting(HelpCatalogue.Line::id).isSorted();
    }
}
