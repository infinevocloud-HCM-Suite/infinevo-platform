package com.infinevo.payroll.component;

/**
 * Request payload for toggling active status of a component (W-26.1).
 */
public record ActiveUpdateRequest(Boolean active) {}
