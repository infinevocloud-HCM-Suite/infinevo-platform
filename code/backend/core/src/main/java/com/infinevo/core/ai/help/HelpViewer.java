package com.infinevo.core.ai.help;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Set;

/**
 * What decides which articles one caller may see (W-114.11, "Who may see an article"): the paths in the caller's
 * navigation feed, the actions they hold, their role codes (ranking only) and the modules the customer holds.
 */
public record HelpViewer(Set<String> screens, Set<String> actions, Set<String> roles, Set<PlatformModule> modules) {

    public HelpViewer {
        screens = screens == null ? Set.of() : Set.copyOf(screens);
        actions = actions == null ? Set.of() : Set.copyOf(actions);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        modules = modules == null ? Set.of() : Set.copyOf(modules);
    }

    /** A viewer whose customer holds no module. */
    public HelpViewer(Set<String> screens, Set<String> actions, Set<String> roles) {
        this(screens, actions, roles, Set.of());
    }

    /**
     * Check 1: the article's screen is in the feed; check 2: its extra action, when set, is held; check 3: its module,
     * when set, is held by the customer. The feed's actions are not filtered by module, so check 3 is what hides the
     * payslip article from a customer without Payroll.
     */
    public boolean maySee(HelpArticle article) {
        return screens.contains(article.screen())
                && (article.action() == null || actions.contains(article.action()))
                && (article.module() == null || modules.contains(article.module()));
    }

    /** Whether one of the caller's roles is in the article's {@code roles}. */
    public boolean writtenFor(HelpArticle article) {
        return article.roles().stream().anyMatch(roles::contains);
    }
}
