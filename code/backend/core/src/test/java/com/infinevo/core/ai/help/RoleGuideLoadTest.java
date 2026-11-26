package com.infinevo.core.ai.help;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-114.12: the shipped role guides load, and each startup check stops a broken guide. */
class RoleGuideLoadTest {

    private static final Set<String> ARTICLES = Set.of("leave-types");
    private static final Set<String> SCREENS = Set.of("core.leave.types", "core.leave.requests");

    private static RoleGuide parse(String file, String text) {
        return HelpLibrary.parseGuide(file, text, ARTICLES, SCREENS);
    }

    private static String guide(String role, String body) {
        return "---\nrole: " + role + "\ntitle: HR\n---\n" + body;
    }

    @Test
    @DisplayName("the six shipped guides load, every line names a named screen, every article is loaded")
    void shippedGuidesLoad() {
        HelpLibrary library = new HelpLibrary();

        assertThat(library.guides().keySet())
                .containsExactlyInAnyOrder("employee", "manager", "hr", "payroll-officer", "tenant-admin", "finance");
        assertThat(library.guides().values()).allSatisfy(g -> {
            assertThat(g.sections()).isNotEmpty();
            g.sections().forEach(s -> s.lines().forEach(l -> {
                assertThat(library.screenNames()).containsKey(l.screen());
                assertThat(l.text()).doesNotContain("{").doesNotContain("}");
                if (l.article() != null) {
                    assertThat(library.all()).extracting(HelpArticle::id).contains(l.article());
                }
            }));
        });
        assertThat(library.guide("hr").orElseThrow().headings()).contains("Every day", "Once a year");
        assertThat(library.screenNames()).containsEntry("core.leave.types", "Leave types");
    }

    @Test
    @DisplayName("a good guide: markers are taken out of the words, the screen and article kept")
    void goodGuide() {
        RoleGuide g = parse(
                "hr.md",
                guide(
                        "hr",
                        "## Every year\n- Review   the leave types. {screen:core.leave.types} {article:leave-types}\n"));

        assertThat(g.role()).isEqualTo("hr");
        assertThat(g.title()).isEqualTo("HR");
        RoleGuide.Line line = g.sections().get(0).lines().get(0);
        assertThat(line.text()).isEqualTo("Review the leave types.");
        assertThat(line.screen()).isEqualTo("core.leave.types");
        assertThat(line.article()).isEqualTo("leave-types");
        assertThat(line.action()).isNull();
        RoleGuide marked = parse(
                "hr.md", guide("hr", "## Day\n- Publish. {screen:core.leave.types} {action:core.holiday.manage}\n"));
        assertThat(marked.sections().get(0).lines().get(0).action()).isEqualTo("core.holiday.manage");
        assertThat(marked.sections().get(0).lines().get(0).text()).isEqualTo("Publish.");
    }

    @Test
    @DisplayName("each startup check fails, naming the file")
    void brokenGuidesStopStartup() {
        assertThatThrownBy(() -> parse("hr.md", guide("hr", "## Day\n- Check requests.\n")))
                .hasMessageContaining("hr.md")
                .hasMessageContaining("has no {screen:}");
        assertThatThrownBy(() -> parse("cook.md", guide("cook", "## Day\n- Cook. {screen:core.leave.types}\n")))
                .hasMessageContaining("unknown role code 'cook'");
        assertThatThrownBy(() -> parse("other.md", guide("hr", "## Day\n- A. {screen:core.leave.types}\n")))
                .hasMessageContaining("must equal the file name");
        assertThatThrownBy(() -> parse("hr.md", guide("hr", "## Day\n- A. {screen:core.leave.types} {article:nope}\n")))
                .hasMessageContaining("article 'nope' is not a help article");
        assertThatThrownBy(() -> parse("hr.md", guide("hr", "## Day\n- A. {screen:core.unnamed}\n")))
                .hasMessageContaining("has no name in help/screens.json");
        assertThatThrownBy(() -> parse(
                        "hr.md", guide("hr", "## Day\n- A. {screen:core.leave.types} {screen:core.leave.requests}\n")))
                .hasMessageContaining("more than one {screen:}");
        assertThatThrownBy(() -> parse("hr.md", guide("hr", "## Day\n- A. {screen:core.leave.types} {link:x}\n")))
                .hasMessageContaining("unknown marker");
        assertThatThrownBy(
                        () -> parse("hr.md", guide("hr", "## Day\n- A. {screen:core.leave.types} {action:Bad Code}\n")))
                .hasMessageContaining("is not an action code");
        assertThatThrownBy(() -> parse("hr.md", guide("hr", "- A. {screen:core.leave.types}\n")))
                .hasMessageContaining("before the first ## heading");
        assertThatThrownBy(() -> parse("hr.md", guide("hr", "## Day\nJust words.\n")))
                .hasMessageContaining("neither a ## heading nor a - duty");
        assertThatThrownBy(() -> parse("hr.md", guide("hr", "## Day\n## Night\n- A. {screen:core.leave.types}\n")))
                .hasMessageContaining("section 'Day' has no duty line");
        assertThatThrownBy(() -> parse("hr.md", "## Day\n- A. {screen:core.leave.types}\n"))
                .hasMessageContaining("has no front matter");
    }

    @Test
    @DisplayName("a guide over 4,000 characters stops startup")
    void tooLong() {
        String line = "- " + "x".repeat(100) + " {screen:core.leave.types}\n";
        String body = "## Day\n" + line.repeat(40);

        assertThatThrownBy(() -> parse("hr.md", guide("hr", body))).hasMessageContaining("at most 4000");
    }
}
