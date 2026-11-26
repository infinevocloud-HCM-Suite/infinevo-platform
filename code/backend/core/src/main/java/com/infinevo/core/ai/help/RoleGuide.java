package com.infinevo.core.ai.help;

import java.util.List;

/**
 * One role's duty guide (W-114.12), shipped as {@code help/roles/<role code>.md}: what that role is for, in sections
 * such as "Every day", each line naming the one screen it needs and, when there is one, the how-to article.
 *
 * @param role the role code, for example {@code hr}
 * @param title the role's plain name, for example {@code HR}
 * @param sections in file order
 */
public record RoleGuide(String role, String title, List<Section> sections) {

    public RoleGuide {
        sections = List.copyOf(sections);
    }

    /** The section headings, in order. */
    public List<String> headings() {
        return sections.stream().map(Section::heading).toList();
    }

    /** A heading and its duty lines. */
    public record Section(String heading, List<Line> lines) {

        public Section {
            lines = List.copyOf(lines);
        }
    }

    /**
     * One duty.
     *
     * @param text the words, with the markers taken out
     * @param screen the menu key the duty needs, for example {@code core.leave.requests}
     * @param article the how-to article id, or {@code null}
     * @param action an action the duty needs beyond opening the screen (approving on a screen anyone with read can
     *     open), or {@code null}; never returned to the model
     */
    public record Line(String text, String screen, String article, String action) {

        /** A line needing no extra action. */
        public Line(String text, String screen, String article) {
            this(text, screen, article, null);
        }
    }
}
