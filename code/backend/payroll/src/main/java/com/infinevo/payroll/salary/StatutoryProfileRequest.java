package com.infinevo.payroll.salary;

/**
 * Request payload for employee statutory eligibility profile (W-26.2).
 */
public record StatutoryProfileRequest(
        Boolean eligibleForPf,
        Boolean eligibleForPt,
        Boolean eligibleForLwf,
        Boolean eligibleForEsi,
        Boolean eligibleForEps,
        Boolean contributesEpsOnHigherWages,
        Boolean director,
        String pfAccountNumber,
        String uan,
        String esiNumber) {}
