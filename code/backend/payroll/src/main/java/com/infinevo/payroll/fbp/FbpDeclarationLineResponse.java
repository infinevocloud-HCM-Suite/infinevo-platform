package com.infinevo.payroll.fbp;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Line item in an FBP declaration response (W-27.2).
 */
public record FbpDeclarationLineResponse(
        String kind,
        UUID componentId,
        String componentCode,
        String componentName,
        BigDecimal lineAnnualAmount,
        BigDecimal declaredAnnualAmount,
        BigDecimal declaredMonthlyAmount) {}
