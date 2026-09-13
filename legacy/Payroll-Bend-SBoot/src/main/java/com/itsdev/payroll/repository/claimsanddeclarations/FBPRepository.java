package com.itsdev.payroll.repository.claimsanddeclarations;

import com.itsdev.payroll.entity.claimsanddeclarations.FBP;
import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FBPRepository extends JpaRepository<FBP, Long> {
    Optional<FBP> findByOrganization(Organization org);
}