package com.infinevo.core.reference;

import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists countries available in reference data (W-116).
 * Signed-in users only; no specific permission action required.
 */
@RestController
@RequestMapping("/api/v1/reference/countries")
public class CountryController {

    private final JdbcTemplate jdbcTemplate;

    public CountryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @GetMapping
    public ResponseEntity<List<CountryResponse>> list() {
        List<CountryResponse> countries = jdbcTemplate.query(
                "SELECT code, name FROM reference.country ORDER BY name",
                (rs, rowNum) -> new CountryResponse(rs.getString("code"), rs.getString("name")));
        return ResponseEntity.ok(countries);
    }
}
