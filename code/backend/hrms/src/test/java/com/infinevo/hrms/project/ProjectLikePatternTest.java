package com.infinevo.hrms.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectLikePatternTest {

    @Test
    @DisplayName("No search text is '%', never null, so the bound parameter always has a type")
    void noSearchMatchesEverything() {
        assertThat(ProjectLikePattern.contains(null)).isEqualTo("%");
        assertThat(ProjectLikePattern.contains("")).isEqualTo("%");
        assertThat(ProjectLikePattern.contains("   ")).isEqualTo("%");
    }

    @Test
    @DisplayName("Search text is trimmed, lower-cased and wrapped for a contains match")
    void containsPattern() {
        assertThat(ProjectLikePattern.contains("  Payroll ")).isEqualTo("%payroll%");
    }

    @Test
    @DisplayName("%, _ and the escape character typed by the user are escaped so they match themselves")
    void wildcardsAreEscaped() {
        assertThat(ProjectLikePattern.contains("100%")).isEqualTo("%100!%%");
        assertThat(ProjectLikePattern.contains("a_b")).isEqualTo("%a!_b%");
        assertThat(ProjectLikePattern.contains("wow!")).isEqualTo("%wow!!%");
    }
}
