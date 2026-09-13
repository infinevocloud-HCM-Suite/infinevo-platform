package com.itsdev.payroll.repository.claimsanddeclarations;


import com.itsdev.payroll.entity.claimsanddeclarations.ReimbursementClaim;
import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReimbursementClaimRepository extends JpaRepository<ReimbursementClaim, Long> {
    Optional<ReimbursementClaim> findByOrganization(Organization org);
}
