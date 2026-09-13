package com.itsdev.payroll.repository.employeeitdeclaration;

import com.itsdev.payroll.entity.EmployeeITDeclaration.EmployeeInvestmentDeclaration;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.List;

public interface EmployeeInvestmentDeclarationRepository
                extends JpaRepository<EmployeeInvestmentDeclaration, Long> {

        Optional<EmployeeInvestmentDeclaration> findByOrganizationAndEmployeeAndFiscalYear(
                        Organization organization,
                        BasicDetails employee,
                        Integer fiscalYear);

        boolean existsByOrganizationAndEmployeeAndFiscalYear(
                        Organization organization,
                        BasicDetails employee,
                        Integer fiscalYear);

        void deleteByOrganizationAndEmployeeAndFiscalYear(
                        Organization organization,
                        BasicDetails employee,
                        Integer fiscalYear);

        List<EmployeeInvestmentDeclaration> findByOrganization_OrganizationIdAndFiscalYear(
                        Organization organization, Integer fiscalYear);

        // @Query("SELECT DISTINCT d FROM EmployeeInvestmentDeclaration d " +
        // "LEFT JOIN FETCH d.letOutProperties " +
        // "LEFT JOIN FETCH d.section6aDeclarations " +
        // "LEFT JOIN FETCH d.houseRents " +
        // "LEFT JOIN FETCH d.otherIncomes " +
        // "LEFT JOIN FETCH d.prevEmploymentDeclarations " +
        // "LEFT JOIN FETCH d.preTaxDeductions " +
        // "LEFT JOIN FETCH d.homeLoans " +
        // "WHERE d.organization = :org AND d.employee = :employee AND d.fiscalYear =
        // :fiscalYear")
        // Optional<EmployeeInvestmentDeclaration>
        // findByOrganizationAndEmployeeAndFiscalYearForPoi(
        // @Param("org") Organization org,
        // @Param("employee") BasicDetails employee,
        // @Param("fiscalYear") Integer fiscalYear);
}
