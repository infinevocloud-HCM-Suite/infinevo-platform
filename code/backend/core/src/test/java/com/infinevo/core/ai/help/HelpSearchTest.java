package com.infinevo.core.ai.help;

import static com.infinevo.core.ai.help.TestArticles.article;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HelpSearchTest {

    private static HelpArticle a(String id, List<String> roles, List<String> keywords, String title, String body) {
        return article(id, "/x", null, null, roles, keywords, title, body);
    }

    @Test
    @DisplayName("a keyword match beats a title match, which beats a body match")
    void keywordBeatsTitleBeatsBody() {
        HelpArticle body = a("a-body", List.of("hr"), List.of("other"), "Something", "Mentions payslip once.");
        HelpArticle title = a("b-title", List.of("hr"), List.of("other"), "My payslip", "Nothing.");
        HelpArticle keyword = a("c-keyword", List.of("hr"), List.of("payslip download"), "Something", "Nothing.");

        assertThat(HelpSearch.search("payslip", List.of(body, title, keyword), Set.of()))
                .extracting(HelpArticle::id)
                .containsExactly("c-keyword", "b-title", "a-body");
    }

    @Test
    @DisplayName("the role bonus lifts an article written for the caller, but never one that does not match")
    void roleBonus() {
        HelpArticle forHr = a("b-hr", List.of("hr"), List.of(), "Leave", "x");
        HelpArticle forEmployee = a("a-employee", List.of("employee"), List.of(), "Leave", "x");
        HelpArticle unrelated = a("c-hr-other", List.of("hr"), List.of(), "Payroll", "x");

        assertThat(HelpSearch.search("leave", List.of(forHr, forEmployee, unrelated), Set.of("hr")))
                .extracting(HelpArticle::id)
                .containsExactly("b-hr", "a-employee");
    }

    @Test
    @DisplayName("stop words and words under 3 characters are dropped; only those gives nothing")
    void stopWords() {
        HelpArticle how = a("how", List.of("hr"), List.of("how", "the", "do"), "How do the", "how the do it");

        assertThat(HelpSearch.words("How do I add THE leave-type?")).containsExactly("add", "leave", "type");
        assertThat(HelpSearch.search("how do the it", List.of(how), Set.of("hr")))
                .isEmpty();
    }

    @Test
    @DisplayName("nothing above 0 gives an empty list")
    void nothingMatches() {
        assertThat(HelpSearch.search("zzzz", List.of(a("x", List.of("hr"), List.of("leave"), "Leave", "b")), Set.of()))
                .isEmpty();
    }

    @Test
    @DisplayName("at most 5, ties by id")
    void atMostFiveTiesById() {
        List<HelpArticle> many = new ArrayList<>();
        for (String id : List.of("g", "c", "a", "f", "b", "e", "d")) {
            many.add(a(id, List.of("hr"), List.of("leave"), "T", "b"));
        }
        assertThat(HelpSearch.search("leave", many, Set.of()))
                .extracting(HelpArticle::id)
                .containsExactly("a", "b", "c", "d", "e");
    }

    @Test
    @DisplayName("on the shipped library, add leave type as HR finds leave-types first")
    void shippedLeaveTypes() {
        HelpLibrary library = new HelpLibrary();

        assertThat(HelpSearch.search("add leave type", library.all(), Set.of("hr")))
                .extracting(HelpArticle::id)
                .first()
                .isEqualTo("leave-types");
    }
}
