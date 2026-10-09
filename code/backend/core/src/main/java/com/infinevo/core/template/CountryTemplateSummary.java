package com.infinevo.core.template;

import java.util.List;

/**
 * A country that has a template, its section names and its highest section version (W-73.9) - the
 * reply of {@code GET /api/v1/reference/country-templates}.
 */
public record CountryTemplateSummary(String countryCode, List<String> sections, int version) {}
