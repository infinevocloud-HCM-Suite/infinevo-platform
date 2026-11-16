package com.infinevo.hrms.attendance;

/**
 * Request payload for attendance clock operations, optionally carrying geolocation coordinates (W-98.2 §4.1).
 */
public record ClockRequest(Double latitude, Double longitude, Double accuracyMeters) {}
