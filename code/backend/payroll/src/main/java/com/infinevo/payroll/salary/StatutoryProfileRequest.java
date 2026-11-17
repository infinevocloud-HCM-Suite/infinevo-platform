package com.infinevo.payroll.salary;

/**
 * Request payload for employee statutory eligibility profile (W-26.2).
 *
 * <p>D-141: {@code tdsApplicable} null leaves the saved value as it is (true for a new profile), so a
 * client that does not know the field never switches TDS off by omission.
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
        String esiNumber,
        Boolean tdsApplicable) {

    /** The shape before D-141: TDS applicability unchanged. */
    public StatutoryProfileRequest(
            Boolean eligibleForPf,
            Boolean eligibleForPt,
            Boolean eligibleForLwf,
            Boolean eligibleForEsi,
            Boolean eligibleForEps,
            Boolean contributesEpsOnHigherWages,
            Boolean director,
            String pfAccountNumber,
            String uan,
            String esiNumber) {
        this(
                eligibleForPf,
                eligibleForPt,
                eligibleForLwf,
                eligibleForEsi,
                eligibleForEps,
                contributesEpsOnHigherWages,
                director,
                pfAccountNumber,
                uan,
                esiNumber,
                null);
    }
}
