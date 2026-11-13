package com.infinevo.payroll.statutory.settings;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Request payload for the company TDS switch (D-141). {@code enabled} is required.
 */
public record TdsSettingRequest(@JsonAlias({"is_enabled", "isEnabled"}) Boolean enabled) {}
