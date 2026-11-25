package com.infinevo.payroll.statutory.ecr;

/**
 * EPFO Challan remittance summary by statutory account (W-87 §3).
 */
public record PfChallanAccountSummary(
        long account01, long account02, long account10, long account21, long account22, long totalChallan) {}
