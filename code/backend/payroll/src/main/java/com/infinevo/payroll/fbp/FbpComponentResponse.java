package com.infinevo.payroll.fbp;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO representing an active salary component flagged for FBP (W-27.1).
 */
public record FbpComponentResponse(String kind, UUID id, String code, String name, BigDecimal maxLimit) {}
