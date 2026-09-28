package com.infinevo.payroll.fbp;

import java.math.BigDecimal;

/**
 * Summary pool and declared amounts for an FBP declaration (W-27.2).
 */
public record FbpSummaryResponse(BigDecimal poolAnnual, BigDecimal declaredAnnual, BigDecimal unallocatedAnnual) {}
