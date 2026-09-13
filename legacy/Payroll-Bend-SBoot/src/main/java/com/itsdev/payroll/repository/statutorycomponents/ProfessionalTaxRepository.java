package com.itsdev.payroll.repository.statutorycomponents;

import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.statutorycomponents.OrgPTOverride;
import com.itsdev.payroll.entity.statutorycomponents.ProfessionalTax;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfessionalTaxRepository extends JpaRepository<ProfessionalTax, Long> {

    List<ProfessionalTax> findByOrganization(Organization organization);

    Optional<ProfessionalTax> findByTaxIdAndOrganization_Id(
            String taxId,
            Long organizationId
    );


    boolean existsByOrganization_OrganizationId(String organizationId);




}