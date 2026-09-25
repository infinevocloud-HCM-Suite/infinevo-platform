package com.infinevo.core.notification;

import java.io.Serial;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns a template and an event's values into the text a person reads (W-20.1).
 *
 * <p>Deliberately small: {@code ${name}} placeholders and nothing else — no expressions, no loops, no
 * includes. A template is edited by a tenant administrator (spec section 13, decision 1), and a
 * template language that can call code is one an administrator can use to reach it.
 *
 * <ul>
 *   <li><strong>A missing value fails loudly.</strong> An email reading "Hello ${employee_name}" is
 *       worse than no email: it tells the recipient the system is broken and tells nobody else.
 *   <li><strong>Values are HTML-escaped into an email body.</strong> Bodies carry employee-supplied
 *       text — a leave reason, a name — and the email channel renders HTML (spec section 7).
 *   <li><strong>A subject loses its line breaks.</strong> It becomes a mail header; a value with a
 *       newline in it is how a header is injected.
 * </ul>
 */
@Component
public class TemplateRenderer {

    /** {@code ${name}} — lowercase snake names only, the shape every {@link NotificationEvent} uses. */
    static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-z0-9_]+)}");

    /** Renders a body. {@code html} escapes every value, for the email channel. */
    public String render(String template, Map<String, ?> values, boolean html) {
        return substitute(template, values, html);
    }

    /** Renders a subject: values unescaped, and every line break in the result replaced by a space. */
    public String renderSubject(String template, Map<String, ?> values) {
        return substitute(template, values, false).replaceAll("[\\r\\n]+", " ");
    }

    /** The placeholder names a template uses, in order of first appearance. */
    static Set<String> placeholdersIn(String template) {
        Set<String> names = new LinkedHashSet<>();
        if (template == null) {
            return names;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * True when a template holds {@code ${} that is not a well-formed placeholder — {@code ${Name}},
     * {@code ${ name }}, an unclosed one. It would reach the recipient as literal text.
     */
    static boolean hasMalformedPlaceholder(String template) {
        if (template == null) {
            return false;
        }
        return PLACEHOLDER.matcher(template).replaceAll("").contains("${");
    }

    static String escapeHtml(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String substitute(String template, Map<String, ?> values, boolean html) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = values == null ? null : values.get(name);
            if (value == null) {
                throw new MissingValueException(name);
            }
            String text = String.valueOf(value);
            matcher.appendReplacement(out, Matcher.quoteReplacement(html ? escapeHtml(text) : text));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** A template uses a placeholder the caller supplied no value for. */
    public static class MissingValueException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String placeholder;

        public MissingValueException(String placeholder) {
            super("No value for ${" + placeholder + "}; the notification was not written");
            this.placeholder = placeholder;
        }

        public String placeholder() {
            return placeholder;
        }
    }
}
