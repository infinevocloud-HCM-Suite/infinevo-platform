package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.ai.help.HelpAccess;
import com.infinevo.core.ai.help.HelpLibrary;
import com.infinevo.core.ai.help.HelpViewer;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiSystemPromptTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private final HelpLibrary library = new HelpLibrary();

    @Test
    @DisplayName("an employee's prompt lists apply-leave, not leave-types, and holds no article body")
    void employeeCatalogue() {
        HelpViewer employee =
                new HelpViewer(Set.of("/me", "/hrms/attendance"), Set.of("core.leave.read_own"), Set.of("employee"));

        String prompt = new AiSystemPrompt(library, HelpAccess.fixed(employee)).build(TODAY, "Asha");

        assertThat(prompt).contains("Help articles you can read:");
        assertThat(prompt).contains("apply-leave | Apply for leave | ");
        assertThat(prompt).doesNotContain("leave-types");
        assertThat(prompt).doesNotContain("Click Apply for Leave");
        assertThat(prompt).contains(AiSystemPrompt.HELP_RULE);
        assertThat(prompt).contains(AiSystemPrompt.ROLE_RULE);
    }

    @Test
    @DisplayName("HR's prompt lists leave-types with its title and summary")
    void hrCatalogue() {
        HelpViewer hr = new HelpViewer(Set.of("/leave/types"), Set.of(), Set.of("hr"));

        String prompt = new AiSystemPrompt(library, HelpAccess.fixed(hr)).build(TODAY, null);

        assertThat(prompt).contains("leave-types | Create a leave type | Add a leave type");
    }

    @Test
    @DisplayName("a caller who may see no article gets (none); no help wired adds no block")
    void emptyAndUnwired() {
        HelpViewer nobody = new HelpViewer(Set.of(), Set.of(), Set.of());

        assertThat(new AiSystemPrompt(library, HelpAccess.fixed(nobody)).build(TODAY, null))
                .contains("Help articles you can read:\n(none)");
        assertThat(new AiSystemPrompt().build(TODAY, null)).doesNotContain("Help articles you can read:");
    }
}
