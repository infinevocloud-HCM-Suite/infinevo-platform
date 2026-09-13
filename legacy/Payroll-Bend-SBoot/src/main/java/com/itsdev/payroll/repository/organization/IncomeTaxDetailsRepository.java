package com.itsdev.payroll.repository.organization;

import com.itsdev.payroll.entity.organization.IncomeTaxDetails;
import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IncomeTaxDetailsRepository extends JpaRepository<IncomeTaxDetails, Long> {
    Optional<IncomeTaxDetails> findByOrganization(Organization organization);
    boolean existsByOrganization_OrganizationId(String organizationId);

}
