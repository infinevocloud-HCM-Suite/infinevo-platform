package com.infinevo.core.ai.help;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.setup.SetupStepCatalogue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-114.16: every setup step has a screen, and a deeper screen is on the fixed sub-route list. */
class SetupStepScreensTest {

    private static final HelpLibrary LIBRARY = new HelpLibrary();

    @Test
    @DisplayName("every step of the catalogue has an entry, and no entry is for a step the catalogue lacks")
    void everyStepHasAScreen() {
        assertThat(SetupStepCatalogue.DEFAULT_STEPS).allSatisfy(step -> assertThat(SetupStepScreens.of(step.code()))
                .as(step.code())
                .isPresent());
        assertThat(SetupStepScreens.all().keySet())
                .allSatisfy(code ->
                        assertThat(SetupStepCatalogue.findByCode(code)).as(code).isPresent());
    }

    @Test
    @DisplayName("every path starts with /; a path below its item is a sub-route whose parent is that item's path")
    void pathsAreScreensOrSubRoutes() {
        SetupStepScreens.all().forEach((code, screen) -> {
            assertThat(screen.path()).as(code).startsWith("/");
            String parent = LIBRARY.subRoutes().get(screen.path());
            if (parent != null) {
                assertThat(screen.path()).as(code).startsWith(parent + "/");
            }
        });
        assertThat(LIBRARY.subRoutes())
                .containsEntry("/payroll/settings/pay-schedule", "/payroll/settings")
                .containsEntry("/payroll/settings/epf", "/payroll/settings")
                .containsEntry("/payroll/settings/esi", "/payroll/settings")
                .containsEntry("/payroll/settings/professional-tax", "/payroll/settings");
    }

    @Test
    @DisplayName("every article named is a shipped article")
    void articlesExist() {
        SetupStepScreens.all().values().stream()
                .filter(s -> s.article() != null)
                .forEach(s -> assertThat(LIBRARY.all())
                        .extracting(HelpArticle::id)
                        .as(s.path())
                        .contains(s.article()));
    }
}
