package com.infinevo.core.ai.help;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** W-114.13: the fixed sub-route list stops the application when an entry could hold an id or leave its parent. */
class SubRoutesCheckTest {

    @TempDir
    Path dir;

    private HelpLibrary load(String json) throws Exception {
        Files.writeString(dir.resolve("sub-routes.json"), json);
        return new HelpLibrary(
                new PathMatchingResourcePatternResolver(),
                "file:" + dir.toAbsolutePath().toString().replace('\\', '/') + "/none-*.md",
                "file:" + dir.toAbsolutePath().toString().replace('\\', '/') + "/sub-routes.json");
    }

    private static String entry(String path, String parent) {
        return "{\"path\":\"" + path + "\",\"parent\":\"" + parent + "\"}";
    }

    @Test
    @DisplayName("a good list loads")
    void goodList() throws Exception {
        assertThat(load("[" + entry("/employees/new", "/employees") + "]").subRoutes())
                .containsEntry("/employees/new", "/employees");
    }

    @Test
    @DisplayName("refused: outside its parent, a :id segment, a {id} segment, a digits-only segment, a duplicate")
    void refused() {
        assertThatThrownBy(() -> load("[" + entry("/users/new", "/employees") + "]"))
                .hasMessageContaining("is not under its parent");
        assertThatThrownBy(() -> load("[" + entry("/employees/new", "/employeesX") + "]"))
                .hasMessageContaining("is not under its parent");
        assertThatThrownBy(() -> load("[" + entry("/employees/:id", "/employees") + "]"))
                .hasMessageContaining("may hold only letters");
        assertThatThrownBy(() -> load("[" + entry("/employees/{id}", "/employees") + "]"))
                .hasMessageContaining("may hold only letters");
        assertThatThrownBy(() -> load("[" + entry("/payroll/runs/123", "/payroll/runs") + "]"))
                .hasMessageContaining("digits only");
        assertThatThrownBy(() -> load("[" + entry("/employees/new", "/employees") + ","
                        + entry("/employees/new", "/employees") + "]"))
                .hasMessageContaining("listed twice");
    }

    @Test
    @DisplayName("more than 40 entries is refused")
    void tooMany() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 41; i++) {
            json.append(i == 0 ? "" : ",")
                    .append(entry("/employees/page-" + (char) ('a' + i / 26) + (char) ('a' + i % 26), "/employees"));
        }
        json.append(']');

        assertThatThrownBy(() -> load(json.toString())).hasMessageContaining("more than 40");
    }
}
