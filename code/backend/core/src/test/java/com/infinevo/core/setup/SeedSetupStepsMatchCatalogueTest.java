package com.infinevo.core.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.entitlement.PlatformModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-24.1 §8 — the local seed's checklist rows ({@code infra/docker/seed/05-tenant-setup-steps.sql})
 * must mirror {@link SetupStepCatalogue} for each seeded tenant's modules
 * ({@code 04-subscriptions.sql}: Acme PAYROLL only, Globex HRMS and PAYROLL). The seed is SQL and
 * the catalogue is code, so this is the one place the two are held together.
 */
class SeedSetupStepsMatchCatalogueTest {

    private static final Path SEED = Path.of("..", "..", "..", "infra", "docker", "seed", "05-tenant-setup-steps.sql");

    private static final Map<String, List<PlatformModule>> SEEDED_TENANT_MODULES = new LinkedHashMap<>();

    static {
        SEEDED_TENANT_MODULES.put("11111111-1111-1111-1111-111111111111", List.of(PlatformModule.PAYROLL));
        SEEDED_TENANT_MODULES.put(
                "22222222-2222-2222-2222-222222222222", List.of(PlatformModule.HRMS, PlatformModule.PAYROLL));
    }

    private static final Pattern ROW = Pattern.compile(
            "\\('([0-9a-f-]{36})',\\s*'([A-Z_]+)',\\s*(NULL|'[A-Z]+'),\\s*(\\d+),\\s*'seed',\\s*'seed'\\)");

    @Test
    @DisplayName(
            "the seed's tenant_setup_step rows are exactly the catalogue steps each seeded tenant's modules give it")
    void seedMatchesCatalogue() throws IOException {
        assertThat(SEED)
                .as("seed file, resolved from the core module directory")
                .exists();
        String sql = Files.readString(SEED, StandardCharsets.UTF_8);

        List<String> seeded = new ArrayList<>();
        Matcher m = ROW.matcher(sql);
        while (m.find()) {
            String module = m.group(3).equals("NULL") ? "null" : m.group(3).replace("'", "");
            seeded.add(m.group(1) + " " + m.group(2) + " " + module + " " + m.group(4));
        }

        List<String> expected = new ArrayList<>();
        SEEDED_TENANT_MODULES.forEach((tenant, modules) -> {
            for (SetupStepCatalogue.StepDefinition def : SetupStepCatalogue.DEFAULT_STEPS) {
                if (def.module() == null || modules.contains(def.module())) {
                    expected.add(tenant + " " + def.code() + " " + def.module() + " " + def.displayOrder());
                }
            }
        });

        assertThat(seeded).containsExactlyElementsOf(expected);
    }
}
