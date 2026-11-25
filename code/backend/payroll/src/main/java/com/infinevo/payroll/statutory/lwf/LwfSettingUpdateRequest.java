package com.infinevo.payroll.statutory.lwf;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload to update tenant LWF state registration and enablement toggle (W-92).
 */
public record LwfSettingUpdateRequest(
        @JsonProperty("isEnabled") Boolean isEnabled, @JsonProperty("registrationNumber") String registrationNumber) {}
