package com.infinevo.core.ai.help;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.List;
import java.util.Objects;

/**
 * One how-to article shipped with the product (W-114.11), read from {@code help/articles/<id>.md}.
 *
 * @param id lower case, digits and {@code -}; the file name without {@code .md}
 * @param title at most 80 characters
 * @param summary one line, at most 160 characters; what the catalogue and the search show
 * @param roles system role codes the article is written for; used to rank, never to decide access
 * @param screen the path of a navigation item; the caller must have it in their feed to see the article
 * @param route a deeper page under {@code screen} from {@code help/sub-routes.json}, or {@code null}
 * @param action one more action the reader must hold, or {@code null}
 * @param keywords words a user might type, lower case
 * @param body the numbered steps, at most 6,000 characters
 * @param module the module the customer must hold, for an article on a shared screen whose tab or panel belongs to
 *     that module (the payslip on {@code /me}, the Salary tab on {@code /employees}); {@code null} for none
 */
public record HelpArticle(
        String id,
        String title,
        String summary,
        List<String> roles,
        String screen,
        String route,
        String action,
        List<String> keywords,
        String body,
        PlatformModule module) {

    public HelpArticle {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        Objects.requireNonNull(screen, "screen must not be null");
        Objects.requireNonNull(body, "body must not be null");
        roles = roles == null ? List.of() : List.copyOf(roles);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    /** An article needing no module. */
    public HelpArticle(
            String id,
            String title,
            String summary,
            List<String> roles,
            String screen,
            String route,
            String action,
            List<String> keywords,
            String body) {
        this(id, title, summary, roles, screen, route, action, keywords, body, null);
    }
}
