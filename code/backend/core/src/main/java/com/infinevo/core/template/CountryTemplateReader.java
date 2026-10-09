package com.infinevo.core.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads {@code reference.country_template} (W-73.9, {@code V162}). Shared reference data: no tenant, no RLS.
 */
@Repository
public class CountryTemplateReader {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public CountryTemplateReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /** The country's sections, or an empty list when it has no template. */
    public List<CountryTemplateSection> sections(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return List.of();
        }
        String code = countryCode.trim().toUpperCase(Locale.ROOT);
        return jdbcTemplate.query(
                "SELECT country_code, section, version, payload::text AS payload"
                        + " FROM reference.country_template WHERE country_code = ? ORDER BY section",
                (rs, rowNum) -> {
                    try {
                        return new CountryTemplateSection(
                                rs.getString("country_code").trim(),
                                rs.getString("section"),
                                rs.getInt("version"),
                                JSON.readTree(rs.getString("payload")));
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(
                                "reference.country_template " + code + "/" + rs.getString("section")
                                        + " holds a payload that is not JSON",
                                e);
                    }
                },
                code);
    }

    /** Every country with a template, by code. */
    public List<CountryTemplateSummary> summaries() {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        Map<String, Integer> versions = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT country_code, section, version FROM reference.country_template ORDER BY country_code, section",
                rs -> {
                    String country = rs.getString("country_code").trim();
                    sections.computeIfAbsent(country, k -> new ArrayList<>()).add(rs.getString("section"));
                    versions.merge(country, rs.getInt("version"), Math::max);
                });
        List<CountryTemplateSummary> out = new ArrayList<>();
        sections.forEach((country, names) ->
                out.add(new CountryTemplateSummary(country, List.copyOf(names), versions.get(country))));
        return out;
    }
}
