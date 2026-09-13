package com.itsdev.payroll.repository.employee;

// Common imports used below
import com.itsdev.payroll.entity.employee.FbpComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

// ===== FbpComponentRepository =====
@Repository
public interface FbpComponentRepository extends JpaRepository<FbpComponent, Long> {
    Optional<FbpComponent> findByIdAndOrganization_OrganizationId(Long id, String organizationId);


    List<FbpComponent> findAllByCtcStructure_IdAndOrganization_OrganizationId(Long ctcStructureId, String organizationId);


    void deleteAllByCtcStructure_IdAndOrganization_OrganizationId(Long ctcStructureId, String organizationId);
}
