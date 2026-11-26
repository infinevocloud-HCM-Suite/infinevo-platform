package com.infinevo.core.ai;

import com.infinevo.core.ai.help.HelpAccess;
import com.infinevo.core.ai.help.HelpLibrary;
import com.infinevo.core.ai.help.HelpViewer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Screens of the caller's own menu (W-114.13): which one a client path is on, and which ones match a few words.
 *
 * <p>Both read only the leaf screens of the caller's navigation feed ({@link HelpViewer#leaves()}), which already left
 * out every item whose module the customer lacks or whose action the caller does not hold. So a path the caller may
 * not open resolves to nothing, and {@link #find} cannot reveal that a screen exists (rules 2 and 3). The client's path
 * is only compared; what comes back is the menu item's own path and name.
 */
@Component
public class AiScreenResolver {

    /** At most this many screens from {@link #find}. */
    static final int MAX_FOUND = 5;

    private static final Set<String> MODULE_WORDS = Set.of("core", "hrms", "payroll", "nav");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "the", "to", "of", "for", "in", "on", "me", "i", "is", "it", "take", "go", "open", "show",
            "where", "page", "screen", "please", "can", "how", "do", "find", "get", "see");

    private final HelpLibrary library;
    private final HelpAccess access;

    public AiScreenResolver(HelpLibrary library, HelpAccess access) {
        this.library = Objects.requireNonNull(library, "library must not be null");
        this.access = Objects.requireNonNull(access, "access must not be null");
    }

    /** The screen the client path is on, for the current caller. */
    public Optional<AiScreen> resolve(String path) {
        return resolve(path, access.currentViewer());
    }

    /**
     * The exact leaf whose path is {@code path}; else the leaf whose path is the longest prefix ending at a {@code /}
     * boundary ({@code /payroll/runs/123} gives Payroll runs, {@code /payroll/runsX} gives nothing). Empty for a path
     * that is missing, does not start with {@code /}, or is over 200 characters.
     */
    public Optional<AiScreen> resolve(String path, HelpViewer viewer) {
        String asked = AiMessageContext.usable(path);
        if (asked == null || viewer == null) {
            return Optional.empty();
        }
        Map.Entry<String, String> best = null;
        for (Map.Entry<String, String> leaf : viewer.leaves().entrySet()) {
            String own = leaf.getValue();
            if (own.equals(asked)) {
                return Optional.of(screen(leaf));
            }
            boolean under = asked.startsWith(own.endsWith("/") ? own : own + "/");
            if (under && (best == null || own.length() > best.getValue().length())) {
                best = leaf;
            }
        }
        return Optional.ofNullable(best).map(this::screen);
    }

    /** Up to five of the current caller's screens that match the words, best first; none when nothing matches. */
    public List<AiScreen> find(String query) {
        return find(query, access.currentViewer());
    }

    List<AiScreen> find(String query, HelpViewer viewer) {
        Set<String> words = words(query);
        words.removeAll(STOP_WORDS);
        if (words.isEmpty() || viewer == null) {
            return List.of();
        }
        List<Scored> scored = new ArrayList<>();
        int order = 0;
        for (Map.Entry<String, String> leaf : viewer.leaves().entrySet()) {
            AiScreen screen = screen(leaf);
            Set<String> title = words(screen.title());
            Set<String> key = words(leaf.getKey());
            key.removeAll(MODULE_WORDS);
            Set<String> path = words(leaf.getValue());
            int score = 0;
            for (String word : words) {
                if (title.contains(word)) {
                    score += 3;
                }
                if (key.contains(word)) {
                    score += 1;
                }
                if (path.contains(word)) {
                    score += 1;
                }
            }
            if (score > 0) {
                scored.add(new Scored(screen, score, order));
            }
            order++;
        }
        return scored.stream()
                .sorted(Comparator.comparingInt(Scored::score).reversed().thenComparingInt(Scored::order))
                .limit(MAX_FOUND)
                .map(Scored::screen)
                .toList();
    }

    private AiScreen screen(Map.Entry<String, String> leaf) {
        String name = library.screenNames().get(leaf.getKey());
        return new AiScreen(name != null ? name : titleFromKey(leaf.getKey()), leaf.getValue());
    }

    /**
     * A name for a menu key that {@code help/screens.json} does not name (the {@code app} test makes that rare): the
     * module word dropped, {@code .} and {@code _} as spaces, the first letter a capital - {@code core.leave.types}
     * gives "Leave types".
     */
    static String titleFromKey(String key) {
        List<String> parts = new ArrayList<>(Arrays.asList(key.split("\\.")));
        if (parts.size() > 1 && MODULE_WORDS.contains(parts.get(0))) {
            parts.remove(0);
        }
        String text = String.join(" ", parts).replace('_', ' ').strip();
        return text.isEmpty() ? key : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Lower-case words of letters and digits. */
    private static Set<String> words(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) {
            return out;
        }
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!word.isEmpty()) {
                out.add(word);
            }
        }
        return out;
    }

    private record Scored(AiScreen screen, int score, int order) {}
}
