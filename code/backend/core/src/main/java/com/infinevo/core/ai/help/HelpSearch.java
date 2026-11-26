package com.infinevo.core.ai.help;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The fallback keyword search over help articles (W-114.11, "The search rule"). A pure function of the query, the
 * articles the caller may see and the caller's role codes.
 *
 * <p>Per query word: +5 if a keyword contains it, +3 if the title contains it, +1 if the body contains it. When an
 * article scores above 0, +2 once if one of the caller's roles is in its {@code roles}. Articles above 0, highest
 * first, ties by id, at most {@value #MAX_RESULTS}.
 */
public final class HelpSearch {

    static final int MAX_RESULTS = 5;

    /** Forty words too common to say what an article is about. */
    static final Set<String> STOP_WORDS = Set.of(
            "how", "the", "can", "and", "for", "you", "are", "what", "where", "when", "who", "why", "does", "did",
            "with", "this", "that", "there", "here", "from", "into", "about", "have", "has", "had", "was", "were",
            "will", "would", "should", "could", "want", "need", "please", "help", "some", "any", "our", "your", "its");

    private HelpSearch() {}

    public static List<HelpArticle> search(String query, Collection<HelpArticle> articles, Set<String> roles) {
        Set<String> words = words(query);
        if (words.isEmpty()) {
            return List.of();
        }
        Map<HelpArticle, Integer> scores =
                articles.stream().collect(Collectors.toMap(a -> a, a -> score(a, words, roles), (a, b) -> a));
        return scores.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.comparing((Map.Entry<HelpArticle, Integer> e) -> -e.getValue())
                        .thenComparing(e -> e.getKey().id()))
                .limit(MAX_RESULTS)
                .map(Map.Entry::getKey)
                .toList();
    }

    static Set<String> words(String query) {
        Set<String> out = new LinkedHashSet<>();
        if (query == null) {
            return out;
        }
        for (String word : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= 3 && !STOP_WORDS.contains(word)) {
                out.add(word);
            }
        }
        return out;
    }

    static int score(HelpArticle article, Set<String> words, Set<String> roles) {
        String title = article.title().toLowerCase(Locale.ROOT);
        String body = article.body().toLowerCase(Locale.ROOT);
        int score = 0;
        for (String word : words) {
            if (article.keywords().stream().anyMatch(k -> k.contains(word))) {
                score += 5;
            }
            if (title.contains(word)) {
                score += 3;
            }
            if (body.contains(word)) {
                score += 1;
            }
        }
        if (score > 0 && roles != null && article.roles().stream().anyMatch(roles::contains)) {
            score += 2;
        }
        return score;
    }
}
