package com.infinevo.shared.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-12.2 spec section 7 & section 9 — every @RestController in hrms and payroll must carry @RequiresModule;
 * no @RestController in core may carry @RequiresModule.
 */
class EntitlementCoverageIT {

    @Test
    @DisplayName("Every @RestController in hrms and payroll carries @RequiresModule")
    void everyModuleControllerCarriesRequiresModule() throws IOException {
        Path backendRoot = findBackendRoot();
        List<Path> moduleDirs =
                List.of(backendRoot.resolve("hrms/src/main/java"), backendRoot.resolve("payroll/src/main/java"));

        List<String> unannotated = new ArrayList<>();
        for (Path dir : moduleDirs) {
            if (!Files.exists(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dir)) {
                files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    try {
                        String content = Files.readString(p);
                        if (content.contains("@RestController") && !content.contains("@RequiresModule")) {
                            unannotated.add(p.getFileName().toString());
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }

        assertThat(unannotated)
                .as("All controllers in hrms and payroll must carry @RequiresModule")
                .isEmpty();
    }

    @Test
    @DisplayName("No @RestController in core carries @RequiresModule (core is available to all tenants)")
    void noCoreControllerCarriesRequiresModule() throws IOException {
        Path backendRoot = findBackendRoot();
        Path coreDir = backendRoot.resolve("core/src/main/java");
        if (!Files.exists(coreDir)) {
            return;
        }

        List<String> wronglyAnnotated = new ArrayList<>();
        try (Stream<Path> files = Files.walk(coreDir)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    String content = Files.readString(p);
                    if (content.contains("@RestController") && content.contains("@RequiresModule")) {
                        wronglyAnnotated.add(p.getFileName().toString());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertThat(wronglyAnnotated)
                .as("Core controllers must not carry @RequiresModule: every tenant holds all core capabilities")
                .isEmpty();
    }

    private static Path findBackendRoot() {
        Path current = Paths.get("").toAbsolutePath();
        if (current.endsWith("shared")) {
            return current.getParent();
        }
        if (Files.exists(current.resolve("code/backend"))) {
            return current.resolve("code/backend");
        }
        if (Files.exists(current.resolve("shared"))) {
            return current;
        }
        return current.resolve("code/backend");
    }
}
