package com.itsdev.payroll.repository.statutorycomponents;

import com.itsdev.payroll.entity.statutorycomponents.Esi;
import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EsiRepository extends JpaRepository<Esi, Long> {
    Optional<Esi> findByOrganization(Organization organization);
    boolean existsByOrganization_OrganizationId(String organizationId);


    Optional<Esi> findByOrganization_OrganizationId(String organizationId);


}
