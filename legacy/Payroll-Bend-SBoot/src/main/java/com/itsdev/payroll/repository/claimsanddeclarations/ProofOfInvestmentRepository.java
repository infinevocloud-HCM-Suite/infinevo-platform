package com.itsdev.payroll.repository.claimsanddeclarations;

import com.itsdev.payroll.entity.claimsanddeclarations.ProofOfInvestment;
import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProofOfInvestmentRepository extends JpaRepository<ProofOfInvestment, Long> {
    Optional<ProofOfInvestment> findByOrganization(Organization org);
}
