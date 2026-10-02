package com.infinevo.hrms.project;

import java.util.Locale;

/**
 * Builds the value bound to a {@code LIKE ... ESCAPE '!'} clause from what a user typed.
 *
 * <p>The pattern is always a non-null string. An optional filter passed to PostgreSQL as an untyped
 * {@code null} is bound as {@code bytea}, and {@code lower(bytea)} does not exist, so "no search text" is
 * expressed as {@code %}, which matches every name, instead of as {@code null}. The user's own {@code %},
 * {@code _} and the escape character are escaped, so they match themselves and never act as wildcards.
 */
final class ProjectLikePattern {

    static final char ESCAPE = '!';

    private ProjectLikePattern() {}

    static String contains(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.trim()
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
