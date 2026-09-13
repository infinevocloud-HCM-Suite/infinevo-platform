package com.itsdev.payroll.repository.claimsanddeclarations;

import com.itsdev.payroll.entity.claimsanddeclarations.IncomeTaxDeclaration;
import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface IncomeTaxDeclarationRepository extends JpaRepository<IncomeTaxDeclaration, Long> {
    Optional<IncomeTaxDeclaration> findByOrganization(Organization org);

    @Query("""
        SELECT d FROM IncomeTaxDeclaration d
        WHERE d.isItDeclarationLocked = false
        AND d.lastDateForItDeclaration IS NOT NULL
        AND d.lastDateForItDeclaration <> ''
    """)
    List<IncomeTaxDeclaration> findAllActiveDeclarations();
}
