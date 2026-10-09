package com.infinevo.core.template;

import java.util.List;

/**
 * What one country-template run did (W-73.9): the sections that wrote rows, and the sections skipped
 * because the tenant already had its own rows or does not hold the section's module. Both empty when
 * the country has no template.
 */
public record TemplateApplyResponse(String countryCode, List<String> applied, List<String> skipped) {

    public TemplateApplyResponse {
        applied = applied == null ? List.of() : List.copyOf(applied);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }
}
