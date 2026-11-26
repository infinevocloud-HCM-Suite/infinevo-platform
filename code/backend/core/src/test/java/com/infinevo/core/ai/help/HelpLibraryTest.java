package com.infinevo.core.ai.help;

import static com.infinevo.core.ai.help.TestArticles.article;
import static com.infinevo.core.ai.help.TestArticles.seesScreens;
import static com.infinevo.core.ai.help.TestArticles.viewer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class HelpLibraryTest {

    private static final Map<String, String> SUB_ROUTES = Map.of("/employees/new", "/employees");

    private static final String GOOD = String.join(
            "\n",
            "---",
            "id: add-employee",
            "title: Add an employee",
            "summary: Create one employee.",
            "roles: hr, tenant-admin",
            "screen: /employees",
            "route: /employees/new",
            "keywords: add employee, New Joiner",
            "---",
            "1. Open Employees.",
            "");

    @Test
    @DisplayName("the shipped library loads: 30 articles, each id its file name, six sub-routes")
    void loadsShippedArticles() {
        HelpLibrary library = new HelpLibrary();

        assertThat(library.all()).hasSize(30);
        assertThat(library.all()).extracting(HelpArticle::id).contains("leave-types", "apply-leave", "run-payroll");
        assertThat(library.subRoutes())
                .containsOnlyKeys(
                        "/employees/new",
                        "/employees/import",
                        "/me/leave",
                        "/me/wfh",
                        "/me/payslips",
                        "/me/taxDeclaration",
                        "/payroll/settings/tax-declaration");
        assertThat(library.all()).allSatisfy(a -> {
            assertThat(a.body().length()).isLessThanOrEqualTo(HelpLibrary.MAX_BODY);
            assertThat(a.roles()).isNotEmpty();
        });
    }

    @Test
    @DisplayName("a good file parses every field; keywords are lower case")
    void parsesAGoodFile() {
        HelpArticle a = HelpLibrary.parse("add-employee.md", GOOD.replace("\n", "\r\n"), SUB_ROUTES);

        assertThat(a.id()).isEqualTo("add-employee");
        assertThat(a.title()).isEqualTo("Add an employee");
        assertThat(a.roles()).containsExactly("hr", "tenant-admin");
        assertThat(a.screen()).isEqualTo("/employees");
        assertThat(a.route()).isEqualTo("/employees/new");
        assertThat(a.action()).isNull();
        assertThat(a.keywords()).containsExactly("add employee", "new joiner");
        assertThat(a.body()).isEqualTo("1. Open Employees.");
    }

    @Test
    @DisplayName("startup checks: each broken file is refused, naming the file and the problem")
    void startupChecks() {
        assertRefused("1. no front matter", "add-employee.md", "has no front matter");
        assertRefused(GOOD.replace("title: Add an employee\n", ""), "add-employee.md", "required field 'title'");
        assertRefused(GOOD.replace("summary: Create one employee.\n", "summary:\n"), "add-employee.md", "'summary'");
        assertRefused(GOOD, "other-name.md", "must equal the file name");
        assertRefused(GOOD.replace("roles: hr, tenant-admin", "roles: hr, boss"), "add-employee.md", "'boss'");
        assertRefused(GOOD.replace("/employees/new", "/employees/old"), "add-employee.md", "route '/employees/old'");
        assertRefused(GOOD.replace("screen: /employees", "screen: /users"), "add-employee.md", "with parent /users");
        assertRefused(GOOD + "x".repeat(HelpLibrary.MAX_BODY), "add-employee.md", "body is");
        assertRefused(GOOD.replace("title: Add an employee", "title: " + "t".repeat(81)), "add-employee.md", "title");
        assertRefused(GOOD.replace("keywords:", "colour: red\nkeywords:"), "add-employee.md", "'colour'");
        assertRefused(
                GOOD.replace("keywords:", "module: core\nkeywords:"), "add-employee.md", "must be hrms or payroll");
    }

    @Test
    @DisplayName("fixture folder: a good folder loads; the same id in two files stops the load")
    void loadsFixturesAndRefusesDuplicates() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        HelpLibrary good = new HelpLibrary(
                resolver, "classpath*:help-fixtures/good/*.md", "classpath*:help-fixtures/good/sub-routes.json");
        assertThat(good.all()).extracting(HelpArticle::id).containsExactly("alpha", "beta");

        assertThatThrownBy(() -> new HelpLibrary(
                        resolver,
                        "classpath*:help-fixtures/duplicate/*/beta.md",
                        "classpath*:help-fixtures/good/sub-routes.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("beta.md")
                .hasMessageContaining("used twice");
    }

    @Test
    @DisplayName("articleIdForScreen: the screen's own article before one with a route or an action; nothing else")
    void articleIdForScreen() {
        HelpLibrary library = HelpLibrary.of(List.of(
                article("a-route", "/employees", "/employees/new", null, List.of("hr"), List.of(), "T", "B"),
                article("b-action", "/employees", null, "payroll.salary.manage", List.of("hr"), List.of(), "T", "B"),
                article("z-plain", "/employees", null, null, List.of("hr"), List.of(), "T", "B")));
        HelpViewer all = viewer(Set.of("/employees"), Set.of("payroll.salary.manage"), Set.of());

        assertThat(library.articleIdForScreen("/employees", all)).contains("z-plain");
        assertThat(library.articleIdForScreen("/leave/types", all)).isEmpty();
        assertThat(library.articleIdForScreen("/employees", seesScreens("/leave/types")))
                .isEmpty();
    }

    private static void assertRefused(String text, String file, String problem) {
        assertThatThrownBy(() -> HelpLibrary.parse(file, text, SUB_ROUTES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(file)
                .hasMessageContaining(problem);
    }
}
