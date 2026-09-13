package com.phegondev.usersmanagementsystem.repository;

import com.phegondev.usersmanagementsystem.entity.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {
    List<EmployeeDocument> findByEmployeeIdAndCompanyIndex(Long employeeId, Integer companyIndex);



}
