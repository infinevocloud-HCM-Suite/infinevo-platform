package com.infinevo.payroll.salary;

import java.util.UUID;

/**
 * Response payload for employee statutory eligibility profile (W-26.2); D-141 adds {@code tdsApplicable}.
 */
public record StatutoryProfileResponse(
        UUID id,
        UUID employeeId,
        boolean eligibleForPf,
        boolean eligibleForPt,
        boolean eligibleForLwf,
        boolean eligibleForEsi,
        boolean eligibleForEps,
        boolean contributesEpsOnHigherWages,
        boolean director,
        String pfAccountNumber,
        String uan,
        String esiNumber,
        boolean tdsApplicable) {

    /** The shape before D-141: TDS applicable. */
    public StatutoryProfileResponse(
            UUID id,
            UUID employeeId,
            boolean eligibleForPf,
            boolean eligibleForPt,
            boolean eligibleForLwf,
            boolean eligibleForEsi,
            boolean eligibleForEps,
            boolean contributesEpsOnHigherWages,
            boolean director,
            String pfAccountNumber,
            String uan,
            String esiNumber) {
        this(
                id,
                employeeId,
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
                true);
    }

    public static StatutoryProfileResponse from(EmployeeStatutoryProfile profile) {
        return new StatutoryProfileResponse(
                profile.getId(),
                profile.getEmployeeId(),
                profile.isEligibleForPf(),
                profile.isEligibleForPt(),
                profile.isEligibleForLwf(),
                profile.isEligibleForEsi(),
                profile.isEligibleForEps(),
                profile.isContributesEpsOnHigherWages(),
                profile.isDirector(),
                profile.getPfAccountNumber(),
                profile.getUan(),
                profile.getEsiNumber(),
                profile.isTdsApplicable());
    }
}
