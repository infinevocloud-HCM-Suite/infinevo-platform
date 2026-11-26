package com.infinevo.core.ai.help;

import java.util.List;

/**
 * The articles one caller may see, as the system prompt lists them (W-114.11, "How an article is found").
 *
 * @param lines id, title and summary of each visible article, sorted by id
 * @param more true when more than {@link HelpLibrary#CATALOGUE_CAP} were visible and only the best-fitting are listed
 */
public record HelpCatalogue(List<Line> lines, boolean more) {

    public HelpCatalogue {
        lines = List.copyOf(lines);
    }

    public static HelpCatalogue empty() {
        return new HelpCatalogue(List.of(), false);
    }

    public record Line(String id, String title, String summary) {

        /** {@code id | title | summary}. */
        public String text() {
            return id + " | " + title + " | " + summary;
        }
    }
}
