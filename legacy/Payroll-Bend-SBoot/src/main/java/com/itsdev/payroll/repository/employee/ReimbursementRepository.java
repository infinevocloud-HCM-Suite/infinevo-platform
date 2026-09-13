package com.itsdev.payroll.repository.employee;
// ==== Repositories for CTC Module (multi‑org) ====
// Adjust package names to match your project structure, e.g.:
// package com.yourapp.payroll.ctc.repository;

// Common imports used below
import com.itsdev.payroll.entity.employee.EmployeeReimbursement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

// ===== ReimbursementRepository =====
@Repository("employeeReimbursementRepository")
public interface ReimbursementRepository extends JpaRepository<EmployeeReimbursement, Long> {
    Optional<EmployeeReimbursement> findByIdAndOrganization_OrganizationId(Long id, String organizationId);


    List<EmployeeReimbursement> findAllByCtcStructure_IdAndOrganization_OrganizationId(Long ctcStructureId, String organizationId);


    void deleteAllByCtcStructure_IdAndOrganization_OrganizationId(Long ctcStructureId, String organizationId);
}
