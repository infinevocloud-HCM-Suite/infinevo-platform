package com.infinevo.core.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.navigation.NavigationCatalogue.ItemDefinition;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The catalogue-consistency rule of {@link NavigationCatalogueValidator}, broken on purpose once
 * (W-12.3 §2). The boot-time wiring — the check runs and the context refuses to start — is
 * exercised by every integration test that scans {@code core.navigation}.
 */
class NavigationCatalogueValidatorTest {

    private static final ItemDefinition ROLES =
            new ItemDefinition("core.roles", "nav.roles", "/roles", "/api/v1/roles", null, "core.role.read");
    private static final ItemDefinition ORG = new ItemDefinition(
            "core.org",
            "nav.organisation",
            "/org/departments",
            "/api/v1/departments",
            null,
            "core.org.read",
            List.of(
                    new ItemDefinition(
                            "core.org.departments",
                            "nav.departments",
                            "/org/departments",
                            "/api/v1/departments",
                            null,
                            "core.org.read"),
                    new ItemDefinition(
                            "core.org.locations",
                            "nav.locations",
                            "/org/work-locations",
                            "/api/v1/work-locations",
                            null,
                            "core.org.read")));

    @Test
    @DisplayName("every leaf mapped: nothing missing")
    void everyLeafMappedIsConsistent() {
        Set<String> mapped = Set.of("/api/v1/roles", "/api/v1/departments", "/api/v1/work-locations");

        assertThat(NavigationCatalogueValidator.missingEndpoints(List.of(ROLES, ORG), mapped))
                .isEmpty();
    }

    @Test
    @DisplayName("a nested leaf without a GET mapping is reported by key and endpoint")
    void nestedLeafWithoutMappingIsReported() {
        Set<String> mapped = Set.of("/api/v1/roles", "/api/v1/departments");

        List<String> missing = NavigationCatalogueValidator.missingEndpoints(List.of(ROLES, ORG), mapped);

        assertThat(missing).hasSize(1);
        assertThat(missing.get(0)).contains("core.org.locations").contains("/api/v1/work-locations");
    }

    @Test
    @DisplayName("a parent's own endpoint is not checked — only leaves lead anywhere")
    void parentEndpointIsNotChecked() {
        ItemDefinition parentWithOddEndpoint = new ItemDefinition(
                "core.org",
                "nav.organisation",
                "/org",
                "/api/v1/nowhere",
                null,
                "core.org.read",
                List.of(new ItemDefinition(
                        "core.org.departments",
                        "nav.departments",
                        "/org/departments",
                        "/api/v1/departments",
                        null,
                        "core.org.read")));

        assertThat(NavigationCatalogueValidator.missingEndpoints(
                        List.of(parentWithOddEndpoint), Set.of("/api/v1/departments")))
                .isEmpty();
    }

    @Test
    @DisplayName("the shipped catalogue has no leaf without an endpoint in the current app")
    void shippedCatalogueLeavesAreAllKnownEndpoints() {
        // The four controllers that exist on main today; the boot-time check proves the same
        // against the live handler mapping. If this list has to grow, the controller shipped first.
        Set<String> shippedGetEndpoints = Set.of(
                "/api/v1/departments",
                "/api/v1/designations",
                "/api/v1/work-locations",
                "/api/v1/roles",
                "/api/v1/audit");

        assertThat(NavigationCatalogueValidator.missingEndpoints(
                        NavigationCatalogue.DEFAULT_ITEMS, shippedGetEndpoints))
                .isEmpty();
    }
}
