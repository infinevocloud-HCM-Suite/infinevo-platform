package com.itsdev.payroll.repository.employee;
// Common imports used below
import com.itsdev.payroll.entity.employee.VariableEarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

// ===== VariableEarningRepository =====
@Repository
public interface VariableEarningRepository extends JpaRepository<VariableEarning, Long> {
    Optional<VariableEarning> findByIdAndOrganization_OrganizationId(Long id, String organizationId);


    List<VariableEarning> findAllByCtcStructure_IdAndOrganization_OrganizationId(Long ctcStructureId, String organizationId);


    void deleteAllByCtcStructure_IdAndOrganization_OrganizationId(Long ctcStructureId, String organizationId);
}
