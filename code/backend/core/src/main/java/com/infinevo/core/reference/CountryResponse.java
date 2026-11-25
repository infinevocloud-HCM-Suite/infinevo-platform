package com.infinevo.core.reference;

/**
 * Country reference item (W-116).
 *
 * @param code 2-letter ISO country code (e.g. "IN")
 * @param name country display name (e.g. "India")
 */
public record CountryResponse(String code, String name) {}
