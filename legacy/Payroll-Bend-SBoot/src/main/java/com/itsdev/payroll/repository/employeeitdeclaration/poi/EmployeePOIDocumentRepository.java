package com.itsdev.payroll.repository.employeeitdeclaration.poi;

import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeePOIDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeePOIDocumentRepository
        extends JpaRepository<EmployeePOIDocument, Long> {

    List<EmployeePOIDocument> findByPoiItem_Id(Long poiItemId);
}
