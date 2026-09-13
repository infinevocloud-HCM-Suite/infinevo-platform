//package com.itsdev.payroll.repository.employeeitdeclaration.taxcalculator;
//
//
//
//
//import com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.EmployeeTaxRecalculation;
//import com.itsdev.payroll.entity.employee.BasicDetails;
//import com.itsdev.payroll.entity.organization.Organization;
//import org.springframework.data.jpa.repository.JpaRepository;
//
//import java.util.List;
//import java.util.Optional;
//
//public interface EmployeeTaxRecalculationRepository
//        extends JpaRepository<EmployeeTaxRecalculation, Long> {
//
//    /* -----------------------------------------------------
//     * Fetch latest recalculation for employee + FY + regime
//     * ----------------------------------------------------- */
//    Optional<EmployeeTaxRecalculation>
//    findTopByOrganizationAndEmployeeAndFinancialYearAndTaxRegimeOrderByCreatedAtDesc(
//            Organization organization,
//            BasicDetails employee,
//            Integer financialYear,
//            String taxRegime
//    );
//
//    /* -----------------------------------------------------
//     * Fetch all recalculations (audit view)
//     * ----------------------------------------------------- */
//    List<EmployeeTaxRecalculation>
//    findByOrganizationAndEmployeeAndFinancialYearOrderByCreatedAtDesc(
//            Organization organization,
//            BasicDetails employee,
//            Integer financialYear
//    );
//
//    /* -----------------------------------------------------
//     * Fetch only NOT applied recalculations
//     * ----------------------------------------------------- */
//    List<EmployeeTaxRecalculation>
//    findByOrganizationAndEmployeeAndFinancialYearAndIsAppliedFalse(
//            Organization organization,
//            BasicDetails employee,
//            Integer financialYear
//    );
//}
//
