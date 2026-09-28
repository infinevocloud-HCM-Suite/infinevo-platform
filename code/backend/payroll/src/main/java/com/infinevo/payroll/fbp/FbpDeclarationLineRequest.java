package com.infinevo.payroll.fbp;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Line item in an FBP declaration request (W-27.2).
 */
public record FbpDeclarationLineRequest(String kind, UUID componentId, BigDecimal annualAmount) {}
