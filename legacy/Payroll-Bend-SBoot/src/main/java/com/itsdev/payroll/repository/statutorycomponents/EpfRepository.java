package com.itsdev.payroll.repository.statutorycomponents;

import com.itsdev.payroll.entity.statutorycomponents.Epf;
import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EpfRepository extends JpaRepository<Epf, Long> {

    Optional<Epf> findByOrganization(Organization organization);
    boolean existsByOrganization_OrganizationId(String organizationId);




    Optional<Epf> findByOrganization_OrganizationId(String organizationId);


}
