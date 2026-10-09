package com.infinevo.core.template;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/** One row of {@code reference.country_template} (W-73.9). */
public record CountryTemplateSection(String countryCode, String section, int version, JsonNode payload) {

    public CountryTemplateSection {
        Objects.requireNonNull(countryCode, "countryCode must not be null");
        Objects.requireNonNull(section, "section must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
    }
}
