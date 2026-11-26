package com.infinevo.core.ai.help;

import java.util.Set;

/**
 * What decides which articles one caller may see (W-114.11, "Who may see an article"): the paths in the caller's
 * navigation feed, the actions they hold, and their role codes (ranking only).
 */
public record HelpViewer(Set<String> screens, Set<String> actions, Set<String> roles) {

    public HelpViewer {
        screens = screens == null ? Set.of() : Set.copyOf(screens);
        actions = actions == null ? Set.of() : Set.copyOf(actions);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /** Check 1: the article's screen is in the feed; check 2: its extra action, when set, is held. */
    public boolean maySee(HelpArticle article) {
        return screens.contains(article.screen()) && (article.action() == null || actions.contains(article.action()));
    }

    /** Whether one of the caller's roles is in the article's {@code roles}. */
    public boolean writtenFor(HelpArticle article) {
        return article.roles().stream().anyMatch(roles::contains);
    }
}
