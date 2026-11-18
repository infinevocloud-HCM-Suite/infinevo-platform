package com.infinevo.payroll.statutory.guided;

/**
 * Result details for one state's professional tax setup (W-93).
 */
public record PtStateResult(String stateCode, String stateName, String registrationNumber, String source) {}
