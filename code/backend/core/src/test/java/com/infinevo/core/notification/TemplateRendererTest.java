package com.infinevo.core.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-20.1 spec section 7 — placeholders substituted; a missing one fails loudly rather than rendering
 * {@code ${name}}; HTML escaped for the email channel.
 */
class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    @DisplayName("Placeholders are substituted, repeated ones every time")
    void substitutes() {
        assertThat(renderer.render(
                        "${name} applied for ${leave_type}; ${name} is away.",
                        Map.of("name", "Asha", "leave_type", "Casual leave"),
                        false))
                .isEqualTo("Asha applied for Casual leave; Asha is away.");
        assertThat(renderer.render("From ${from_date}", Map.of("from_date", LocalDate.of(2026, 10, 2)), false))
                .isEqualTo("From 2026-10-02");
    }

    @Test
    @DisplayName("A placeholder with no value fails, naming it - it never reaches a person as ${...}")
    void missingValueFails() {
        assertThatThrownBy(() -> renderer.render("Hello ${employee_name}", Map.of(), false))
                .isInstanceOfSatisfying(TemplateRenderer.MissingValueException.class, e -> assertThat(e.placeholder())
                        .isEqualTo("employee_name"));
    }

    @Test
    @DisplayName("Email bodies escape every value; in-app bodies do not, the screen escapes them")
    void htmlIsEscapedForEmail() {
        Map<String, Object> values = Map.of("reason", "<script>alert('x')</script> & \"more\"");

        assertThat(renderer.render("<p>${reason}</p>", values, true))
                .isEqualTo("<p>&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;more&quot;</p>");
        assertThat(renderer.render("${reason}", values, false)).isEqualTo("<script>alert('x')</script> & \"more\"");
    }

    @Test
    @DisplayName("A value holding $ or \\ is inserted literally, not read as a regex group")
    void specialCharactersInValues() {
        assertThat(renderer.render("Total ${amount}", Map.of("amount", "$1\\2"), false))
                .isEqualTo("Total $1\\2");
    }

    @Test
    @DisplayName("A subject loses its line breaks - a header cannot be injected through a value")
    void subjectHasNoLineBreaks() {
        assertThat(renderer.renderSubject("Leave for ${name}", Map.of("name", "Asha\r\nBcc: someone@evil.test")))
                .isEqualTo("Leave for Asha Bcc: someone@evil.test");
    }

    @Test
    @DisplayName("Placeholders are listed, and a malformed one is detected")
    void placeholdersAndMalformed() {
        assertThat(TemplateRenderer.placeholdersIn("${a} ${b_1} ${a}")).containsExactly("a", "b_1");
        assertThat(TemplateRenderer.hasMalformedPlaceholder("${ok} fine")).isFalse();
        assertThat(TemplateRenderer.hasMalformedPlaceholder("Hello ${Name}")).isTrue();
        assertThat(TemplateRenderer.hasMalformedPlaceholder("Hello ${name")).isTrue();
        assertThat(TemplateRenderer.hasMalformedPlaceholder("costs $5")).isFalse();
    }
}
