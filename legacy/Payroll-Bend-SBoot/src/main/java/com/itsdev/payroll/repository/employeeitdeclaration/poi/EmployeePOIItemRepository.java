package com.itsdev.payroll.repository.employeeitdeclaration.poi;

import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeePOIItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeePOIItemRepository
        extends JpaRepository<EmployeePOIItem, Long> {

    List<EmployeePOIItem> findByProofOfInvestment_Id(Long poiId);
}
