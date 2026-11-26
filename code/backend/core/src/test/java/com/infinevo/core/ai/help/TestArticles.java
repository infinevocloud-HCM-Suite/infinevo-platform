package com.infinevo.core.ai.help;

import java.util.List;
import java.util.Set;

/** Small articles and viewers for the help tests. */
final class TestArticles {

    private TestArticles() {}

    static HelpArticle article(String id, String screen) {
        return article(id, screen, null, null, List.of("hr"), List.of(id), "Title " + id, "1. Step for " + id + ".");
    }

    static HelpArticle article(
            String id,
            String screen,
            String route,
            String action,
            List<String> roles,
            List<String> keywords,
            String title,
            String body) {
        return new HelpArticle(id, title, "Summary of " + id + ".", roles, screen, route, action, keywords, body);
    }

    static HelpViewer viewer(Set<String> screens, Set<String> actions, Set<String> roles) {
        return new HelpViewer(screens, actions, roles);
    }

    static HelpViewer seesScreens(String... screens) {
        return new HelpViewer(Set.of(screens), Set.of(), Set.of());
    }
}
